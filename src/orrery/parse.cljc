(ns orrery.parse
  "Parses the notation. Reads mathematics, as the learner types it,
  into the canonical tagged vectors. The parser is the inverse of
  `orrery.notation`, so what the page prints reads back as the term
  it printed.

  This is a Pratt parser over the precedence table of the printer.
  Shifts are below sums, and sums are below products and quotients.
  Unary minus comes next, and then powers. A function applied to a
  bare operand (sin x, sin²x, d/dx u) takes an operand of at least
  the level of a negation, so sin x² is sin(x²) and sin x·y is
  (sin x)·y.

  The parser reads the typed spellings as well as the printed ones:

    * or ×    for ·
    -         for −
    ^ or **   for a superscript
    pi        for π
    -> or =>  for →

  f(a, b) is the node `[:f a b]` for any name f.

  The spelling of a numeral absorbs a leading minus and a slash
  between integers. The EDN reader does the same, and bendix writes
  its coefficients that way. So 1/2 and -3 are the numbers, one leaf
  each, while 1/(2) is the quotient node over 1 and 2, and -(3) is
  the negation node over 3. Apart from that, the parser never
  computes: 2·3 is a product node, just as 2·x is.

  a + b + c reads as the left-nested binary chain, which is how the
  printer prints it. The n-ary sums of bendix print the same way and
  read back nested. Its polynomial cannot tell the two apart.

  Unary minus reads as `:neg`, which is how the page spells it in its
  own terms. d/dx u reads as `[:D u :x]`.

  `term` and `rules` return `{:value v}` or `{:error message}`. Blank
  text has the value nil."
  (:require [bendix.num :as num]
            [clojure.string :as str]
            [orrery.notation :as notation]))

(defn- fail [message] (throw (ex-info message {:notation true})))

;; ---------------------------------------------------------------------------
;; tokens

(def ^:private superscript-digits
  {\⁰ "0" \¹ "1" \² "2" \³ "3" \⁴ "4" \⁵ "5" \⁶ "6" \⁷ "7" \⁸ "8" \⁹ "9" \⁻ "-"})

(def ^:private token-rules
  "The token rules, each a `[regex kind]` pair. The tokenizer tries
  them in order at each position, and reads the value of a token
  from the match.

  - A derivative d/dx is one token. Its rule comes ahead of the rule
    for identifiers, which would read d and dx.
  - A decimal is one token, so that it can be refused.
  - A ratio is two integers and a slash. The grammar folds them, so
    that 1/2³ is still 1 over 2³."
  [[#"^\s+" :space]
   [#"^d/d([A-Za-z_Ͱ-Ͽ][A-Za-z0-9_Ͱ-Ͽ]*)" :deriv]
   [#"^\d+\.\d+" :decimal]
   [#"^\d+" :number]
   [#"^\?[A-Za-z_][A-Za-z0-9_]*" :pvar]
   [#"^[A-Za-z_Ͱ-Ͽ][A-Za-z0-9_Ͱ-Ͽ]*" :ident]
   [#"^⁻?[⁰¹²³⁴⁵⁶⁷⁸⁹]+" :sup]
   [#"^(?:->|→|=>)" :arrow]
   [#"^(?:\*\*|\^)" :pow]
   [#"^<<" :<<]
   [#"^>>" :>>]
   [#"^\+" :+]
   [#"^[-−–]" :-]
   [#"^[*·×⋅]" :*]
   [#"^[/÷]" :div]
   [#"^\(" :open]
   [#"^\)" :close]
   [#"^," :comma]
   [#"^:" :colon]])

(defn- integer-of
  "Returns the integer that `digits` spells, exact on every runtime.
  Drops leading zeros so that the reader does not take the digits
  for octal."
  [digits]
  (num/read-string (str/replace digits #"^0+(?=\d)" "")))

(defn- ratio-of
  "Returns the exact ratio of `n` to `d`, which is an integer when
  `d` divides `n`. Fails with a message that quotes `text` and says
  division by zero when the ratio cannot be made."
  [n d text]
  (try (num/ratio n d)
       (catch #?(:clj Exception :default :default) _
         (fail (str text ": division by zero")))))

(defn- token [kind m pos]
  (let [text (if (vector? m) (first m) m)]
    (merge {:kind kind :text text :pos pos}
           (case kind
             :number {:value (integer-of text)}
             :deriv {:value (keyword (second m))}
             :sup {:value (integer-of (apply str (map superscript-digits text)))}
             :pvar {:value (symbol text)}
             nil))))

(defn- tokens
  "Returns the tokens of `text`, ending with an `:end` token. Throws
  at a character that no token starts with."
  [text]
  (loop [i 0, acc []]
    (if (>= i (count text))
      (conj acc {:kind :end :text "the end" :pos i})
      (let [rest-text (subs text i)
            [kind m] (some (fn [[re kind]] (when-let [m (re-find re rest-text)] [kind m])) token-rules)]
        (if-not kind
          (fail (str "unexpected " (pr-str (str (first rest-text))) " at character " (inc i)))
          (let [len (count (if (vector? m) (first m) m))]
            (recur (+ i len) (if (= :space kind) acc (conj acc (token kind m i))))))))))

;; ---------------------------------------------------------------------------
;; the grammar

(def ^:private infix
  "A map from every infix token kind to its binding power and its
  operator."
  {:<< [10 :<<] :>> [10 :>>] :+ [20 :+] :- [20 :-] :* [30 :*] :div [30 :/] :pow [50 :expt] :sup [50 nil]})

(def ^:private negation
  "The binding power of unary minus. It is also the level of what a
  function or d/dx takes as a bare operand: atoms, powers and
  negations, but not products."
  40)

(def ^:private operand-starts #{:number :decimal :ident :pvar :deriv :open :-})

(defn- at [text pos] (str text " at character " (inc pos)))

(defn- unexpected [ts i]
  (let [t (nth ts i)]
    (fail (if (= :end (:kind t))
            (if (pos? i) (str "nothing after " (:text (nth ts (dec i)))) "nothing to read")
            (at (str "unexpected " (:text t)) (:pos t))))))

(declare expression)

(defn- continue
  "Applies to `left` the infix and postfix operators that bind
  tighter than `min-bp`. Returns `{:value v :next j :bare? b}`.

  `:bare?` is still true only when the value is a numeral and
  nothing was applied to it. A slash between two bare integers makes
  the ratio, not a quotient node. Stops without an error at anything
  that is not an operator."
  [ts i left bare? min-bp]
  (let [t (nth ts i)
        k (:kind t)
        [bp op] (infix k)]
    (cond
      (or (nil? bp) (<= bp min-bp)) {:value left :next i :bare? bare?}
      (= :sup k) (recur ts (inc i) [:expt left (:value t)] false min-bp)
      (= :pow k) (let [r (expression ts (inc i) (dec bp))]      ; right-associative
                   (recur ts (:next r) [:expt left (:value r)] false min-bp))
      (= :div k) (let [r (expression ts (inc i) bp)]
                   (if (and bare? (integer? left) (:bare? r) (integer? (:value r)))
                     (recur ts (:next r) (ratio-of left (:value r) (str left "/" (:value r))) true min-bp)
                     (recur ts (:next r) [:/ left (:value r)] false min-bp)))
      :else (let [r (expression ts (inc i) bp)]
              (recur ts (:next r) [op left (:value r)] false min-bp)))))

(defn- operand
  "Reads the expression at `min-bp` that starts at `i`. Fails with
  the message `missing` when no expression starts there."
  [ts i min-bp missing]
  (if (contains? operand-starts (:kind (nth ts i)))
    (expression ts i min-bp)
    (fail missing)))

(defn- arguments
  "Reads the comma-separated arguments of `name`, starting after its
  opening parenthesis. Returns the arguments and the index past the
  closing parenthesis."
  [ts i name]
  (if (= :close (:kind (nth ts i)))
    [[] (inc i)]
    (loop [i i, args []]
      (let [r (expression ts i 0)
            args (conj args (:value r))
            t (nth ts (:next r))]
        (case (:kind t)
          :comma (recur (inc (:next r)) args)
          :close [args (inc (:next r))]
          (fail (at (str "missing ) after the arguments of " name ", found " (:text t)) (:pos t))))))))

(defn- identifier
  "Reads what the identifier at `i` starts. That is one of:

  - a variable;
  - a constant;
  - a function applied to a bare operand (sin x, sin²x, sin^2 x);
  - any name applied to parenthesized arguments."
  [ts i]
  (let [t (nth ts i), name (:text t), op (keyword name), j (inc i), following (:kind (nth ts j))]
    (cond
      (= :open following)
      (let [[args k] (arguments ts (inc j) name)]
        {:value (into [op] args) :next k :bare? false})

      (contains? #{"pi" "π"} name) {:value [:pi] :next j :bare? false}

      (contains? notation/functions op)
      (let [missing (str name " needs an argument: " name " x or " name "(x)")
            applied (fn [j] (let [r (operand ts j negation missing)] [[op (:value r)] (:next r)]))]
        (case following
          :sup (let [[v k] (applied (inc j))]
                 {:value [:expt v (:value (nth ts j))] :next k :bare? false})
          :pow (let [e (expression ts (inc j) negation)  ; sin^2 x, sin^-1 x, sin^(n + 1) x
                     [v k] (applied (:next e))]
                 {:value [:expt v (:value e)] :next k :bare? false})
          (let [[v k] (applied j)]
            {:value v :next k :bare? false})))

      :else {:value op :next j :bare? false})))

(defn- prefix
  "Reads the operand that starts at `i`. Returns
  `{:value v :next j :bare? b}`."
  [ts i]
  (let [t (nth ts i), j (inc i)]
    (case (:kind t)
      :number {:value (:value t) :next j :bare? true}
      :decimal (fail (str (:text t) " is not exact; write a ratio such as 1/2"))
      :pvar {:value (:value t) :next j :bare? false}
      :ident (identifier ts i)
      :deriv (let [r (operand ts j negation (str (:text t) " needs a function to differentiate"))]
               {:value [:D (:value r) (:value t)] :next (:next r) :bare? false})
      :open (let [r (expression ts j 0)
                  close (nth ts (:next r))]
              (when (not= :close (:kind close))
                (fail (str "missing ) for the ( at character " (inc (:pos t))
                           (when (not= :end (:kind close)) (at (str ", found " (:text close)) (:pos close))))))
              {:value (:value r) :next (inc (:next r)) :bare? false})
      :- (if (= :number (:kind (nth ts j)))
           ;; −3 is the number. −3² is the negation of a power.
           ;; −3·x is the number times x.
           (let [r (continue ts (inc j) (:value (nth ts j)) true negation)]
             (if (:bare? r)
               (assoc r :value (num/neg (:value r)))
               {:value [:neg (:value r)] :next (:next r) :bare? false}))
           (let [r (operand ts j negation "− needs something to negate")]
             {:value [:neg (:value r)] :next (:next r) :bare? false}))
      (unexpected ts i))))

(defn- expression
  "Reads the expression that starts at `i` and whose operators bind
  tighter than `min-bp`."
  [ts i min-bp]
  (let [{:keys [value next bare?]} (prefix ts i)]
    (continue ts next value bare? min-bp)))

(defn- leftover
  "Returns the message that says why the tokens from `i` on were not
  read."
  [ts i]
  (let [t (nth ts i)]
    (if (contains? operand-starts (:kind t))
      (at (str "missing an operator before " (:text t)) (:pos t))   ; 2x, x y, (a)(b)
      (at (str "unexpected " (:text t)) (:pos t)))))

(defn- read-all
  "Reads the whole of `text` as one expression and returns it.
  Throws when the text does not read."
  [text]
  (let [ts (tokens text)]
    (when-not (= :end (:kind (first ts)))
      (let [r (expression ts 0 0)]
        (if (= :end (:kind (nth ts (:next r))))
          (:value r)
          (fail (leftover ts (:next r))))))))

(defn as-read
  "Returns `t` as `term` reads it back from its printed form. N-ary
  sums and products are left-nested, and unary `:-` is `:neg`. The
  round trip is exact up to these two changes."
  [t]
  (if (vector? t)
    (let [[op & args] t, args (map as-read args)]
      (cond (and (contains? #{:+ :*} op) (> (count args) 2)) (reduce (fn [acc a] [op acc a]) args)
            (and (= :- op) (= 1 (count args))) [:neg (first args)]
            :else (into [op] args)))
    t))

(defn term
  "Parses `text`, a term or a pattern in the notation. Returns:

  - `{:value t}` when the text reads;
  - `{:value nil}` for blank text;
  - `{:error message}` when the text does not read.

  When the text is missing an operator, the message also says how
  to write a product, since that is the likely intent."
  [text]
  (try {:value (read-all text)}
       (catch #?(:clj Exception :default :default) e
         (let [m (ex-message e)]
           {:error (if (str/starts-with? m "missing an operator") (str m "; a product is written 2·x or 2*x") m)}))))

;; ---------------------------------------------------------------------------
;; rules

(def ^:private arrow #"\s*(?:->|→|=>)\s*")

(def ^:private rule-shape "a rule is written name: pattern -> replacement")

(defn rules
  "Parses `text` as rules, one rule per line. Each rule is written
  name: pattern -> replacement, and → and => are arrows too.
  Returns:

  - `{:value [[name lhs rhs] ...]}` when every line reads;
  - `{:value nil}` for blank text;
  - `{:error message}` otherwise. The message names the line."
  [text]
  (let [lines (keep-indexed (fn [i line] (when-not (str/blank? line) [(inc i) line])) (str/split-lines text))]
    (if (empty? lines)
      {:value nil}
      (reduce (fn [acc [n line]]
                (let [problem (fn [m] (reduced {:error (str "line " n ": " m)}))
                      ;; The regex is anchored at both ends. In
                      ;; JavaScript, re-matches only checks that the
                      ;; first match is the whole line, and a lazy
                      ;; group would stop short.
                      [_ name body] (re-matches #"^\s*([^:]*?)\s*:\s*(.*?)\s*$" line)
                      sides (when body (str/split body arrow -1))]
                  (cond
                    (nil? name) (problem rule-shape)
                    (str/blank? name) (problem (str rule-shape "; the name is missing"))
                    (not= 2 (count sides)) (problem (str rule-shape "; one arrow between the two"))
                    :else
                    (let [[lhs rhs] (map term sides)]
                      (cond
                        (:error lhs) (problem (:error lhs))
                        (nil? (:value lhs)) (problem "the pattern before the arrow is missing")
                        (:error rhs) (problem (:error rhs))
                        (nil? (:value rhs)) (problem "the replacement after the arrow is missing")
                        :else (update acc :value conj [name (:value lhs) (:value rhs)]))))))
              {:value []}
              lines))))
