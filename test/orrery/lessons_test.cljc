(ns orrery.lessons-test
  (:require [clojure.test :refer [deftest is testing]]
            [cromulent.core :as eg]
            [orrery.eclass :as eclass]
            [orrery.expect :as expect]
            [orrery.notation :as notation]
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

(deftest prose-widgets-hold-for-the-curated-run
  (doseq [l lessons/all
          :when (lessons/live? l)
          :let [run (run/run-all (lessons/make-run l))
                last (run/last-step run)
                labels (set (map :label (:alternatives l)))
                costs (set (:costs l))
                where (fn [w] (str (:title l) ": " (pr-str w)))]
          w (lessons/widgets l)
          :let [[kind a b c] w]]
    (case kind
      :notation (is (string? (notation/term->str a)) (where w))
      :native (is (string? (pr-str a)) (where w))
      :step (is (<= 0 a last) (where w))
      :select (do (is (string? b) (where w))
                  (is (or (nil? c) (<= 0 c last)) (where w))
                  (is (some? (eclass/class-of (run/egraph-at run (or c last)) a)) (where w)))
      :cost (is (contains? costs a) (where w))
      :alternative (is (contains? labels a) (where w))
      :print (is (contains? #{:native :notation} a) (where w))
      :cite (do (is (seq (rest w)) (where w))
                (is (every? #(contains? lessons/reading %) (rest w)) (where w)))))
  (is (seq (lessons/widgets lessons/tree))))

(deftest every-citation-resolves-and-every-work-is-cited
  (doseq [l lessons/all :when (lessons/live? l)]
    (is (seq (lessons/credits l)) (str (:title l) " cites someone"))
    (doseq [k (lessons/credits l)]
      (is (contains? lessons/reading k) (str (:title l) ": " k))))
  (doseq [[k work] lessons/reading]
    (is (some #(some #{k} (lessons/credits %)) lessons/all) (str k " is cited by a lesson"))
    (is (every? work [:who :what :where :year :short]) (str k))
    (when-let [url (:url work)]
      (is (re-find #"^https://" url) (str k)))))

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
