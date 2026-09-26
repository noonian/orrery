(ns orrery.smoke-cli
  "The third runtime as a command: cromulent's cross-runtime facts,
  compiled by shadow-cljs and run by node.

    npx shadow-cljs compile smoke && node target/smoke.js

  Prints one row per check and exits 1 on any failure."
  (:require [cromulent.smoke :as smoke]))

(defn -main [& _]
  (let [t0 (js/performance.now)
        cs (smoke/checks)
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
