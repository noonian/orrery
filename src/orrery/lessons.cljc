(ns orrery.lessons
  "The lessons, as data: a number, a title, prose in hiccup with the
  widgets [:notation term], [:native term] and [:step k label]; the inputs
  the learner may edit and their curated values; for a saturation the
  rules and runner options, for a script the function from values to
  steps; alternatives for \"try another\"; which optional panels the
  page shows. Prose is data so the JVM tests can walk it and the page
  can render it; there is no markdown. Lessons that need bendix carry
  :status :coming."
  (:require [bendix.core :as bx]
            [bendix.rules :as rules]
            [clojure.walk :as walk]
            [cromulent.core :as eg]
            [cromulent.rewrite :as rw]
            [orrery.notation :as notation]
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
  "Rule maps from [name lhs rhs] data; a rule that is already a map
  (bendix's normal-form rules) passes through."
  [rule-data]
  (mapv (fn [r] (if (map? r) r (let [[n lhs rhs] r] (rw/rule n lhs rhs)))) rule-data))

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
                 [(str (notation/term->str lhs) " = " (notation/term->str rhs) " asserted; rebuild pending") g1]
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
                 [(str "a copy, with " (notation/term->str lhs) " = " (notation/term->str rhs) " asserted; rebuild pending") g1]
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
   [[:p [:notation [:+ [:* 2 :x] :y]] " is a tree: an addition at the root, with " [:notation [:* 2 :x]] " and " [:notation :y] " beneath it, and 2 and " [:notation :x] " beneath the product. In the native format it is the tagged vector " [:native [:+ [:* 2 :x] :y]] ": a vector is a node, its first element the operator and the rest its children; a keyword is a variable and a number is a number."]
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
   [[:p [:notation [:* [:+ :x 1] [:+ :x 1]]] " has seven tree nodes, but the graph has four. The two " [:notation [:+ :x 1]] " subtrees are the same term, and an e-graph stores each distinct term once: adding a node looks it up first, in a table keyed by the node itself, and a node that is already there returns its class instead of making a new one. That table is the hashcons."]
    [:p "Hover either " [:notation [:+ :x 1]] " in the tree: both light up, and one row of the class list. Sharing is why a graph can hold every arrangement of a sum in far less space than a list of the arrangements would take. Lesson 7 is where the arithmetic of that stops being kind."]]})

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
   [[:p "Add " [:notation [:/ [:* :a 2] 2]] " and " [:notation [:/ [:<< :a 1] 2]] ": seven classes, one per distinct subterm. Now assert that " [:notation [:* :a 2]] " equals " [:notation [:<< :a 1]] ". Union merges their two classes into one and marks the graph dirty, " [:step 1 "rebuild pending"] ". Nothing else has moved: the two quotients are still two classes, though each now divides the same class by 2."]
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
   [[:p "A rule is a pattern and a replacement: " [:native '[:* ?x 2]] " → " [:native '[:<< ?x 1]] ", multiplying by two is shifting left by one. Running it has two phases. " [:b "Search"] ": match the pattern against the graph. Every class holding a node of the pattern's shape is a match, with " [:native '?x] " bound to the child's class; the matches are highlighted, " [:notation [:* :a 2]] " and " [:notation [:* :b 2]] "."]
    [:p [:b "Apply"] ": for each match, build the replacement under its bindings and union it with the matched class. " [:step 1 "Scrub to iteration 1"] ": two shift nodes appear, each in the class of the product it equals. The term is not rewritten; it is joined. Both forms stay, and the input reads " [:notation [:+ [:* :a 2] [:* :b 2]]] " or " [:notation [:+ [:<< :a 1] [:<< :b 1]]] ", whichever cost you ask under."]
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
   [[:p "After saturation the class of the input holds three forms: " [:notation [:+ :a :a]] ", " [:notation [:* :a 2]] ", " [:notation [:<< :a 1]] ". Which is the answer? The e-graph does not say. A cost function does: extraction walks the classes bottom-up, gives every node the cost of itself plus its cheapest children, and picks the cheapest node of each class."]
    [:p "Change the cost. Charge multiplications and shifts and the answer is " [:notation [:+ :a :a]] "; charge additions and shifts, " [:notation [:* :a 2]] "; charge additions and multiplications, " [:notation [:<< :a 1]] ". Same graph, three answers. This is the thesis behind bendix: simplification is equality saturation plus taste, and the taste is a cost function. AST size, the default in the other lessons, ties here, since the three forms are the same size, and a tie falls to a fixed but arbitrary order."]]})

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
   [[:p "A sum of five atoms, " [:notation (sum-of 5)] ", is one term of nine nodes: five atoms and four additions. The e-graph starts there, at " [:step 0 "nine classes and nine nodes"] "."]
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
   [[:p "The e-graph is a value. Asserting something in it does not change it; it makes a new one, and the old one is still there. Take " [:notation [:+ [:* :x :x] [:* 2 :x]]] " and ask: what if " [:notation :x] " were 2? Union the classes of " [:notation :x] " and 2 in a copy. " [:notation [:* :x :x]] " and " [:notation [:* 2 :x]] " are now the same node, a product of that class with itself, so " [:step 2 "congruence merges them on rebuild"] ", and the sum becomes a sum of a class with itself."]
    [:p "The original has not moved: it is step 0, side by side with the copy. In a mutable e-graph this needs an undo log or a deep copy; here a fork is a " [:native 'let] "."]]})

;; ---------------------------------------------------------------------------
;; bendix: the polynomial analysis, a rule over it, differentiation

(def s2 [:expt [:sin :x] 2])
(def c2 [:expt [:cos :x] 2])

(def materialize-step
  "The step every bendix lesson ends with: each class's normal form
  written into the graph as a term, so extraction can choose it."
  ["the normal forms, written in as terms" bx/materialize-all])

(def bendix-opts
  {:egraph (bx/egraph) :scheduler :simple :after [materialize-step]})

(def fix
  {:key :fix :n 8 :title "The fix" :needs :bendix :kind :embiggen
   :inputs [term-input rules-input]
   :values {:term (sum-of 5) :rules ac-rules}
   :opts (assoc bendix-opts :iter-limit 12 :node-limit 5000)
   :costs [:bendix :ast-size]
   :alternatives [{:label "another arrangement of the same sum" :values {:term [:+ :a4 [:+ :a3 [:+ :a2 [:+ :a1 :a0]]]] :rules ac-rules}}
                  {:label "no rules at all" :values {:term (sum-of 5) :rules []}}
                  {:label "six atoms" :values {:term (sum-of 6) :rules ac-rules}}]
   :panels #{:stats}
   :prose
   [[:p "Lesson 7's sum, " [:notation (sum-of 5)] ", the same two rules, and one addition: this e-graph carries the polynomial analysis. Every class computes what it is worth as a polynomial over the atoms, " [:notation [:+ :a0 :a1 :a2 :a3 :a4]] " for the whole sum and " [:notation [:+ :a0 :a1]] " for its first pair, and two classes with the same polynomial are merged as the node is added, before any rule sees it. The column on the right is that polynomial."]
    [:p "Run it. " [:step 1 "One iteration"] ", and the rules merge nothing: commutativity proposes " [:notation [:+ :a1 :a0]] " for the class of " [:notation [:+ :a0 :a1]] ", the analysis has already put it there, and a union of a class with itself is not a merge. Associativity adds three classes, the right-nested pairs, and nothing more. Twelve classes and nineteen nodes where lesson 7 needed thirty-one and a hundred and eighty-five, and the runner stops there: an iteration that merges nothing is saturation."]
    [:p "The arrangement of the sum has stopped mattering. Type any other, " [:native '[:+ :a4 [:+ :a3 [:+ :a2 [:+ :a1 :a0]]]]] " say: the class of the input has the same polynomial, and " [:step 2 "the last step"] " writes every polynomial into the graph as a term, so that the best term under bendix's cost is " [:notation [:+ :a0 :a1 :a2 :a3 :a4]] " whatever was typed. At the REPL, " [:native '(second (eg/add g [:+ :a4 [:+ :a3 [:+ :a2 [:+ :a1 :a0]]]]))] " returns the input's own class: the arrangement was already there."]
    [:p "This is the fix: commutativity, associativity, distributivity and cancellation are not rules but a decision procedure inside every class, a polynomial normal form, and the blowup never starts. Try six atoms, which needed six hundred nodes in lesson 7."]]})

(def polynomial-rule
  {:key :polynomial-rule :n 9 :title "A rule over the polynomial" :needs :bendix :kind :embiggen
   :inputs [term-input]
   :values {:term [:+ [:+ [:+ :a s2] c2] :b] :rules rules/trig}
   :opts bendix-opts
   :costs [:bendix :ast-size]
   :alternatives [{:label "in another arrangement" :values {:term [:+ [:+ :a c2] [:+ s2 :b]]}}
                  {:label "1 − cos²x" :values {:term [:- 1 c2]}}
                  {:label "with a cofactor" :values {:term [:+ [:* :y s2] [:* :y c2]]}}
                  {:label "sin(x + y) and cos(y + x)" :values {:term [:+ [:expt [:sin [:+ :x :y]] 2] [:expt [:cos [:+ :y :x]] 2]]}}]
   :panels #{:stats}
   :prose
   [[:p [:notation [:+ s2 c2]] " = 1 is not a ring identity. The ring sees " [:notation [:sin :x]] " and " [:notation [:cos :x]] " as two atoms it knows nothing about, and the class of " [:notation [:+ [:+ [:+ :a s2] c2] :b]] " is worth sin²x + cos²x + a + b, nothing less. A pattern rule would need the two squares side by side, and here they are not."]
    [:p "The pythagoras rule reads the polynomial instead of the nodes. For every class whose polynomial mentions a sine and a cosine of one argument, it reduces the polynomial modulo sin²x = 1 − cos²x, and modulo cos²x = 1 − sin²x, and where the result differs it proposes it as another form of the class: a + b + 1 here. The runner adds that form as a term and unions it in, the analysis keeps the smaller polynomial, and the classes that share one merge. " [:step 1 "Iteration 1"] " proposes a form for five classes, " [:step 2 "iteration 2"] " for two more, and the third finds nothing."]
    [:p "The arrangement never mattered, because the rule never looked at it. " [:step 4 "The last step"] " writes the polynomials in as terms, and the best term under bendix's cost is " [:notation [:+ :a :b 1]] ". Try 1 − cos²x: the sine of the graph does not exist yet, and the rule's proposal creates it."]]})

(def differentiation
  {:key :differentiation :n 11 :title "Differentiation is simplification" :needs :bendix :kind :embiggen
   :inputs [{:key :term :label "the function" :type :term}
            {:key :var :label "with respect to" :type :term}]
   :values {:term [:sin [:* 2 :x]] :var :x :rules rules/derivative}
   :build (fn [{:keys [term var]}] [:D term var])
   :opts bendix-opts
   :costs [:no-D :bendix :ast-size]
   :alternatives [{:label "x·sin x" :values {:term [:* :x [:sin :x]]}}
                  {:label "sin(x² + 1)" :values {:term [:sin [:+ [:expt :x 2] 1]]}}
                  {:label "(x + 1)³" :values {:term [:expt [:+ :x 1] 3]}}
                  {:label "sin(sin(sin x))" :values {:term [:sin [:sin [:sin :x]]]}}
                  {:label "x·|x|: no rule for abs" :values {:term [:* :x [:abs :x]]}}]
   :panels #{:stats}
   :prose
   [[:p "A derivative is a term like any other: " [:notation [:D [:sin [:* 2 :x]] :x]] " is a node with two children, and differentiating is saturating under rules. The ring part is not a rule at all: for a class worth a polynomial, its derivative is computed from the polynomial, so linearity, the product rule and the power rule are polynomial calculus. The chain rule is one pattern rule per operator: " [:native '[:D [:sin ?u] ?x]] " → " [:native '[:* [:cos ?u] [:D ?u ?x]]] "."]
    [:p [:step 1 "Iteration 1"] ": d-sin fires, and the class of the derivative gains " [:notation [:* [:cos [:* 2 :x]] [:D [:* 2 :x] :x]]] ". " [:step 2 "Iteration 2"] ": the ring differentiates 2·x to 2, so that product is worth 2·cos(2·x). The third iteration finds nothing, and " [:step 4 "the last step"] " writes the normal forms in."]
    [:p "Which term is the answer is the cost's decision. Bendix's default cost charges a derivative node no more than a sine, so for sin(sin(sin x)) it keeps the derivative unevaluated: the node is cheaper than the product of three cosines. The no-D cost counts what is still under a derivative before it counts size, so a derivative-free spelling wins whenever one exists, and when none does, x·|x| say, the D stays and the answer says so."]]})

(def all
  [tree
   sharing
   congruence
   rule
   saturation
   taste
   blowup
   fix
   polynomial-rule
   what-if
   differentiation])

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
     :embiggen (run/start ((or (:build lesson) :term) values) (rules-of (:rules values)) (merge (:opts lesson) opts))
     :script ((:script lesson) values))))
