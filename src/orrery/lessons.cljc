(ns orrery.lessons
  "The lessons, as data: a number, a title, prose in hiccup with the
  widgets [:lay term], [:native term] and [:step k label], a curated
  example, rules, runner options, alternatives for \"try another\",
  and the panels the page shows. Prose is data so the JVM tests can
  walk it and the page can render it; there is no markdown. Lessons
  that need bendix, or are not written yet, carry :status :coming."
  (:require [cromulent.rewrite :as rw]
            [orrery.run :as run]))

(defn sum-of
  "A left-nested sum of n atoms, a0 + a1 + …: the fixture of
  experiment 1 of ../design/ac-problem.md."
  [n]
  (reduce (fn [acc i] [:+ acc (keyword (str "a" i))]) :a0 (range 1 n)))

(def ac-rules
  "Commutativity and associativity of +, as [name lhs rhs] data."
  '[["comm" [:+ ?a ?b] [:+ ?b ?a]]
    ["assoc" [:+ [:+ ?a ?b] ?c] [:+ ?a [:+ ?b ?c]]]])

(def egg-rules
  "The rule set of egg's README."
  '[["commute-add" [:+ ?a ?b] [:+ ?b ?a]]
    ["commute-mul" [:* ?a ?b] [:* ?b ?a]]
    ["add-0" [:+ ?a 0] ?a]
    ["mul-0" [:* ?a 0] 0]
    ["mul-1" [:* ?a 1] ?a]])

(defn rules-of
  "Rule maps from [name lhs rhs] data."
  [rule-data]
  (mapv (fn [[n lhs rhs]] (rw/rule n lhs rhs)) rule-data))

(def blowup
  {:key :blowup :n 7 :title "The blowup" :needs :cromulent :kind :embiggen
   :term (sum-of 5)
   :rules ac-rules
   :opts {:scheduler :simple :iter-limit 12 :node-limit 5000}
   :costs [:ast-size]
   :alternatives [{:label "four atoms" :term (sum-of 4)}
                  {:label "six atoms" :term (sum-of 6)}
                  {:label "six atoms under a node limit of 500" :term (sum-of 6) :opts {:node-limit 500}}]
   :panels [:tiles :scrubber :classes :best]
   :prose
   [[:p "A sum of five atoms, " [:lay (sum-of 5)] ", is one term of nine nodes: five atoms and four additions. The e-graph starts there, at " [:step 0 "nine classes and nine nodes"] "."]
    [:p "Two rules say that the order and the grouping of a sum do not matter. Commutativity: " [:native '[:+ ?a ?b]] " → " [:native '[:+ ?b ?a]] ". Associativity: " [:native '[:+ [:+ ?a ?b] ?c]] " → " [:native '[:+ ?a [:+ ?b ?c]]] ". Under them every arrangement of the sum equals every other, and an e-graph is obliged to hold them all."]
    [:p "Watch the counters as the iterations run. Every non-empty subset of the five atoms becomes a class, 2⁵ − 1 = 31 of them, and every way of splitting a subset in two becomes a node, 3⁵ − 2⁶ + 1 = 180 of them, plus the five atoms themselves. Scrub back and forth: the graph grows by three to the n, and the rules stop only when there is nothing left to add. Six atoms climb past six hundred nodes; nine would need eighteen thousand."]
    [:p "This is the blowup. Commutativity and associativity are the two rules a computer algebra system cannot do without, and the two an e-graph cannot afford. Lesson 8 is the fix."]]})

(def all
  [{:key :tree :n 1 :title "A term is a tree" :needs :cromulent :status :coming}
   {:key :sharing :n 2 :title "Sharing" :needs :cromulent :status :coming}
   {:key :congruence :n 3 :title "Equality and congruence" :needs :cromulent :status :coming}
   {:key :rule :n 4 :title "A rule" :needs :cromulent :status :coming}
   {:key :saturation :n 5 :title "Saturation" :needs :cromulent :status :coming}
   {:key :taste :n 6 :title "Extraction is taste" :needs :cromulent :status :coming}
   blowup
   {:key :fix :n 8 :title "The fix" :needs :bendix :status :coming}
   {:key :polynomial-rule :n 9 :title "A rule over the polynomial" :needs :bendix :status :coming}
   {:key :what-if :n 10 :title "What if" :needs :cromulent :status :coming}
   {:key :differentiation :n 11 :title "Differentiation is simplification" :needs :bendix :status :coming}])

(defn by-key [k] (some (fn [l] (when (= k (:key l)) l)) all))

(defn live?
  "Does the lesson run today?"
  [lesson]
  (not= :coming (:status lesson)))

(defn make-run
  "The run for a lesson over term, with the lesson's rules and options
  under opts. A script lesson's :script is (fn [term] steps)."
  ([lesson] (make-run lesson (:term lesson) {}))
  ([lesson term opts]
   (case (:kind lesson)
     :embiggen (run/start term (rules-of (:rules lesson)) (merge (:opts lesson) opts))
     :script (run/script ((:script lesson) term)))))
