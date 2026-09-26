(ns orrery.test-runner
  "Entry point for `jolt test` / `jolt -M:test` and `clojure -M:test`."
  (:require [clojure.test :as t]
            [orrery.notation-test]
            [orrery.diff-test]
            [orrery.input-test]
            [orrery.lessons-test]
            [orrery.normal-test]
            [orrery.exact-test]
            [orrery.generate-test]
            [orrery.score-test]))

(defn -main [& _]
  (let [{:keys [fail error]} (t/run-tests 'orrery.notation-test
                                          'orrery.diff-test
                                          'orrery.input-test
                                          'orrery.lessons-test
                                          'orrery.normal-test
                                          'orrery.exact-test
                                          'orrery.generate-test
                                          'orrery.score-test)]
    (System/exit (if (pos? (+ fail error)) 1 0))))
