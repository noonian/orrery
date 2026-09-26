(ns orrery.eclass-test
  "The facts the selected-class panel shows, on the JVM and Jolt:
  counts, costs, parents and steps, never ids."
  (:require [clojure.test :refer [deftest is testing]]
            [cromulent.core :as eg]
            [orrery.costs :as costs]
            [orrery.eclass :as ec]
            [orrery.lessons :as lessons]
            [orrery.run :as run]))

(defn- finished
  ([lesson] (finished lesson nil))
  ([lesson label]
   (let [alt (some #(when (= label (:label %)) %) (:alternatives lesson))]
     (run/run-all (lessons/make-run lesson (merge (:values lesson) (:values alt)) (or (:opts alt) {}))))))

(defn- final [run] (peek (:timeline run)))

(defn- root-of [run] (eg/find (final run) (:root run)))

(deftest class-of-looks-up-without-adding
  (let [g (final (finished lessons/tree))]
    (is (every? some? (map #(ec/class-of g %) [[:+ [:* 2 :x] :y] [:* 2 :x] :x :y 2])))
    (is (= (ec/class-of g [:+ [:* 2 :x] :y]) (root-of (finished lessons/tree))))
    (is (nil? (ec/class-of g [:+ :q :q])))
    (is (nil? (ec/class-of g :q)))
    (is (= 5 (eg/class-count g)) "the graph is a value; a lookup adds nothing")))

(deftest the-terms-a-class-stands-for
  (testing "a class of one node with unshared children stands for one term"
    (is (= 1 (ec/term-count (final (finished lessons/tree)) (root-of (finished lessons/tree))))))
  (testing "the blowup: every arrangement of the sum, (2n − 2)!/(n − 1)! of them"
    (is (= 1680 (ec/term-count (final (finished lessons/blowup)) (root-of (finished lessons/blowup)))))
    (let [r (finished lessons/blowup "four atoms")] (is (= 120 (ec/term-count (final r) (root-of r))))))
  (testing "the fix holds far fewer"
    (let [r (finished lessons/fix)] (is (= 37 (ec/term-count (final r) (root-of r))))))
  (testing "a class that reaches itself stands for infinitely many"
    (let [r (finished lessons/saturation "egg's rules on 0 + 1·a")]
      (is (= :infinite (ec/term-count (final r) (root-of r))))))
  (testing "what if x = 2: a sum of a four-node class with itself"
    (let [r (finished lessons/what-if)] (is (= 16 (ec/term-count (final r) (root-of r)))))))

(deftest the-cheapest-terms-under-a-cost
  (let [r (finished lessons/taste) g (final r) root (root-of r)]
    (testing "charge additions and multiplications: the shift first, the other two at ten more"
      (let [cf (costs/cost-fn :prefer-shift g)
            table (ec/cheapest g cf 6)]
        (is (= [[:<< :a 1] [:* :a 2] [:+ :a :a]] (mapv :term (nth table root))))
        (is (= [3 12 12] (mapv :cost (nth table root))))
        (is (= [[:<< 3 true] [:* 12 nil] [:+ 12 nil]]
               (mapv (fn [{:keys [node cost best?]}] [(first node) cost best?]) (ec/node-costs g table cf root))))))
    (testing "charge multiplications and shifts: the sum first"
      (let [cf (costs/cost-fn :prefer-add g)]
        (is (= [:+ :a :a] (:term (first (nth (ec/cheapest g cf 6) root)))))))
    (testing "the list is cut at k"
      (let [r (finished lessons/blowup) g (final r)]
        (is (= 6 (count (nth (ec/cheapest g (costs/cost-fn :ast-size g) 6) (root-of r)))))
        (is (every? #(= 9 (:cost %)) (nth (ec/cheapest g (costs/cost-fn :ast-size g) 6) (root-of r)))))))
  (testing "a cycle adds nothing past the k-th: x + 0 = x under egg's rules"
    (let [r (finished lessons/saturation "egg's rules on 0 + 1·a") g (final r)
          terms (nth (ec/cheapest g (costs/cost-fn :ast-size g) 4) (root-of r))]
      (is (= 4 (count terms)))
      (is (= :a (:term (first terms))))
      (is (= [1 3 3 3] (mapv :cost terms))))))

(deftest parents-and-children
  (let [r (finished lessons/congruence) timeline (:timeline r)
        parents-of (fn [g] (ec/parents g (ec/class-of g [:* :a 2])))]
    (testing "before the assertion a·2 has one parent, its quotient"
      (is (= 1 (count (parents-of (nth timeline 0))))))
    (testing "between the union and the rebuild, two parents that read the same, in two classes"
      (let [ps (parents-of (nth timeline 1))]
        (is (= 2 (count ps)))
        (is (= 1 (count (distinct (map :node ps)))) "the same node, canonicalized")
        (is (= 2 (count (distinct (map :class ps)))) "in two classes: the broken invariant")))
    (testing "after the rebuild, one parent"
      (is (= 1 (count (parents-of (nth timeline 2))))))
    (testing "children are the classes the nodes point at"
      (let [g (nth timeline 2) id (ec/class-of g [:* :a 2])]
        (is (= (set (map #(ec/class-of g %) [:a 2 1])) (set (ec/children g id))))))))

(deftest history-along-a-run
  (testing "the input's class of lesson 6: there from the start, grows twice, never merges with an old class"
    (let [r (finished lessons/taste) k (run/last-step r)]
      (is (= {:born 0 :grew [1 2] :merged []} (ec/history (:timeline r) k (root-of r))))))
  (testing "lesson 3: a·2's class is two classes until the assertion, and gains no node by it; the quotient's is two until the rebuild"
    (let [r (finished lessons/congruence) tl (:timeline r) g (nth tl 2)]
      (is (= {:born 0 :grew [] :merged [1]} (ec/history tl 2 (ec/class-of g [:* :a 2]))))
      (is (= {:born 0 :grew [] :merged [2]} (ec/history tl 2 (ec/class-of g [:/ [:* :a 2] 2]))))))
  (testing "traced backwards too: from step 0 the class has the same story"
    (let [r (finished lessons/congruence) tl (:timeline r) g (nth tl 0)]
      (is (= {:born 0 :grew [] :merged [1]} (ec/history tl 0 (ec/class-of g [:* :a 2]))))))
  (testing "the blowup's input class gains nodes at every iteration that added any"
    (let [r (finished lessons/blowup)]
      (is (= {:born 0 :grew [1 2 3 4 5] :merged []} (ec/history (:timeline r) (run/last-step r) (root-of r)))))))
