(ns orrery.notation
  "Prints the canonical tagged vectors as mathematics, for display.

    [:+ [:* 2 :x] :y]         prints as  2·x + y
    [:expt [:sin :x] 2]       prints as  sin²x
    [:D [:sin [:* 2 :x]] :x]  prints as  d/dx sin(2·x)

  The printer shows nesting faithfully. `[:+ :a [:+ :b :c]]` prints
  as a + (b + c) and `[:+ [:+ :a :b] :c]` prints as a + b + c, so
  the arrangement of a sum stays visible. The children of an e-node
  are class ids, and the ids print as #7. The printer is pure and
  prints the same on every runtime.

  `orrery.parse` reads the notation back, so what prints here must
  read as the term it came from. In the parser, the spelling of a
  numeral absorbs a leading minus and a slash between integers. For
  that reason:

  - the quotient node of two integers prints as 1/(2);
  - the negation of a numeral prints as −(3);
  - a negative numeral is parenthesized wherever a negation would
    be, as in (−2)²."
  (:require [bendix.num :as num]
            [clojure.string :as str]))

;; Precedence levels:
;;
;;   0  shifts
;;   1  sums
;;   2  a function applied to a bare atom (sin x), and a derivative
;;      (d/dx u)
;;   3  products
;;   4  unary minus
;;   5  powers
;;   6  atoms, and anything already parenthesized
;;
;; An operand is parenthesized when its level is below the level of
;; its operator. In a chain, the operands after the first are also
;; parenthesized when they are at the level of the operator.

(def ^:private binary
  {:+ [" + " 1] :- [" − " 1] :* ["·" 3] :/ ["/" 3] :<< [" << " 0] :>> [" >> " 0]})

(def functions
  "The set of operators that print as a function of one argument,
  such as sin x, and that read back as one."
  #{:sin :cos :tan :exp :log :sqrt :abs})

(def ^:private constants {:pi "π" :e "e"})

(def ^:private superscripts
  {\0 "⁰" \1 "¹" \2 "²" \3 "³" \4 "⁴" \5 "⁵" \6 "⁶" \7 "⁷" \8 "⁸" \9 "⁹" \- "⁻"})

(defn- small-integer? [x] (and (integer? x) (< -100 x 100)))

(defn- superscript [n] (apply str (map superscripts (str n))))

(defn- paren [s] (str "(" s ")"))

(defn- wrap
  "Returns `s`, parenthesized when `level` is below `min-level`."
  [[s level] min-level]
  (if (< level min-level) (paren s) s))

(defn- number-str [n]
  (let [s (str n)]
    (if (str/starts-with? s "-") (str "−" (subs s 1)) s)))

(defn leaf-str
  "Returns the string for `x`, a leaf of a term: a variable, a
  number, or a stray symbol."
  [x]
  (cond (keyword? x) (name x)
        (number? x) (number-str x)
        (num/ratio? x) (number-str (str x))
        (symbol? x) (str x)
        (string? x) x
        :else (pr-str x)))

(defn- leaf-level
  "Returns the precedence level of the leaf `x`. A ratio sits at the
  level of a product: 1/2·x, x·(1/2), x^(1/2). Any other negative
  number sits at the level of a negation: (−2)², sin(−2). Every
  other leaf is an atom."
  [x]
  (cond (num/ratio? x) 3
        (and (number? x) (neg? x)) 4
        :else 6))

(defn- chain
  "Returns [string level] for a left-associative chain at level `p`,
  such as a + b + c or a + (b + c). The first operand is
  parenthesized when its level is below `first-min`. The other
  operands are parenthesized when their level is below `rest-min`."
  ([sep p cs] (chain sep p p (inc p) cs))
  ([sep p first-min rest-min cs]
   [(str/join sep (cons (wrap (first cs) first-min) (map #(wrap % rest-min) (rest cs)))) p]))

(defn- render
  "Returns [string level] for `t`.

  `child` is the function that renders an operand. `ids?` is true
  when the operands are class ids, which means `t` is an e-node. In
  that case no exponent is a number and no child is a numeral."
  [t child ids?]
  (if (vector? t)
    (let [op (first t), args (rest t), cs (mapv child args), n (count cs)]
      (cond
        (zero? n) [(get constants op (name op)) 6]

        (and (= 1 n) (or (= :- op) (= :neg op)))
        (if (and (not ids?) (num/rational? (first args)))
          [(str "−" (paren (first (first cs)))) 4]    ; −(3): the node, not the number −3
          [(str "−" (wrap (first cs) 4)) 4])

        (= :/ op)
        (if (and (not ids?) (= 2 n) (every? integer? args))
          [(str (first (first cs)) "/" (paren (first (second cs)))) 3]   ; 1/(2): the node, not the number 1/2
          (chain "/" 3 4 4 cs))                     ; (a·2)/2, a/(b·c): both sides parenthesized

        (contains? binary op)
        (let [[sep p] (binary op)] (chain sep p cs))

        (= :expt op)
        (let [[b e] args, [bs bp] (first cs), [es ep] (second cs)]
          (cond
            ;; sin²x: a function of one argument raised to a small integer
            (and (not ids?) (vector? b) (functions (first b)) (= 2 (count b)) (small-integer? e))
            (let [[as ap] (child (second b))]
              (if (= 6 ap)
                [(str (name (first b)) (superscript e) as) 2]
                [(str (name (first b)) (superscript e) (paren as)) 6]))
            (and (not ids?) (small-integer? e))
            [(str (wrap [bs bp] 6) (superscript e)) 5]
            :else
            [(str (wrap [bs bp] 6) "^" (wrap [es ep] 6)) 5]))

        (functions op)
        (let [[as ap] (first cs)]
          (if (= 6 ap)
            [(str (name op) " " as) 2]
            [(str (name op) (paren as)) 6]))

        (= :D op)
        (let [[u x] cs]
          [(str "d/d" (first x) " " (wrap u 4)) 2])

        :else
        [(str (name op) (paren (str/join ", " (map first cs)))) 6]))
    [(leaf-str t) (leaf-level t)]))

(defn- render-term [t] (render t render-term false))

(defn term->str
  "Returns the term `t` printed as mathematics."
  [t]
  (first (render-term t)))

(defn rule->str
  "Returns a `[name lhs rhs]` rule printed as the line
  name: lhs → rhs, which is the line that `orrery.parse` reads."
  [[n lhs rhs]]
  (str n ": " (term->str lhs) " → " (term->str rhs)))

(defn rules->str
  "Returns `rules`, given as `[name lhs rhs]` data, printed with one
  rule on each line."
  [rules]
  (str/join "\n" (map rule->str rules)))

(defn class-ref
  "Returns the class id `id` as it prints inside an e-node, such as
  #7."
  [id]
  (str "#" id))

(defn enode->str
  "Returns the e-node `node` printed as mathematics, with its
  children printed as class ids."
  [node]
  (if (vector? node)
    (first (render node (fn [id] [(class-ref id) 6]) true))
    (leaf-str node)))

(def ^:private digits (zipmap "0123456789" (range 10)))

(defn- ref-id
  "Returns the class id that `tok`, a printed #id, refers to."
  [tok]
  (reduce (fn [n c] (+ (* 10 n) (digits c))) 0 (subs tok 1)))

(defn enode-parts
  "Returns the e-node `node` printed in the print mode `mode`, as a
  vector of parts. A part is a string of text, or a map
  `{:id id :text s}` for a child's class id, so that a view can
  draw the ids apart from the text. The texts of the parts join to
  the printed node: `pr-str` in the native format, `enode->str` in
  the notation."
  [node mode]
  (cond
    (not (vector? node)) [(if (= :native mode) (pr-str node) (leaf-str node))]
    (= :native mode) (-> ["[" (pr-str (nth node 0))]
                         (into (mapcat (fn [id] [" " {:id id :text (str id)}])) (subvec node 1))
                         (conj "]"))
    :else (mapv (fn [tok] (if (re-matches #"#\d+" tok) {:id (ref-id tok) :text tok} tok))
                (re-seq #"#\d+|[^#]+" (enode->str node)))))
