(ns orrery.costs
  "The cost picker's functions: the same saturated graph, another
  extraction. Each is (fn [node child-costs] cost) as
  cromulent.extract takes; costs compare with `compare`, so a vector
  cost is lexicographic."
  (:require [cromulent.extract :as ex]))

(defn- op? [node op] (and (vector? node) (= op (first node))))

(defn prefer-shift
  "AST size with a multiplication charged ten: prefers a << 1 to a·2."
  [node child-costs]
  (+ (if (op? node :*) 10 1) (reduce + 0 child-costs)))

(defn no-shift
  "[shifts size]: the fewest shifts first, size second."
  [node child-costs]
  [(+ (if (op? node :<<) 1 0) (reduce + 0 (map first child-costs)))
   (+ 1 (reduce + 0 (map second child-costs)))])

(def all
  [{:key :ast-size :label "AST size" :blurb "one per node; the smallest tree wins" :fn ex/ast-size}
   {:key :prefer-shift :label "prefer shifts" :blurb "a multiplication costs ten" :fn prefer-shift}
   {:key :no-shift :label "no shifts" :blurb "the fewest shifts, then the smallest tree" :fn no-shift}])

(defn cost-fn
  "The function behind a cost key."
  [key]
  (some (fn [c] (when (= key (:key c)) (:fn c))) all))
