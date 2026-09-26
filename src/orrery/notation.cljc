(ns orrery.notation
  "The notation printer: the canonical tagged vectors printed as
  mathematics, for display. [:+ [:* 2 :x] :y] prints as 2·x + y,
  [:expt [:sin :x] 2] as sin²x, [:D [:sin [:* 2 :x]] :x] as
  d/dx sin(2·x). Nesting is shown faithfully: [:+ :a [:+ :b :c]] is
  a + (b + c) while [:+ [:+ :a :b] :c] is a + b + c, so an arrangement
  of a sum stays visible. E-nodes, whose children are class ids, print
  the ids as #7. Pure and the same on every runtime.

  orrery.parse reads the notation back, so what prints here must read
  as the term it came from: a numeral's spelling absorbs a leading
  minus and a slash between integers there, so the quotient node of
  two integers prints 1/(2), the negation of a numeral −(3), and a
  negative numeral is parenthesized wherever a negation would be,
  (−2)²."
  (:require [bendix.num :as num]
            [clojure.string :as str]))

;; Precedence levels: 0 shifts, 1 sums, 2 a function applied to a bare
;; atom (sin x) and a derivative (d/dx u), 3 products, 4 unary minus,
;; 5 powers, 6 atoms and anything already parenthesized. An operand
;; below its operator's level is parenthesized; in a chain the operands
;; after the first are parenthesized at the operator's level too.

(def ^:private binary
  {:+ [" + " 1] :- [" − " 1] :* ["·" 3] :/ ["/" 3] :<< [" << " 0] :>> [" >> " 0]})

(def functions
  "The operators that print as a function of one argument, sin x,
  and read back as one."
  #{:sin :cos :tan :exp :log :sqrt :abs})

(def ^:private constants {:pi "π" :e "e"})

(def ^:private superscripts
  {\0 "⁰" \1 "¹" \2 "²" \3 "³" \4 "⁴" \5 "⁵" \6 "⁶" \7 "⁷" \8 "⁸" \9 "⁹" \- "⁻"})

(defn- small-integer? [x] (and (integer? x) (< -100 x 100)))

(defn- superscript [n] (apply str (map superscripts (str n))))

(defn- paren [s] (str "(" s ")"))

(defn- wrap
  "s, parenthesized when its level is below min-level."
  [[s level] min-level]
  (if (< level min-level) (paren s) s))

(defn- number-str [n]
  (let [s (str n)]
    (if (str/starts-with? s "-") (str "−" (subs s 1)) s)))

(defn leaf-str
  "A leaf of a term: a variable, a number, or a stray symbol."
  [x]
  (cond (keyword? x) (name x)
        (number? x) (number-str x)
        (num/ratio? x) (number-str (str x))
        (symbol? x) (str x)
        (string? x) x
        :else (pr-str x)))

(defn- leaf-level
  "A ratio sits at the product level (1/2·x, x·(1/2), x^(1/2)); a
  negative integer at the level of a negation ((−2)², sin(−2))."
  [x]
  (cond (num/ratio? x) 3
        (and (number? x) (neg? x)) 4
        :else 6))

(defn- chain
  "A left-associative chain at level p: a + b + c, a + (b + c). The
  first operand is parenthesized below first-min, the rest below
  rest-min."
  ([sep p cs] (chain sep p p (inc p) cs))
  ([sep p first-min rest-min cs]
   [(str/join sep (cons (wrap (first cs) first-min) (map #(wrap % rest-min) (rest cs)))) p]))

(defn- render
  "[string level] for t. child renders an operand; ids? says the
  operands are class ids (an e-node), so no exponent is a number and
  no child is a numeral."
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
  "t as mathematics."
  [t]
  (first (render-term t)))

(defn rule->str
  "A [name lhs rhs] rule as name: lhs → rhs, the line orrery.parse reads."
  [[n lhs rhs]]
  (str n ": " (term->str lhs) " → " (term->str rhs)))

(defn rules->str
  "Rules as [name lhs rhs] data, one line each."
  [rules]
  (str/join "\n" (map rule->str rules)))

(defn class-ref
  "How a class id prints inside an e-node."
  [id]
  (str "#" id))

(defn enode->str
  "An e-node as mathematics, its children printed as class ids."
  [node]
  (if (vector? node)
    (first (render node (fn [id] [(class-ref id) 6]) true))
    (leaf-str node)))
