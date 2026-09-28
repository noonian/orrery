(ns orrery.normal-test
  (:require [clojure.test :refer [deftest is]]
            [bendix.core :as bx]
            [bendix.rules]
            [cromulent.core :as eg]
            [orrery.notation :as notation]
            [orrery.normal :as normal]
            [orrery.working]))

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
    (is (= [:expt (symbol (notation/class-ref (eg/find g s))) 2] (:term (normal/form g p))) "an opaque class prints as its id")
    (is (nil? (normal/form (first (eg/add (eg/egraph) :x)) 0)) "no analysis, no form")))

(deftest an-opaque-class-rendered-by-a-term
  (let [g (bx/egraph)
        [g p] (eg/add g [:+ [:* [:sin :x] [:sin :x]] :a])
        g (eg/rebuild g)
        [_ s] (eg/add g [:sin :x])]
    (is (= [:+ [:expt [:sin :x] 2] :a] (:term (normal/form g p (fn [id] (when (= id (eg/find g s)) [:sin :x]))))) "higher degree first")
    (is (= [:+ [:expt (symbol (notation/class-ref (eg/find g s))) 2] :a] (:term (normal/form g p))) "without a renderer, the id")))

(deftest how-a-class-got-its-polynomial
  (let [g (bx/egraph)
        [g root] (eg/add g [:- [:+ [:* [:+ :x 1] [:- :x 1]] 1] [:* :x :x]])
        [g big] (eg/add g [:expt [:+ :a :b :c :d] 20])
        [g s] (eg/add g [:sin :y])
        g (eg/rebuild g)
        [_ sq] (eg/add g [:* :x :x])
        [_ x] (eg/add g :x)
        w (normal/workings g sq)]
    (is (= {:kind :polynomial :term [:expt :x 2]} (:form w)))
    (is (= :agree (:verdict w)) "x·x and (x + 1)·(x − 1) + 1 both make x²")
    (is (= #{[:* :x :x] [:+ [:- [:expt :x 2] 1] 1]} (set (map :reads (:nodes w)))) "each node read over its children's polynomials")
    (is (every? #(= :operation (:kind %)) (:nodes w)))
    (is (= :one (:verdict (normal/workings g root))))
    (is (= {:kind :polynomial :term 0} (:form (normal/workings g root))))
    (is (= [{:node :x :kind :variable :reads nil :form {:kind :polynomial :term :x}}] (:nodes (normal/workings g x))))
    (is (= :too-big (:verdict (normal/workings g big))))
    (let [w (normal/workings g s)]
      (is (= :atom (:verdict w)))
      (is (= :unknown (:kind (first (:nodes w)))) "the ring cannot see inside sin y"))
    (is (nil? (normal/workings (first (eg/add (eg/egraph) :x)) 0)) "no analysis, no workings")))

(deftest two-forms-and-the-one-kept
  (let [r (bx/saturate [:expt [:sin :x] 2] {:rules bendix.rules/trig :timeline? false})
        g (:egraph r)
        [_ s2] (eg/add g [:expt [:sin :x] 2])
        [_ s] (eg/add g [:sin :x])
        w (normal/workings g s2)]
    (is (= :disagree (:verdict w)) "sin²x and 1 − cos²x meet in one class")
    (is (= [:monomials] (:why w)) "sin²x has fewer monomials")
    (is (= [{:id (eg/find g s) :term nil}] (:unknowns w)) "its unknown is the class of sin x"))
  (is (= :unknowns (normal/why-kept {{:x 1} 1} {{7 1} 1})))
  (is (= :degree (normal/why-kept {{:x 1} 1} {{:x 2} 1})))
  (is (= :numbers (normal/why-kept {{:x 1} 1} {{:x 1} 3}))))

(deftest a-polynomial-spelled-for-a-reader
  (is (= [:- [:expt :x 2] 1] (normal/spell [:+ [:expt :x 2] -1])))
  (is (= [:- 1 [:expt :c 2]] (normal/spell [:+ [:* -1 [:expt :c 2]] 1])) "a positive monomial first")
  (is (= [:+ [:- :a [:* 2 :c]] :b] (normal/spell [:+ :a [:* -2 :c] :b])) "the order is kept")
  (is (= [:+ :a0 :a1 [:* 2 :a2]] (normal/spell [:+ :a0 :a1 [:* 2 :a2]])) "no negative monomial, no change")
  (is (= [:neg :x] (normal/spell [:* -1 :x])))
  (is (= -1 (normal/spell -1)) "a number keeps its sign"))

(deftest operations-worked-through
  (let [w (orrery.working/work :product [[:+ :x 1] [:- :x 1]])]
    (is (:grid? w))
    (is (= [[:expt :x 2] [:neg :x] :x -1] (map :term (apply concat (:cells w)))))
    (is (= [nil :cancels :cancels nil] (map :mark (apply concat (:cells w)))) "−x and x cancel")
    (is (= [{:parts [[:neg :x] :x] :sum 0 :cancels? true}] (:like w)))
    (is (= [:- [:expt :x 2] 1] (:result w))))
  (let [w (orrery.working/work :reduction [[:+ [:expt [:sin :x] 2] [:expt [:cos :x] 2] :a] [:sin :x] [:- 1 [:expt [:cos :x] 2]]])]
    (is (= [{:before [:expt [:sin :x] 2] :after [:- 1 [:expt [:cos :x] 2]]}] (:steps w)))
    (is (= [:+ [:- 1 [:expt [:cos :x] 2]] [:expt [:cos :x] 2] :a] (:substituted w)) "each monomial replaced, not yet collected")
    (is (= [:+ :a 1] (:result w))))
  (let [w (orrery.working/work :derivative [[:expt [:+ :x 1] 3] :x])]
    (is (= [[:* 3 [:expt :x 2]] [:* 6 :x] 3 0] (map :after (:steps w))))
    (is (= [:+ [:* 3 [:expt :x 2]] [:* 6 :x] 3] (:result w))))
  (is (nil? (orrery.working/work :reduction [[:expt [:sin :x] 2] [:+ :x 1] :y])) "the base must be an atom")
  (is (nil? (orrery.working/work :product [[:expt [:+ :a :b :c :d] 20] :x])) "a term past the threshold is worth no polynomial")
  (is (false? (:grid? (orrery.working/work :product [[:+ :a :b :c :d :e :f] :x]))) "past the limit, no grid"))
