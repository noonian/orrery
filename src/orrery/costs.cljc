(ns orrery.costs
  "Holds the cost functions that the cost picker offers. Each cost
  gives another extraction from the same saturated graph.

  A plain cost is `(fn [node child-costs] cost)`, which is the form
  that cromulent.extract takes. The two bendix costs are functions
  of the e-graph that return a plain cost, because the bendix
  default reads the bases of the children of a product from the
  graph.

  Costs are compared with `compare`, so a vector cost is compared
  lexicographically."
  (:require [bendix.core :as bx]
            [bendix.num :as num]
            [clojure.string :as str]
            [cromulent.extract :as ex]))

(defn- op? [node op] (and (vector? node) (= op (first node))))

(defn- charging
  "Returns a cost function that is AST size, except that it charges
  ten for each operator in `expensive`."
  [expensive]
  (fn [node child-costs]
    (+ (if (and (vector? node) (contains? expensive (first node))) 10 1)
       (reduce + 0 child-costs))))

(def prefer-shift "Additions and multiplications cost ten." (charging #{:+ :*}))
(def prefer-add "Multiplications and shifts cost ten." (charging #{:* :<<}))
(def prefer-mul "Additions and shifts cost ten." (charging #{:+ :<<}))

(defn no-shift
  "Returns the cost `[shifts size]`. The fewest shifts win first,
  and the smallest size wins second."
  [node child-costs]
  [(+ (if (op? node :<<) 1 0) (reduce + 0 (map first child-costs)))
   (+ 1 (reduce + 0 (map second child-costs)))])

(def all
  [{:key :ast-size :label "AST size" :blurb "each node costs one, so the smallest tree wins" :fn ex/ast-size}
   {:key :prefer-add :label "prefer additions" :blurb "a multiplication or a shift costs ten" :fn prefer-add}
   {:key :prefer-mul :label "prefer multiplications" :blurb "an addition or a shift costs ten" :fn prefer-mul}
   {:key :prefer-shift :label "prefer shifts" :blurb "an addition or a multiplication costs ten" :fn prefer-shift}
   {:key :no-shift :label "no shifts" :blurb "the fewest shifts win, and then the smallest tree" :fn no-shift}
   {:key :bendix :label "bendix's default"
    :blurb "AST size, plus a sixty-fourth for each operator other than +, · and powers, plus two for each product that repeats a base"
    :of-graph bx/default-cost}
   {:key :no-D :label "no D"
    :blurb "counts what is still under a derivative first, and then uses bendix's default cost"
    :of-graph bx/no-D}])

(defn cost-fn
  "Returns the cromulent cost function behind the cost key `key`,
  for the e-graph `g`."
  [key g]
  (when-let [c (some (fn [c] (when (= key (:key c)) c)) all)]
    (or (:fn c) ((:of-graph c) g))))

(defn cost-str
  "Returns a cost as a string for display:

    - an exact number is written as itself
    - a double that is a multiple of 1/64 is written as k/64 (these
      are bendix's costs in JavaScript)
    - a vector is written element by element"
  [c]
  (cond (vector? c) (str "[" (str/join " " (map cost-str c)) "]")
        (integer? c) (str c)
        (num/ratio? c) (str c)
        (number? c) (let [k (* 64 c)]
                      (if (zero? (mod k 1)) (str (long k) "/64") (str c)))
        :else (pr-str c)))

(defn label [key]
  (some (fn [c] (when (= key (:key c)) (:label c))) all))
