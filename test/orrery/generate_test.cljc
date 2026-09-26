(ns orrery.generate-test
  (:require [clojure.test :refer [deftest is testing]]
            [orrery.generate :as generate]
            [orrery.input :as input]
            [orrery.lessons :as lessons]))

(deftest the-stream-is-the-same-everywhere
  (let [s (generate/stream 1)]
    (is (= [542 588 125 627 435 366] (vec (repeatedly 6 #(s 1000))))))
  (let [s (generate/stream 0) t (generate/stream 0)]
    (is (= (s 10) (t 10)) "seed nought is a seed"))
  (is (= ((generate/stream 5) 7) ((generate/stream -5) 7)) "a negative seed is its magnitude")
  (let [s (generate/stream 123)]
    (is (every? #(< -1 % 3) (repeatedly 100 #(s 3))))))

(deftest one-seed-draws-one-term
  (is (= [[:expt :x 3] :x [:+ [:/ :x 2] :x] [:- [:sin :x] [:+ [:sin 3] [:* 2 :y]]] :x]
         (mapv (fn [seed] (:term (generate/a-term (generate/stream seed) nil))) (range 1 6)))))

(deftest shapes-fill-their-slots
  (let [s (generate/stream 3)
        sig {:leaves [:a] :leaf-pct 0 :shapes [[:+ :t :same]]}]
    (is (= [:+ [:+ :a :a] [:+ :a :a]] (generate/term s sig 2)) ":same repeats the slot before it"))
  (let [s (generate/stream 3)
        sig {:leaves [:a :b] :leaf-pct 0 :shapes [[:- :t :flip]]}
        [_ l r] (generate/term s sig 2)]
    (is (vector? l))
    (is (= r (into [(first l)] (reverse (rest l)))) ":flip reverses the children of the slot before it")))

(deftest an-arrangement-is-the-same-atoms
  (doseq [seed (range 20)]
    (let [t (generate/arrangement (generate/stream seed) [:a0 :a1 :a2 :a3 :a4])]
      (is (= [:a0 :a1 :a2 :a3 :a4] (sort (generate/leaves t))))
      (is (every? #(= :+ (first %)) (filter vector? (tree-seq vector? rest t)))))))

(deftest every-draw-is-a-term-the-page-takes
  (doseq [l lessons/all
          seed (range 30)
          :let [values (generate/draw l (generate/stream seed) (:values l))]
          [k v] values]
    (case k
      :wrapper (do (is (nil? (input/term-problem (clojure.walk/postwalk-replace {'?x :z} v))) (pr-str v))
                   (is (some #{'?x} (generate/leaves v)) "a wrapper mentions ?x"))
      (do (is (nil? (input/term-problem v)) (str (:title l) " " (pr-str v)))
          (when (= :term k)
            (is (<= (input/leaf-count v) input/leaf-limit) (str (:title l) " " (pr-str v))))))))

(deftest the-planted-draws-plant
  (doseq [seed (range 20)]
    (let [t (:term (generate/a-pythagorean-term (generate/stream seed) nil))
          subterms (tree-seq vector? rest t)]
      (is (some #(and (vector? %) (= :expt (first %)) (= :sin (first (second %)))) subterms))
      (is (some #(and (vector? %) (= :expt (first %)) (= :cos (first (second %)))) subterms)))
    (let [{:keys [term lhs rhs]} (generate/a-what-if (generate/stream seed) nil)
          vs (generate/variables term)]
      (is (some #{lhs} vs) "the what-if is about a variable of the term")
      (is (or (number? rhs) (some #{rhs} vs)))
      (is (not= lhs rhs)))
    (let [{:keys [lhs rhs wrapper]} (generate/an-equation (generate/stream seed) nil)]
      (is (not= lhs rhs))
      (is (vector? wrapper)))
    (let [{:keys [term]} (generate/a-function (generate/stream seed) {:var :y})]
      (is (some #{:y} (generate/leaves term)) "a function of the variable in force"))))
