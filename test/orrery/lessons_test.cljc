(ns orrery.lessons-test
  (:require [clojure.test :refer [deftest is testing]]
            [cromulent.core :as eg]
            [cromulent.extract :as ex]
            [orrery.expect :as expect]
            [orrery.lay :as lay]
            [orrery.lessons :as lessons]
            [orrery.run :as run]))

(defn- observe [run]
  (let [g (peek (:timeline run))]
    {:iterations (:iterations run)
     :stop-reason (:stop-reason run)
     :classes (eg/class-count g)
     :nodes (eg/node-count g)
     :nodes-per-iteration (run/nodes-per-iteration run)
     :best-cost (:cost (ex/extract g (:root run)))}))

(deftest every-live-lesson-meets-its-expectation
  (doseq [lesson lessons/all
          :when (and (lessons/live? lesson) (= :embiggen (:kind lesson)))]
    (testing (:title lesson)
      (let [expected (get expect/runs (:key lesson))]
        (is (map? expected) "every live lesson has an expectation")
        (doseq [[label {:keys [nodes-per-iteration] :as e}] expected]
          (let [alt (some (fn [a] (when (= label (:label a)) a)) (:alternatives lesson))
                r (run/run-all (lessons/make-run lesson (if alt (:term alt) (:term lesson)) (or (:opts alt) {})))
                o (observe r)]
            (testing (or label "the curated example")
              (is (= (dissoc e :nodes-per-iteration) (dissoc o :nodes-per-iteration)))
              (when (seq nodes-per-iteration)
                (is (= nodes-per-iteration (:nodes-per-iteration o)))))))))))

(deftest prose-renders
  (doseq [lesson lessons/all
          :when (:prose lesson)
          p (:prose lesson)
          x (tree-seq vector? rest p)
          :when (and (vector? x) (= :lay (first x)))]
    (is (string? (lay/term->str (second x))))))

(deftest stepping-equals-one-run
  (let [lesson lessons/blowup
        stepped (run/run-all (lessons/make-run lesson))
        g (peek (:timeline stepped))]
    (is (= (inc (:iterations stepped)) (count (:timeline stepped))))
    (is (= (count (:stats stepped)) (:iterations stepped)))
    (is (= (mapv inc (range (:iterations stepped))) (mapv :iter (:stats stepped))))
    (is (= [31 185] [(eg/class-count g) (eg/node-count g)]))))
