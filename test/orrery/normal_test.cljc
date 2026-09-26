(ns orrery.normal-test
  (:require [clojure.test :refer [deftest is]]
            [bendix.core :as bx]
            [cromulent.core :as eg]
            [orrery.lay :as lay]
            [orrery.normal :as normal]))

(deftest the-form-beside-a-class
  (let [g (bx/egraph)
        [g sum] (eg/add g [:+ [:+ :a1 :a0] [:* 2 :a2]])
        [g s] (eg/add g [:sin :x])
        [g p] (eg/add g [:* [:sin :x] [:sin :x]])
        g (eg/rebuild g)]
    (is (normal/analysis? g))
    (is (not (normal/analysis? (eg/egraph))))
    (is (= {:kind :polynomial :term [:+ :a0 :a1 [:* 2 :a2]]} (normal/form g sum)) "canonical order, whatever was written")
    (is (= {:kind :atom :id (eg/find g s)} (normal/form g s)) "sin x is its own atom")
    (is (= [:expt (symbol (lay/class-ref (eg/find g s))) 2] (:term (normal/form g p))) "an opaque class prints as its id")
    (is (nil? (normal/form (first (eg/add (eg/egraph) :x)) 0)) "no analysis, no form")))

(deftest an-opaque-class-rendered-by-a-term
  (let [g (bx/egraph)
        [g p] (eg/add g [:+ [:* [:sin :x] [:sin :x]] :a])
        g (eg/rebuild g)
        [_ s] (eg/add g [:sin :x])]
    (is (= [:+ [:expt [:sin :x] 2] :a] (:term (normal/form g p (fn [id] (when (= id (eg/find g s)) [:sin :x]))))) "higher degree first")
    (is (= [:+ [:expt (symbol (lay/class-ref (eg/find g s))) 2] :a] (:term (normal/form g p))) "without a renderer, the id")))
