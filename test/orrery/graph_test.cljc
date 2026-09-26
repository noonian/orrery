(ns orrery.graph-test
  "The picture: for every lesson at every step, a box per class with
  every node in it, an edge per child slot, layers that put every
  edge downwards except the ones that close a cycle, boxes that do
  not overlap and stay inside the picture; counts pinned from a JVM
  run, never positions, which follow the ids."
  (:require [clojure.test :refer [deftest is testing]]
            [cromulent.core :as eg]
            [cromulent.term :as term]
            [orrery.eclass :as eclass]
            [orrery.graph :as graph]
            [orrery.lessons :as lessons]
            [orrery.notation :as notation]
            [orrery.run :as run]))

(defn- final [lesson] (peek (:timeline (run/run-all (lessons/make-run lesson)))))

(defn- overlapping?
  "Do two boxes of one layer overlap horizontally?"
  [a b]
  (and (= (:y a) (:y b))
       (< (:x a) (+ (:x b) (:w b)))
       (< (:x b) (+ (:x a) (:w a)))))

(defn- well-formed
  "Every invariant of a layout of g drawing the classes in only (or all)."
  [g only]
  (let [l (graph/layout g {:only only :node-label notation/enode->str})
        {:keys [width height classes edges]} l
        by-id (into {} (map (fn [b] [(:id b) b])) classes)
        drawn (or only (set (eg/roots g)))]
    (is (= drawn (set (keys by-id))) "a box per drawn class")
    (doseq [b classes]
      (is (= (set (eg/nodes g (:id b))) (set (map :node (:nodes b)))) "every node of the class, once")
      (is (and (>= (:x b) 0) (>= (:y b) 0) (<= (+ (:x b) (:w b)) width) (<= (+ (:y b) (:h b)) height)) "inside the picture")
      (doseq [{:keys [x y w h label]} (:nodes b)]
        (is (and (>= x 0) (>= y 0) (<= (+ x w) (:w b)) (<= (+ y h) (:h b))) "a node inside its box")
        (is (string? label))))
    (doseq [a classes, b classes :when (< (:id a) (:id b))]
      (is (not (overlapping? a b)) (str "boxes " (:id a) " and " (:id b) " overlap")))
    (is (= (count edges)
           (reduce + 0 (for [b classes, {:keys [node]} (:nodes b) :when (term/compound? node)]
                         (count (filter #(contains? drawn (eg/find g %)) (term/children node))))))
        "an edge per child slot pointing at a drawn class")
    (doseq [{:keys [from to y1 y2 back?]} edges]
      (is (contains? by-id from))
      (is (contains? by-id to))
      (if back?
        (is (<= (:y (by-id to)) (:y (by-id from))) "a cycle-closing edge points at a class in the same layer or above")
        (is (< y1 y2) "every other edge points down")))
    l))

(deftest every-lesson-at-every-step
  (doseq [l lessons/all :when (lessons/live? l)
          :let [run (run/run-all (lessons/make-run l))]
          [k g] (map-indexed vector (:timeline run))]
    (testing (str (:title l) ", step " k)
      (well-formed g nil))))

(deftest the-counts
  (testing "lesson 2: four boxes, four nodes, four edges, two of them from the product to one class"
    (let [l (well-formed (final lessons/sharing) nil)]
      (is (= 4 (count (:classes l))))
      (is (= 4 (count (:edges l))))
      (is (= 3 (:layers l)))
      (is (= 1 (count (distinct (map :to (filter #(= [:* 2 2] (:node %)) (:edges l)))))))))
  (testing "lesson 7: the blowup, five layers of subsets, three hundred and sixty edges"
    (let [l (well-formed (final lessons/blowup) nil)]
      (is (= 31 (count (:classes l))))
      (is (= 185 (reduce + 0 (map (comp count :nodes) (:classes l)))))
      (is (= 360 (count (:edges l))))
      (is (= 5 (:layers l)))
      (is (empty? (filter :back? (:edges l))))))
  (testing "lesson 9: the class that reaches itself closes a cycle"
    (let [g (final lessons/polynomial-rule)
          l (well-formed g nil)]
      (is (pos? (count (filter :back? (:edges l)))))
      (is (= :infinite (eclass/term-count g (eclass/class-of g [:expt [:sin :x] 2]))))))
  (testing "lesson 3, step 1: the merged class holds two nodes, and two quotients point at it"
    (let [g (nth (:timeline (run/run-all (lessons/make-run lessons/congruence))) 1)
          l (well-formed g nil)
          m (eclass/class-of g [:* :a 2])]
      (is (= 6 (count (:classes l))))
      (is (= 2 (count (:nodes (first (filter #(= m (:id %)) (:classes l)))))))
      (is (= 2 (count (filter #(= m (:to %)) (:edges l))))))))

(deftest the-filter-and-the-labels
  (let [g (final lessons/sharing)
        sum (eclass/class-of g [:+ :x 1])]
    (is (= 3 (count (graph/reachable g sum))) "x + 1 reaches itself, x and 1")
    (is (= 4 (count (graph/reachable g (eclass/class-of g [:* [:+ :x 1] [:+ :x 1]])))))
    (let [l (well-formed g (graph/reachable g sum))]
      (is (= 3 (count (:classes l))))
      (is (= 2 (count (:edges l))))))
  (let [g (final lessons/fix)
        l (graph/layout g {:node-label notation/enode->str :class-label (fn [id] (str "poly of " id))})]
    (is (every? #(= (str "poly of " (:id %)) (:sub %)) (:classes l)) "the class label sits in the box")
    (is (every? #(>= (:w %) (+ (* 2 graph/box-pad) (* graph/char-w (count (str "#" (:id %) "  poly of " (:id %)))))) (:classes l))
        "and the box is wide enough for it"))
  (testing "a class of many nodes wraps into rows"
    (let [l (graph/layout (final lessons/blowup) {:node-label notation/enode->str})
          big (first (sort-by (comp - count :nodes) (:classes l)))]
      (is (= 30 (count (:nodes big))))
      (is (= 5 (count (distinct (map :y (:nodes big)))))))))
