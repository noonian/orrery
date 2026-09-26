(ns orrery.lessons
  "The lessons, as data: a number, a title, prose in hiccup with the
  widgets [:lay term], [:native term] and [:step k label]; the inputs
  the learner may edit and their curated values; for a saturation the
  rules and runner options, for a script the function from values to
  steps; alternatives for \"try another\"; which optional panels the
  page shows. Prose is data so the JVM tests can walk it and the page
  can render it; there is no markdown. Lessons that need bendix carry
  :status :coming."
  (:require [clojure.walk :as walk]
            [cromulent.core :as eg]
            [cromulent.rewrite :as rw]
            [orrery.lay :as lay]
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

(def term-input {:key :term :label "the term" :type :term})
(def rules-input {:key :rules :label "the rules, as [name pattern replacement]" :type :rules})

(defn- fill
  "wrapper with ?x replaced by t."
  [wrapper t]
  (walk/postwalk-replace {'?x t} wrapper))

;; ---------------------------------------------------------------------------
;; scripts

(defn- add-all [g terms]
  (reduce (fn [[g ids] t] (let [[g id] (eg/add g t)] [g (conj ids id)])) [g []] terms))

(defn tree-script
  "One step: the term, added."
  [{:keys [term]}]
  (let [[g id] (eg/add (eg/egraph) term)]
    (run/script [["the term" g]] id)))

(defn congruence-script
  "Two terms over each side of an equation; the equation asserted;
  the rebuild."
  [{:keys [lhs rhs wrapper]}]
  (let [[g [l r]] (add-all (eg/egraph) [(fill wrapper lhs) (fill wrapper rhs)])
        [g a] (eg/add g lhs)
        [g b] (eg/add g rhs)
        [g1 _] (eg/union g a b)
        g2 (eg/rebuild g1)]
    (run/script [["the two terms" g]
                 [(str (lay/term->str lhs) " = " (lay/term->str rhs) " asserted; rebuild pending") g1]
                 ["after rebuild" g2]]
                l)))

(defn what-if-script
  "A term; a copy with an equation asserted; the rebuild. The original
  is step 0 and does not move."
  [{:keys [term lhs rhs]}]
  (let [[g id] (eg/add (eg/egraph) term)
        [g' a] (eg/add g lhs)
        [g' b] (eg/add g' rhs)
        [g1 _] (eg/union g' a b)
        g2 (eg/rebuild g1)]
    (run/script [["the original" g]
                 [(str "a copy, with " (lay/term->str lhs) " = " (lay/term->str rhs) " asserted; rebuild pending") g1]
                 ["the copy, after rebuild" g2]]
                id)))

;; ---------------------------------------------------------------------------
;; the lessons

(def tree
  {:key :tree :n 1 :title "A term is a tree" :needs :cromulent :kind :script
   :script tree-script
   :inputs [term-input]
   :values {:term [:+ [:* 2 :x] :y]}
   :alternatives [{:label "(x + 1)·(y − 2)" :values {:term [:* [:+ :x 1] [:- :y 2]]}}
                  {:label "sin(2·x)" :values {:term [:sin [:* 2 :x]]}}
                  {:label "(x + 1)³" :values {:term [:expt [:+ :x 1] 3]}}]
   :panels #{:tree}
   :prose
   [[:p [:lay [:+ [:* 2 :x] :y]] " is a tree: an addition at the root, with " [:lay [:* 2 :x]] " and " [:lay :y] " beneath it, and 2 and " [:lay :x] " beneath the product. In the native format it is the tagged vector " [:native [:+ [:* 2 :x] :y]] ": a vector is a node, its first element the operator and the rest its children; a keyword is a variable and a number is a number."]
    [:p "An e-graph adds a term bottom-up. Each subterm becomes an e-node, and each e-node is put in an e-class, a set of nodes that are known to be equal. Nothing is equal to anything else yet, so classes and nodes are one to one: five of each. Hover a node of the tree to see its class in the list; the class's nodes are written with their children as class ids, " [:native [:+ 1 3]] " rather than the subterms themselves, because a class is a set of terms, not one term."]
    [:p "Type any term in the native format and add it. Every other lesson starts here."]]})

(def sharing
  {:key :sharing :n 2 :title "Sharing" :needs :cromulent :kind :script
   :script tree-script
   :inputs [term-input]
   :values {:term [:* [:+ :x 1] [:+ :x 1]]}
   :alternatives [{:label "a·b + a·b" :values {:term [:+ [:* :a :b] [:* :a :b]]}}
                  {:label "(x + 2·y)·(x + 2·y)" :values {:term [:* [:+ :x [:* 2 :y]] [:+ :x [:* 2 :y]]]}}
                  {:label "(a + a) + (a + a)" :values {:term [:+ [:+ :a :a] [:+ :a :a]]}}]
   :panels #{:tree}
   :prose
   [[:p [:lay [:* [:+ :x 1] [:+ :x 1]]] " has seven tree nodes, but the graph has four. The two " [:lay [:+ :x 1]] " subtrees are the same term, and an e-graph stores each distinct term once: adding a node looks it up first, in a table keyed by the node itself, and a node that is already there returns its class instead of making a new one. That table is the hashcons."]
    [:p "Hover either " [:lay [:+ :x 1]] " in the tree: both light up, and one row of the class list. Sharing is why a graph can hold every arrangement of a sum in far less space than a list of the arrangements would take. Lesson 7 is where the arithmetic of that stops being kind."]]})

(def congruence
  {:key :congruence :n 3 :title "Equality and congruence" :needs :cromulent :kind :script
   :script congruence-script
   :inputs [{:key :lhs :label "one side" :type :term}
            {:key :rhs :label "the other side" :type :term}
            {:key :wrapper :label "a term over each side, ?x standing for the side" :type :pattern}]
   :values {:lhs [:* :a 2] :rhs [:<< :a 1] :wrapper '[:/ ?x 2]}
   :alternatives [{:label "x + 0 = x, under a sine" :values {:lhs [:+ :x 0] :rhs :x :wrapper '[:sin ?x]}}
                  {:label "deeper: (?x + 1)·(?x + 1)" :values {:lhs [:* :a 2] :rhs [:<< :a 1] :wrapper '[:* [:+ ?x 1] [:+ ?x 1]]}}]
   :panels #{}
   :prose
   [[:p "Add " [:lay [:/ [:* :a 2] 2]] " and " [:lay [:/ [:<< :a 1] 2]] ": seven classes, one per distinct subterm. Now assert that " [:lay [:* :a 2]] " equals " [:lay [:<< :a 1]] ". Union merges their two classes into one and marks the graph dirty, " [:step 1 "rebuild pending"] ". Nothing else has moved: the two quotients are still two classes, though each now divides the same class by 2."]
    [:p "Rebuild restores the invariant that equal children make equal parents. It re-keys every node whose child moved, finds the two quotient nodes now identical, and merges their classes too, " [:step 2 "without being told"] ". That is congruence, and it is the whole trick of an e-graph: say one equality, and every consequence that follows from the shape of the terms comes for free. This is the example from egg's README."]]})

(def rule
  {:key :rule :n 4 :title "A rule" :needs :cromulent :kind :embiggen
   :inputs [term-input rules-input]
   :values {:term [:+ [:* :a 2] [:* :b 2]]
            :rules '[["mul-2-to-shift" [:* ?x 2] [:<< ?x 1]]]}
   :opts {:scheduler :simple :iter-limit 1}
   :costs [:ast-size :prefer-shift]
   :alternatives [{:label "egg's rules on 0 + 1·a, one iteration" :values {:term [:+ 0 [:* 1 :a]] :rules egg-rules}}
                  {:label "commutativity on a + b" :values {:term [:+ :a :b] :rules '[["comm" [:+ ?a ?b] [:+ ?b ?a]]]}}]
   :panels #{:matches}
   :prose
   [[:p "A rule is a pattern and a replacement: " [:native '[:* ?x 2]] " → " [:native '[:<< ?x 1]] ", multiplying by two is shifting left by one. Running it has two phases. " [:b "Search"] ": match the pattern against the graph. Every class holding a node of the pattern's shape is a match, with " [:native '?x] " bound to the child's class; the matches are highlighted, " [:lay [:* :a 2]] " and " [:lay [:* :b 2]] "."]
    [:p [:b "Apply"] ": for each match, build the replacement under its bindings and union it with the matched class. " [:step 1 "Scrub to iteration 1"] ": two shift nodes appear, each in the class of the product it equals. The term is not rewritten; it is joined. Both forms stay, and the input reads " [:lay [:+ [:* :a 2] [:* :b 2]]] " or " [:lay [:+ [:<< :a 1] [:<< :b 1]]] ", whichever cost you ask under."]
    [:p "Edit the rules: a vector of " [:native '["name" [:* ?x 2] [:<< ?x 1]]] " triples. A variable on the right must appear on the left."]]})

(def saturation
  {:key :saturation :n 5 :title "Saturation" :needs :cromulent :kind :embiggen
   :inputs [term-input rules-input]
   :values {:term (sum-of 4) :rules ac-rules}
   :opts {:scheduler :backoff :match-limit 4 :ban-length 2 :iter-limit 30}
   :costs [:ast-size]
   :alternatives [{:label "the same, every match every time" :values {:term (sum-of 4) :rules ac-rules} :opts {:scheduler :simple}}
                  {:label "egg's rules on 0 + 1·a" :values {:term [:+ 0 [:* 1 :a]] :rules egg-rules} :opts {:scheduler :simple}}
                  {:label "five atoms under a node limit of 100" :values {:term (sum-of 5) :rules ac-rules} :opts {:scheduler :simple :node-limit 100}}]
   :panels #{:stats}
   :prose
   [[:p "Saturation runs the rules again and again until no application changes the graph. Each iteration searches every rule against the same snapshot, applies every match, then rebuilds. The stats table shows each iteration: how many matches each rule found, how many applications merged something, and the time each phase took. An iteration that applied nothing is the sign to stop."]
    [:p "Under the " [:b "backoff"] " scheduler a rule whose matches exceed its budget is banned for a few iterations, and both its budget and its ban double each time it happens; saturation is declared only when an iteration applied nothing " [:i "and"] " no rule is banned, otherwise the bans are lifted and the run goes on. That is why the run ends with a quiet iteration, and why the stop reason says saturated rather than a limit. Try the alternative that applies every match every time: fewer iterations, the same graph."]
    [:p "The other stop reasons are limits: on iterations, on nodes, on time. The last alternative hits the node limit on purpose."]]})

(def taste
  {:key :taste :n 6 :title "Extraction is taste" :needs :cromulent :kind :embiggen
   :inputs [term-input rules-input]
   :values {:term [:+ :a :a]
            :rules '[["double" [:+ ?x ?x] [:* ?x 2]]
                     ["shift" [:* ?x 2] [:<< ?x 1]]]}
   :opts {:scheduler :simple}
   :costs [:prefer-add :prefer-mul :prefer-shift :ast-size]
   :alternatives [{:label "(b·2) + (b·2)" :values {:term [:+ [:* :b 2] [:* :b 2]]
                                                   :rules '[["double" [:+ ?x ?x] [:* ?x 2]] ["undouble" [:* ?x 2] [:+ ?x ?x]] ["shift" [:* ?x 2] [:<< ?x 1]]]}}
                  {:label "with commutativity" :values {:term [:+ :a :a]
                                                        :rules '[["double" [:+ ?x ?x] [:* ?x 2]] ["shift" [:* ?x 2] [:<< ?x 1]] ["comm-mul" [:* ?a ?b] [:* ?b ?a]]]}}]
   :panels #{}
   :prose
   [[:p "After saturation the class of the input holds three forms: " [:lay [:+ :a :a]] ", " [:lay [:* :a 2]] ", " [:lay [:<< :a 1]] ". Which is the answer? The e-graph does not say. A cost function does: extraction walks the classes bottom-up, gives every node the cost of itself plus its cheapest children, and picks the cheapest node of each class."]
    [:p "Change the cost. Charge multiplications and shifts and the answer is " [:lay [:+ :a :a]] "; charge additions and shifts, " [:lay [:* :a 2]] "; charge additions and multiplications, " [:lay [:<< :a 1]] ". Same graph, three answers. This is the thesis behind bendix: simplification is equality saturation plus taste, and the taste is a cost function. AST size, the default in the other lessons, ties here, since the three forms are the same size, and a tie falls to a fixed but arbitrary order."]]})

(def blowup
  {:key :blowup :n 7 :title "The blowup" :needs :cromulent :kind :embiggen
   :inputs [term-input rules-input]
   :values {:term (sum-of 5) :rules ac-rules}
   :opts {:scheduler :simple :iter-limit 12 :node-limit 5000}
   :costs [:ast-size]
   :alternatives [{:label "four atoms" :values {:term (sum-of 4) :rules ac-rules}}
                  {:label "six atoms" :values {:term (sum-of 6) :rules ac-rules}}
                  {:label "six atoms under a node limit of 500" :values {:term (sum-of 6) :rules ac-rules} :opts {:node-limit 500}}]
   :panels #{:stats}
   :prose
   [[:p "A sum of five atoms, " [:lay (sum-of 5)] ", is one term of nine nodes: five atoms and four additions. The e-graph starts there, at " [:step 0 "nine classes and nine nodes"] "."]
    [:p "Two rules say that the order and the grouping of a sum do not matter. Commutativity: " [:native '[:+ ?a ?b]] " → " [:native '[:+ ?b ?a]] ". Associativity: " [:native '[:+ [:+ ?a ?b] ?c]] " → " [:native '[:+ ?a [:+ ?b ?c]]] ". Under them every arrangement of the sum equals every other, and an e-graph is obliged to hold them all."]
    [:p "Watch the counters as the iterations run. Every non-empty subset of the five atoms becomes a class, 2⁵ − 1 = 31 of them, and every way of splitting a subset in two becomes a node, 3⁵ − 2⁶ + 1 = 180 of them, plus the five atoms themselves. Scrub back and forth: the graph grows by three to the n, and the rules stop only when there is nothing left to add. Six atoms climb past six hundred nodes; nine would need eighteen thousand."]
    [:p "This is the blowup. Commutativity and associativity are the two rules a computer algebra system cannot do without, and the two an e-graph cannot afford. Lesson 8 is the fix."]]})

(def what-if
  {:key :what-if :n 10 :title "What if" :needs :cromulent :kind :script
   :script what-if-script
   :inputs [term-input
            {:key :lhs :label "what if this…" :type :term}
            {:key :rhs :label "…equalled this" :type :term}]
   :values {:term [:+ [:* :x :x] [:* 2 :x]] :lhs :x :rhs 2}
   :alternatives [{:label "x·y + y·x, what if y = x" :values {:term [:+ [:* :x :y] [:* :y :x]] :lhs :y :rhs :x}}
                  {:label "sin x + sin y, what if x = y" :values {:term [:+ [:sin :x] [:sin :y]] :lhs :x :rhs :y}}]
   :panels #{:fork}
   :prose
   [[:p "The e-graph is a value. Asserting something in it does not change it; it makes a new one, and the old one is still there. Take " [:lay [:+ [:* :x :x] [:* 2 :x]]] " and ask: what if " [:lay :x] " were 2? Union the classes of " [:lay :x] " and 2 in a copy. " [:lay [:* :x :x]] " and " [:lay [:* 2 :x]] " are now the same node, a product of that class with itself, so " [:step 2 "congruence merges them on rebuild"] ", and the sum becomes a sum of a class with itself."]
    [:p "The original has not moved: it is step 0, side by side with the copy. In a mutable e-graph this needs an undo log or a deep copy; here a fork is a " [:native 'let] "."]]})

(def all
  [tree
   sharing
   congruence
   rule
   saturation
   taste
   blowup
   {:key :fix :n 8 :title "The fix" :needs :bendix :status :coming}
   {:key :polynomial-rule :n 9 :title "A rule over the polynomial" :needs :bendix :status :coming}
   what-if
   {:key :differentiation :n 11 :title "Differentiation is simplification" :needs :bendix :status :coming}])

(defn by-key [k] (some (fn [l] (when (= k (:key l)) l)) all))

(defn live?
  "Does the lesson run today?"
  [lesson]
  (not= :coming (:status lesson)))

(defn make-run
  "The run for a lesson over its input values, with the lesson's
  options under opts."
  ([lesson] (make-run lesson (:values lesson) {}))
  ([lesson values opts]
   (case (:kind lesson)
     :embiggen (run/start (:term values) (rules-of (:rules values)) (merge (:opts lesson) opts))
     :script ((:script lesson) values))))
