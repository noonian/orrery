(ns orrery.selftest
  "The self-test tile: cromulent's cross-runtime facts and every
  lesson's expectation, run in this browser after the page has
  painted. The same table the JVM and Jolt suites assert."
  (:require [cromulent.smoke :as smoke]
            [orrery.expect :as expect]
            [replicant.dom :as r]))

(defn check! []
  (let [el (js/document.getElementById "selftest")
        t0 (js/performance.now)
        cs (into (smoke/checks) (expect/checks))
        ms (js/Math.round (- (js/performance.now) t0))
        failed (remove :ok? cs)]
    (r/render el
              (if (empty? failed)
                [:span.ok {:id "selftest-ok"}
                 (str "✓ engine self-test: " (count cs) " facts hold in this browser, " ms " ms")]
                [:span.bad {:id "selftest-bad"}
                 (str "✗ engine self-test: " (count failed) " of " (count cs) " failed; first: " (:name (first failed))
                      " expected " (pr-str (:expected (first failed))) " got " (pr-str (:actual (first failed))))]))))
