(ns orrery.costs
  "The cost picker's functions: the same saturated graph, another
  extraction. Each is (fn [node child-costs] cost) as
  cromulent.extract takes; costs compare with `compare`, so a vector
  cost is lexicographic."
  (:require [cromulent.extract :as ex]))

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
   {:key :no-shift :label "no shifts" :blurb "the fewest shifts, then the smallest tree" :fn no-shift}])

(defn cost-fn
  "The function behind a cost key."
  [key]
  (some (fn [c] (when (= key (:key c)) (:fn c))) all))

(defn label [key]
  (some (fn [c] (when (= key (:key c)) (:label c))) all))
