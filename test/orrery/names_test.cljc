(ns orrery.names-test
  "What the REPL calls things: the table the prelude is made from and
  the page prints."
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [orrery.names :as names]
            ;; every namespace of the table, so that a row naming one
            ;; that does not load fails here
            [bendix.analysis]
            [bendix.core]
            [bendix.num]
            [bendix.poly]
            [bendix.rules]
            [bendix.term]
            [cromulent.check]
            [cromulent.core]
            [cromulent.export]
            [cromulent.extract]
            [cromulent.pattern]
            [cromulent.rewrite]
            [cromulent.term]
            [orrery.costs]
            [orrery.diff]
            [orrery.eclass]
            [orrery.input]
            [orrery.lessons]
            [orrery.notation]
            [orrery.run]
            [orrery.workbench]))

(deftest every-row-is-a-namespace-under-a-name-of-its-own
  (is (apply distinct? (map :alias names/namespaces)))
  (is (apply distinct? (map :ns names/namespaces)))
  (is (= (count names/namespaces) (count names/aliases)))
  (doseq [{:keys [alias ns of says]} names/namespaces]
    (is (symbol? alias) (str alias))
    (is (some? (find-ns ns)) (str ns " loads"))
    (is (contains? #{"cromulent" "bendix" "orrery"} of) (str alias))
    (is (str/starts-with? (str ns) of) (str alias))
    (is (and (string? says) (seq says)) (str alias))))

(deftest what-a-row-says-is-in-the-namespace-is
  (doseq [{:keys [alias ns says]} names/namespaces
          :let [publics (set (map str (keys (ns-publics ns))))
                ;; the names in a row are the words that are nothing but a name a namespace could hold
                words (re-seq #"[a-z>-][a-z0-9>*?!-]*" says)
                named (filter publics words)]]
    (is (seq named) (str alias ": the row names something the namespace holds"))))

(deftest the-names-bound
  (is (= '[g timeline sel state show! push! *1 doc] (mapv :name names/bound)))
  (is (every? (comp seq :says) names/bound))
  (is (empty? (filter names/aliases (map :name names/bound))) "a bound name is no namespace's"))

(deftest the-prelude
  (let [text (names/prelude)]
    (is (str/starts-with? text "(ns user (:require "))
    (doseq [{:keys [alias ns]} names/namespaces]
      (is (str/includes? text (str "[" ns " :as " alias "]")) (str alias)))
    (is (str/includes? text "[clojure.repl :refer [doc dir apropos find-doc]]"))
    (is (= (count (filter #{\(} text)) (count (filter #{\)} text))))
    (is (= (count (filter #{\[} text)) (count (filter #{\]} text))))))

(deftest the-namespaces-a-line-asks-for
  (is (= '#{eg} (names/qualifiers "(eg/add g [:+ :a :b])")))
  (is (= '#{rw eg lessons} (names/qualifiers "(rw/saturate (first (eg/add (eg/egraph) t)) (lessons/rules-of lessons/ac-rules) {})")))
  (is (= '#{wb} (names/qualifiers "(swap! state wb/scrub 0)")))
  (is (= '#{rules bx} (names/qualifiers "(bx/saturate t {:rules rules/trig :timeline? true})")))
  (is (= '#{input} (names/qualifiers "(input/read-term \"2·x + y\")")))
  (is (= #{} (names/qualifiers "(count timeline)")))
  (is (= #{} (names/qualifiers "[:+ 1/2 :x]")) "a ratio is no namespace")
  (testing "a name that is not in scope is found out"
    (is (not (every? names/aliases (names/qualifiers "(egg/add g :a)"))))))
