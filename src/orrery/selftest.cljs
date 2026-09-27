(ns orrery.selftest
  "The self-test tile. It runs these checks in this browser, after
  the page has painted:

    - cromulent's and bendix's cross-runtime facts
    - every lesson's expectation
    - every lesson's values, read back from their notation

  The JVM and Jolt suites assert the same table."
  (:require [bendix.smoke :as bendix-smoke]
            [cromulent.smoke :as smoke]
            [orrery.expect :as expect]
            [replicant.dom :as r]))

(defn check! []
  (let [el (js/document.getElementById "selftest")
        t0 (js/performance.now)
        cs (-> (smoke/checks) (into (bendix-smoke/checks)) (into (expect/checks)) (into (expect/notation-checks)))
        ms (js/Math.round (- (js/performance.now) t0))
        failed (remove :ok? cs)]
    (r/render el
              (if (empty? failed)
                [:span.ok {:id "selftest-ok"}
                 (str "✓ engine self-test: " (count cs) " facts hold in this browser, " ms " ms")]
                [:span.bad {:id "selftest-bad"}
                 (str "✗ engine self-test: " (count failed) " of " (count cs) " failed; first: " (:name (first failed))
                      " expected " (pr-str (:expected (first failed))) " got " (pr-str (:actual (first failed))))]))))
