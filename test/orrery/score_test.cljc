(ns orrery.score-test
  (:require [clojure.test :refer [deftest is testing]]
            [orrery.generate :as generate]
            [orrery.lessons :as lessons]
            [orrery.run :as run]
            [orrery.score :as score]))

(defn- curated [l] (run/run-all (lessons/make-run l)))

(defn- feature [l k] (get (score/features l (:values l) (curated l)) k))

(deftest the-features-read-the-curated-lessons
  (testing "the same counts the expectation table pins, as features"
    (is (= {:raw 5 :score 1.0} (feature lessons/tree :size)))
    (is (= 1.75 (:raw (feature lessons/sharing :sharing))) "seven tree nodes over four")
    (is (= {:raw 1 :score 0.5} (feature lessons/congruence :congruence)) "the two quotients, merged on rebuild")
    (is (= {:raw 1 :score 0.5} (feature lessons/what-if :congruence)))
    (is (< 0.9 (:score (feature lessons/blowup :growth))) "nine nodes to a hundred and eighty-five")
    (is (= 1.0 (:score (feature lessons/blowup :saturated))))
    (is (= 0.0 (:score (feature lessons/blowup :node-limit))))
    (is (= {:raw 7 :score 1.0} (feature lessons/blowup :iterations)))
    (is (= {:raw 1 :score 0.2} (feature lessons/rule :iterations)))
    (is (= 7 (:raw (feature lessons/fix :analysis-merges))) "seven proposals the analysis had already made")
    (is (= 7 (:raw (feature lessons/polynomial-rule :rule-fired))) "pythagoras, five then two")
    (is (= 0 (:raw (feature lessons/blowup :rule-fired))) "pattern rules are not normal-form rules")
    (is (<= 3 (:raw (feature lessons/taste :disagreement))) "three costs, three answers, and a tie under the fourth")
    (is (= {:raw true :score 1.0} (feature lessons/differentiation :derivative-free)))
    (is (< 1.0 (:raw (feature lessons/polynomial-rule :shrink))) "a + b + 1 is smaller than the input"))
  (testing "every score is in [0, 1]"
    (doseq [l lessons/all
            [k {:keys [score]}] (score/features l (:values l) (curated l))]
      (is (<= 0.0 score 1.0) (str (:title l) " " k)))))

(deftest the-score-is-a-weighted-mean
  (let [fs {:a {:score 1.0} :b {:score 0.0}}]
    (is (= 1.0 (score/score {:a 1} fs)))
    (is (= 0.5 (score/score {:a 1 :b 1} fs)))
    (is (= 0.75 (score/score {:a 3 :b 1} fs)))
    (is (= 0.0 (score/score {:c 1} fs)) "a feature that is missing scores nought")
    (is (= 0.0 (score/score {} fs)))))

(deftest every-lesson-has-a-bank
  (doseq [l lessons/all :when (:surprise l)]   ; the introduction offers none
    (let [c (score/surprise l (:values l) {} 7)
          wants (get-in l [:surprise :wants])]
      (is (map? (:drawn c)) (:title l))
      (is (= :done (get-in c [:run :status])) (:title l))
      (is (<= 0.0 (:score c) 1.0) (:title l))
      (is (every? (set (keys (:features c))) (keys wants)) (str (:title l) " wants a feature that exists"))
      (is (<= 1 (:shapes c) (:of c)) (:title l))
      (is (re-find #"^drawn from \d+ candidates, \d+ shapes of result; score \d\.\d\d: " (score/explain c wants)) (:title l)))))

(deftest the-bank-keeps-one-of-each-shape
  (let [b (score/bank lessons/blowup (:values lessons/blowup) {} (generate/stream 11) 8)]
    (is (= (count b) (count (distinct (map (comp score/shape :run) b)))))
    (is (every? #(= :saturated (get-in % [:run :stop-reason])) b))))

(deftest the-pick-leans-on-the-score
  (let [bank [{:score 0.0 :name :weak} {:score 1.0 :name :strong}]
        picks (frequencies (map (fn [seed] (:name (score/pick bank (generate/stream seed)))) (range 200)))]
    (is (< 150 (get picks :strong 0)) (pr-str picks))
    (is (pos? (get picks :weak 0)) "a weak candidate keeps a small chance")))
