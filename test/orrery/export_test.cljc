(ns orrery.export-test
  "The page's export: the e-graph on show as egraph-serialize JSON,
  every node costed under the cost in force, the input's class the
  root, and the polynomial of each class as class data on a bendix
  graph."
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is]]
            [orrery.derived :as derived]
            [orrery.lessons :as lessons]
            [orrery.run :as run]))

(defn- state-for [l]
  (let [run (run/run-all (lessons/make-run l))]
    (derived/clear-cache!)
    {:lesson (:key l)
     :input {:fields {} :values (:values l) :error nil :alternative nil :opts {}}
     :run run :run-id 1 :step (run/last-step run) :follow? true
     :cost (or (first (:costs l)) :ast-size)
     :repl {:input "" :history []}
     :ui {:print :notation :playing nil :selected nil :hover nil :drawing? false}}))

(deftest a-cromulent-graph
  (let [s (state-for lessons/sharing)
        json (derived/export-json s)]
    (is (str/starts-with? json "{\n  \"nodes\": {\n"))
    (is (= 4 (count (re-seq #"\"eclass\": " json))) "four nodes")
    (is (str/includes? json "\"root_eclasses\": [\"") "the input's class is the root")
    (is (str/includes? json "\"cost\": 7") "the product costs seven under AST size")
    (is (str/ends-with? json "\"class_data\": {}\n}") "no analysis, no class data")))

(deftest a-bendix-graph
  (let [s (state-for lessons/fix)
        json (derived/export-json s)]
    (is (str/includes? json "\"type\": \"polynomial\""))
    (is (str/includes? json "\"poly\": \"a0 + a1 + a2 + a3 + a4\"") "the normal form in the notation")
    (is (str/includes? json "\"cost\": 6") "under bendix's cost the five-way sum costs six, one and its five atoms")
    (is (not (str/includes? json "\"cost\": 9")) "no arrangement is as dear as lesson 7's, since the sum is flat"))
  (let [s (state-for lessons/polynomial-rule)
        json (derived/export-json s)]
    (is (str/includes? json "\"type\": \"atom\"") "sin x is its own atom")
    (is (str/includes? json "\"poly\": \"sin²x\"") "an opaque atom in a polynomial is spelled by its best term")
    (is (str/includes? json "\"poly\": \"a + b + 1\""))))
