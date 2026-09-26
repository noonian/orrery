(ns orrery.lessons-test
  (:require [clojure.test :refer [deftest is testing]]
            [cromulent.core :as eg]
            [orrery.expect :as expect]
            [orrery.lay :as lay]
            [orrery.lessons :as lessons]
            [orrery.run :as run]))

(deftest every-live-lesson-meets-its-expectation
  (doseq [c (expect/checks)]
    (is (:ok? c) (pr-str (dissoc c :ok?)))))

(deftest every-live-lesson-has-an-expectation
  (doseq [l lessons/all :when (lessons/live? l)]
    (is (map? (get expect/runs (:key l))) (:title l))
    (doseq [a (:alternatives l)]
      (is (contains? (get expect/runs (:key l)) (:label a)) (str (:title l) " · " (:label a))))))

(deftest prose-renders
  (doseq [lesson lessons/all
          :when (:prose lesson)
          p (:prose lesson)
          x (tree-seq vector? rest p)
          :when (and (vector? x) (= :lay (first x)))]
    (is (string? (lay/term->str (second x))))))

(deftest stepping-is-one-run
  (let [stepped (run/run-all (lessons/make-run lessons/blowup))
        g (peek (:timeline stepped))]
    (is (= (inc (:iterations stepped)) (count (:timeline stepped))))
    (is (= (count (:stats stepped)) (:iterations stepped)))
    (is (= (mapv inc (range (:iterations stepped))) (mapv :iter (:stats stepped))))
    (is (= [31 185] [(eg/class-count g) (eg/node-count g)]))
    (is (nil? (:engine stepped)) "the engine is dropped once the run is done")))

(deftest stopping-a-run
  (let [r (run/stop (run/step (lessons/make-run lessons/blowup)))]
    (is (= :stopped (:stop-reason r)))
    (is (= :done (:status r)))
    (is (= 1 (:iterations r)))
    (is (= r (run/step r)) "a stopped run is a fixed point")))
