(ns orrery.test-runner
  "Entry point for `jolt test` / `jolt -M:test` and `clojure -M:test`."
  (:require [clojure.test :as t]
            [orrery.notation-test]
            [orrery.parse-test]
            [orrery.diff-test]
            [orrery.eclass-test]
            [orrery.page-test]
            [orrery.input-test]
            [orrery.lessons-test]
            [orrery.normal-test]
            [orrery.exact-test]
            [orrery.generate-test]
            [orrery.score-test]
            [orrery.graph-test]
            [orrery.export-test]
            [orrery.workbench-test]
            [orrery.names-test]))

(defn -main [& _]
  (let [{:keys [fail error]} (t/run-tests 'orrery.notation-test
                                          'orrery.parse-test
                                          'orrery.diff-test
                                          'orrery.eclass-test
                                          'orrery.page-test
                                          'orrery.input-test
                                          'orrery.lessons-test
                                          'orrery.normal-test
                                          'orrery.exact-test
                                          'orrery.generate-test
                                          'orrery.score-test
                                          'orrery.graph-test
                                          'orrery.export-test
                                          'orrery.workbench-test
                                          'orrery.names-test)]
    (System/exit (if (pos? (+ fail error)) 1 0))))
