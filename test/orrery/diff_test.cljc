(ns orrery.diff-test
  (:require [clojure.test :refer [deftest is]]
            [cromulent.core :as eg]
            [orrery.diff :as diff]
            [orrery.lessons :as lessons]
            [orrery.run :as run]))

(deftest egraph-values
  (is (diff/egraph? (eg/egraph)))
  (is (not (diff/egraph? {:uf []})))
  (is (not (diff/egraph? [:+ 1 2])))
  (is (not (diff/egraph? (sorted-map 0 1 3 2))) "a sorted map with number keys"))

(deftest the-egg-readme-merge
  ;; (a·2)/2 and (a<<1)/2 merge on rebuild once a·2 = a<<1
  (let [g (eg/egraph)
        [g m] (eg/add g [:* :a 2])
        [g s] (eg/add g [:<< :a 1])
        [g _] (eg/add g [:/ [:* :a 2] 2])
        [g _] (eg/add g [:/ [:<< :a 1] 2])
        [g1 _] (eg/union g m s)
        g2 (eg/rebuild g1)
        d (diff/between g g2)]
    (is (= 2 (count (:merged d))) "the two products, then the two quotients")
    (is (= 2 (count (:absorbing d))))
    (is (= 1 (:collapsed d)) "the two quotient nodes became one")
    (is (= 0 (:added-count d)))
    (is (empty? (:new-classes d)))
    (is (= 5 (eg/class-count g2)))))

(deftest the-first-iteration-of-the-blowup
  (let [r (run/step (lessons/make-run lessons/blowup))
        d (diff/between (run/egraph-at r 0) (run/egraph-at r 1))]
    ;; nine nodes become nineteen. Commutativity gives each of the four
    ;; sums its commuted form: the class of the sum takes the new node
    ;; in and keeps its root, so it grew, and no class of step 0 went
    ;; away or met another. Associativity bears three classes, the
    ;; right-nested pairs. Five untouched roots, four that grew, three
    ;; new: twelve
    (is (= 10 (:added-count d)))
    (is (= 3 (count (:new-classes d))))
    (is (= 7 (count (:added d))) "the four sums that grew and the three new classes")
    (is (empty? (:merged d)))
    (is (empty? (:absorbing d)))
    (is (= 12 (eg/class-count (run/egraph-at r 1))))
    (is (= 0 (:collapsed d)))))
