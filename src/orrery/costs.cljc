(ns orrery.costs
  "The cost picker's functions: the same saturated graph, another
  extraction. A plain cost is (fn [node child-costs] cost) as
  cromulent.extract takes; bendix's two are functions of the e-graph
  that return one, since the default reads the bases of a product's
  children from the graph. Costs compare with `compare`, so a vector
  cost is lexicographic."
  (:require [bendix.core :as bx]
            [bendix.num :as num]
            [clojure.string :as str]
            [cromulent.extract :as ex]))

(defn- op? [node op] (and (vector? node) (= op (first node))))

(defn- charging
  "AST size, with the operators in expensive charged ten."
  [expensive]
  (fn [node child-costs]
    (+ (if (and (vector? node) (contains? expensive (first node))) 10 1)
       (reduce + 0 child-costs))))

(def prefer-shift "Additions and multiplications cost ten." (charging #{:+ :*}))
(def prefer-add "Multiplications and shifts cost ten." (charging #{:* :<<}))
(def prefer-mul "Additions and shifts cost ten." (charging #{:+ :<<}))

(defn no-shift
  "[shifts size]: the fewest shifts first, size second."
  [node child-costs]
  [(+ (if (op? node :<<) 1 0) (reduce + 0 (map first child-costs)))
   (+ 1 (reduce + 0 (map second child-costs)))])

(def all
  [{:key :ast-size :label "AST size" :blurb "one per node; the smallest tree wins" :fn ex/ast-size}
   {:key :prefer-add :label "prefer additions" :blurb "a multiplication or a shift costs ten" :fn prefer-add}
   {:key :prefer-mul :label "prefer multiplications" :blurb "an addition or a shift costs ten" :fn prefer-mul}
   {:key :prefer-shift :label "prefer shifts" :blurb "an addition or a multiplication costs ten" :fn prefer-shift}
   {:key :no-shift :label "no shifts" :blurb "the fewest shifts, then the smallest tree" :fn no-shift}
   {:key :bendix :label "bendix's default"
    :blurb "AST size, a sixty-fourth more for an operator outside +, · and powers, and two for a product that repeats a base"
    :of-graph bx/default-cost}
   {:key :no-D :label "no D"
    :blurb "what is still under a derivative first, then bendix's default"
    :of-graph bx/no-D}])

(defn cost-fn
  "The cromulent cost function behind a cost key, for the e-graph g."
  [key g]
  (when-let [c (some (fn [c] (when (= key (:key c)) c)) all)]
    (or (:fn c) ((:of-graph c) g))))

(defn cost-str
  "A cost for display: an exact number as itself, a double that is a
  multiple of 1/64 (bendix's costs in JavaScript) as k/64, a vector
  elementwise."
  [c]
  (cond (vector? c) (str "[" (str/join " " (map cost-str c)) "]")
        (integer? c) (str c)
        (num/ratio? c) (str c)
        (number? c) (let [k (* 64 c)]
                      (if (zero? (mod k 1)) (str (long k) "/64") (str c)))
        :else (pr-str c)))

(defn label [key]
  (some (fn [c] (when (= key (:key c)) (:label c))) all))
