(ns orrery.smoke-cli
  "Runs the checks on the third runtime, as a command. The checks
  are cromulent's and bendix's cross-runtime facts, and the notation
  round trip over every lesson. shadow-cljs compiles them and node
  runs them.

    npx shadow-cljs compile smoke && node target/smoke.js

  Prints one row per check and exits 1 on any failure."
  (:require [bendix.smoke :as bendix-smoke]
            [cromulent.smoke :as smoke]
            [orrery.expect :as expect]))

(defn -main [& _]
  (let [t0 (js/performance.now)
        cs (-> (smoke/checks) (into (bendix-smoke/checks)) (into (expect/notation-checks)))
        ms (- (js/performance.now) t0)
        failed (remove :ok? cs)]
    (doseq [{:keys [name ok? expected actual]} cs]
      (println (if ok? "PASS " "FAIL ") name)
      (when-not ok?
        (println "      expected" (pr-str expected))
        (println "      actual  " (pr-str actual))))
    (println)
    (println (str (- (count cs) (count failed)) "/" (count cs) " passed in " (js/Math.round ms) " ms"))
    (js/process.exit (if (seq failed) 1 0))))
