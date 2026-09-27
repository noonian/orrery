(ns orrery.snippets-test
  "The snippets for the REPL's buffer use only the names in scope."
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is]]
            [orrery.names :as names]
            [orrery.snippets :as snippets]))

(def ^:private every-snippet
  (concat (vals snippets/panels) (map :code snippets/library)))

(deftest every-snippet-uses-the-names-in-scope
  (doseq [code every-snippet]
    (is (every? names/aliases (names/qualifiers code)) code)))

(deftest every-snippet-is-one-form
  (doseq [code every-snippet
          :let [body (apply str (remove #(re-find #"^\s*;;" %) (str/split-lines code)))]]
    (is (= (count (filter #{\(} body)) (count (filter #{\)} body))) code)
    (is (= (count (filter #{\[} body)) (count (filter #{\]} body))) code)
    (is (= (count (filter #{\{} body)) (count (filter #{\}} body))) code)))

(deftest the-library-says-what-each-snippet-does
  (is (= (count snippets/library) (count (set (map :label snippets/library)))) "labels differ")
  (doseq [{:keys [label says code]} snippets/library]
    (is (and (seq label) (seq says) (seq code)) label)))
