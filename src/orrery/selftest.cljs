(ns orrery.selftest
  "The self-test tile: cromulent's cross-runtime facts and lesson 7's
  expectation, run in this browser after the page has painted."
  (:require [cromulent.core :as eg]
            [cromulent.extract :as ex]
            [cromulent.smoke :as smoke]
            [orrery.expect :as expect]
            [orrery.lessons :as lessons]
            [orrery.run :as run]
            [replicant.dom :as r]))

(defn- lesson-facts []
  (let [l lessons/blowup
        e (get-in expect/runs [:blowup nil])
        r (run/run-all (lessons/make-run l))
        g (peek (:timeline r))
        actual {:iterations (:iterations r) :stop-reason (:stop-reason r)
                :classes (eg/class-count g) :nodes (eg/node-count g)
                :nodes-per-iteration (run/nodes-per-iteration r)
                :best-cost (:cost (ex/extract g (:root r)))}]
    [{:name "lesson 7 meets its expectation" :expected e :actual actual :ok? (= e actual)}]))

(defn check! []
  (let [el (js/document.getElementById "selftest")
        t0 (js/performance.now)
        cs (into (smoke/checks) (lesson-facts))
        ms (js/Math.round (- (js/performance.now) t0))
        failed (remove :ok? cs)]
    (r/render el
              (if (empty? failed)
                [:span.ok {:id "selftest-ok"} (str "✓ engine self-test: " (count cs) " facts hold in this browser, " ms " ms")]
                [:span.bad {:id "selftest-bad"}
                 (str "✗ engine self-test: " (count failed) " of " (count cs) " failed; first: " (:name (first failed))
                      " expected " (pr-str (:expected (first failed))) " got " (pr-str (:actual (first failed))))]))))
