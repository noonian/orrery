(ns orrery.lessons
  "The lessons, as data: a number, a title, prose in hiccup with the
  widgets of `widget-kinds` as its figures; the inputs
  the learner may edit and their curated values; for a saturation the
  rules and runner options, for a script the function from values to
  steps; alternatives for \"try another\"; which optional panels the
  page shows. Prose is data so the JVM tests can walk it and the page
  can render it; there is no markdown. For \"surprise me\", the draw
  that makes a candidate and the features the lesson wants high
  (orrery.generate, orrery.score). Each names its operation, what
  its workbench runs (`operations`), and its inputs are that
  operation's arguments. Before the lessons come two pages shaped
  like them and without a number: the basics, from nothing, where
  the page opens, and the introduction, what an e-graph is and why.
  Before those, `about`: what the site is, said once, over the page
  it opens on, and in a line under every page (`colophon`)."
  (:require [bendix.core :as bx]
            [bendix.rules :as rules]
            [clojure.string :as str]
            [clojure.walk :as walk]
            [cromulent.core :as eg]
            [cromulent.rewrite :as rw]
            [orrery.generate :as generate]
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

(def basics-rules
  "Two facts anyone can check with a number in hand."
  '[["add-0" [:+ ?a 0] ?a]
    ["mul-1" [:* ?a 1] ?a]])

(def intro-rules
  "The four rules of the example egg's paper opens with: one that
  spoils (a·2)/2 for a rewriter, and three that take it to a."
  '[["mul-2-to-shift" [:* ?x 2] [:<< ?x 1]]
    ["regroup" [:/ [:* ?x ?y] ?z] [:* ?x [:/ ?y ?z]]]
    ["cancel" [:/ ?x ?x] 1]
    ["mul-1" [:* ?x 1] ?x]])

(defn rules-of
  "Rule maps from [name lhs rhs] data; a rule that is already a map
  (bendix's normal-form rules) passes through."
  [rule-data]
  (mapv (fn [r] (if (map? r) r (let [[n lhs rhs] r] (rw/rule n lhs rhs)))) rule-data))

(def term-input
  {:key :term :arg "term" :label "the term" :says "the expression to start from" :type :term})

(def rules-input
  {:key :rules :arg "rules" :label "the rules" :type :rules
   :says "what may be written as what, as [name pattern replacement]"
   :notation-says "what may be written as what, one per line as name: pattern → replacement"})

(defn input-label
  "What an input goes by in a sentence, a problem with it, say."
  [input]
  (:label input))

(defn input-says
  "What an input is, in the print mode in force: the words beside its
  argument's name over the field."
  [{:keys [says notation-says]} mode]
  (if (and (= :notation mode) notation-says) notation-says says))

(def operations
  "What a workbench runs, by key: the algorithm in a word; the
  function that is it, as the REPL under the page names it; what it
  does, in a sentence; and the call, in lines narrow enough for the
  panel, its arguments named as the fields over them are, g being
  the e-graph and opts standing for the runner options in force
  (`call`). The page steps the same functions one iteration or one
  call at a time, so that there is something to scrub."
  {:add
   {:name "add" :fn "eg/add"
    :says "Put the term into an empty e-graph, subterms first, each distinct subterm once."
    :call ["(eg/add (eg/egraph) term)"]}
   :union
   {:name "union, then rebuild" :fn "eg/union · eg/rebuild"
    :says "Add the wrapper over each side, say that the two sides are equal, and let rebuild find what follows from it."
    :call ["(eg/add g wrapper)  ; for each side"
           "(eg/union g lhs rhs)"
           "(eg/rebuild g)"]}
   :what-if
   {:name "union, in a copy" :fn "eg/union · eg/rebuild"
    :says "Add the term; then, in a copy of the e-graph, say that lhs equals rhs, and rebuild. The original is a value and does not change."
    :call ["(eg/add (eg/egraph) term)"
           "(eg/union g lhs rhs)"
           "(eg/rebuild g)"]}
   :saturate
   {:name "saturate" :fn "rw/saturate"
    :says "Add the term to an empty e-graph, g, and run every rule over it, again and again, until none adds anything or a limit stops the run."
    :call ["(eg/add (eg/egraph) term)"
           "(rw/saturate g rules"
           "  {opts})"]}
   :simplify
   {:name "simplify" :fn "bx/simplify"
    :says "Saturate the term under the rules in an e-graph whose classes each carry a polynomial, write every polynomial in as a term, and extract the cheapest term under the cost."
    :call ["(bx/simplify term"
           "  {:rules rules"
           "   :cost cost"
           "   opts})"]}
   :differentiate
   {:name "differentiate" :fn "bx/differentiate"
    :says "Simplify the derivative of term by x, the term D(term, x), under the derivative rules and the cost that charges what is still under a D."
    :call ["(bx/differentiate term x"
           "  {opts})"]}})

(defn operation
  "What a lesson's workbench runs: the entry of `operations` it names,
  under whatever of it the lesson says for itself."
  [lesson]
  (merge (get operations (:operation lesson)) (:operation-says lesson)))

(def ^:private shown-opts
  "The runner options a call shows, in this order."
  [:scheduler :match-limit :ban-length :iter-limit :node-limit])

(defn call
  "The lines of a lesson's call under the runner options in force,
  the lesson's under opts: the line that says opts becomes a line
  per option, aligned under the first."
  [lesson opts]
  (let [opts (merge (:opts lesson) opts)
        entries (vec (for [k shown-opts :when (contains? opts k)]
                       (str k " " (pr-str (get opts k)))))
        n (count entries)]
    (vec (mapcat (fn [line]
                   (if-let [[_ before after] (re-matches #"^(.*)opts(.*)$" line)]
                     (if (zero? n)
                       [(str before after)]
                       (map-indexed (fn [i entry]
                                      (str (if (zero? i) before (apply str (repeat (count before) " ")))
                                           entry
                                           (when (= i (dec n)) after)))
                                    entries))
                     [line]))
                 (:call (operation lesson))))))

(def widget-kinds
  "The vectors in prose that the page resolves rather than renders as
  hiccup: [:notation t] and [:native t] print a term; [:step k label]
  scrubs to step k; [:select t label] opens the class of t, and
  [:select t label k] scrubs to k first; [:cost key label] sets the
  cost; [:alternative label text] chooses one of the lesson's
  alternatives; [:print mode label] switches the print mode; [:lesson
  key label] goes to another lesson; [:cite key …] cites works of
  `reading` on the idea it follows, as short author-year links. The
  prose is written in the explorable voice: each paragraph says what
  is on the page and hands the reader one of these to pull, and
  cites rather than recounts."
  #{:notation :native :step :select :cost :alternative :print :lesson :cite})

(def reading
  "The works the prose cites, by key: who, what, where, the year, the
  short author-year the citation shows, and a link when the paper has
  one (a DOI where it exists; verified 2026-09-26)."
  {:nelson-1981 {:who "Greg Nelson" :what "Techniques for Program Verification" :where "Xerox PARC report CSL-81-10" :year 1981
                 :short "Nelson 1981" :url "https://people.eecs.berkeley.edu/~necula/Papers/nelson-thesis.pdf"}
   :nelson-oppen-1980 {:who "Greg Nelson and Derek Oppen" :what "Fast Decision Procedures Based on Congruence Closure" :where "Journal of the ACM" :year 1980
                       :short "Nelson & Oppen 1980" :url "https://doi.org/10.1145/322186.322198"}
   :downey-sethi-tarjan-1980 {:who "Peter Downey, Ravi Sethi and Robert Tarjan" :what "Variations on the Common Subexpression Problem" :where "Journal of the ACM" :year 1980
                              :short "Downey, Sethi & Tarjan 1980" :url "https://doi.org/10.1145/322217.322228"}
   :nelson-oppen-1979 {:who "Greg Nelson and Derek Oppen" :what "Simplification by Cooperating Decision Procedures" :where "ACM Transactions on Programming Languages and Systems" :year 1979
                       :short "Nelson & Oppen 1979" :url "https://doi.org/10.1145/357073.357079"}
   :ershov-1958 {:who "Andrei Ershov" :what "On Programming of Arithmetic Operations" :where "Communications of the ACM" :year 1958
                 :short "Ershov 1958" :url "https://doi.org/10.1145/368892.368907"}
   :goto-1974 {:who "Eiichi Goto" :what "Monocopy and Associative Algorithms in an Extended Lisp" :where "University of Tokyo report TR 74-03" :year 1974
               :short "Goto 1974"}
   :detlefs-nelson-saxe-2005 {:who "David Detlefs, Greg Nelson and James Saxe" :what "Simplify: A Theorem Prover for Program Checking" :where "Journal of the ACM" :year 2005
                              :short "Detlefs, Nelson & Saxe 2005" :url "https://doi.org/10.1145/1066100.1066102"}
   :de-moura-bjorner-2007 {:who "Leonardo de Moura and Nikolaj Bjørner" :what "Efficient E-Matching for SMT Solvers" :where "CADE" :year 2007
                           :short "de Moura & Bjørner 2007" :url "https://doi.org/10.1007/978-3-540-73595-3_13"}
   :tate-2009 {:who "Ross Tate, Michael Stepp, Zachary Tatlock and Sorin Lerner" :what "Equality Saturation: A New Approach to Optimization" :where "POPL" :year 2009
               :short "Tate et al. 2009" :url "https://doi.org/10.1145/1480881.1480915"}
   :willsey-2021 {:who "Max Willsey, Chandrakana Nandi, Yisu Remy Wang, Oliver Flatt, Zachary Tatlock and Pavel Panchekha" :what "egg: Fast and Extensible Equality Saturation" :where "POPL" :year 2021
                  :short "Willsey et al. 2021" :url "https://doi.org/10.1145/3434304"}
   :panchekha-2015 {:who "Pavel Panchekha, Alex Sanchez-Stern, James Wilcox and Zachary Tatlock" :what "Automatically Improving Accuracy for Floating Point Expressions" :where "PLDI" :year 2015
                    :short "Panchekha et al. 2015" :url "https://doi.org/10.1145/2737924.2737959"}
   :bachmair-2000 {:who "Leo Bachmair, Ashish Tiwari and Laurent Vigneron" :what "Congruence Closure Modulo Associativity and Commutativity" :where "FroCoS" :year 2000
                   :short "Bachmair, Tiwari & Vigneron 2000" :url "https://doi.org/10.1007/10720084_16"}
   :zucker-2025 {:who "Philip Zucker" :what "Omelets Need Onions: E-graphs Modulo Theories via Bottom-up E-matching" :where "arXiv 2504.14340" :year 2025
                 :short "Zucker 2025" :url "https://arxiv.org/abs/2504.14340"}
   :driscoll-1989 {:who "James Driscoll, Neil Sarnak, Daniel Sleator and Robert Tarjan" :what "Making Data Structures Persistent" :where "Journal of Computer and System Sciences" :year 1989
                   :short "Driscoll et al. 1989" :url "https://doi.org/10.1016/0022-0000(89)90034-2"}
   :bagwell-2001 {:who "Phil Bagwell" :what "Ideal Hash Trees" :where "EPFL" :year 2001
                  :short "Bagwell 2001" :url "https://lampwww.epfl.ch/papers/idealhashtrees.pdf"}
   :hickey-2020 {:who "Rich Hickey" :what "A History of Clojure" :where "PACMPL, HOPL IV" :year 2020
                 :short "Hickey 2020" :url "https://doi.org/10.1145/3386321"}})

(defn widgets
  "Every widget in a lesson's prose, in reading order."
  [lesson]
  (letfn [(walk [x]
            (cond (and (vector? x) (contains? widget-kinds (first x))) [x]
                  (vector? x) (mapcat walk (rest x))
                  :else nil))]
    (vec (mapcat walk (:prose lesson)))))

(defn credits
  "The keys of `reading` a lesson's prose cites, in order of first
  citation: the lesson's reading line."
  [lesson]
  (vec (distinct (mapcat rest (filter #(= :cite (first %)) (widgets lesson))))))

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
;; what the site is

(def about
  "What the site is, in prose with nothing to pull: it stands over
  the page the site opens on, before any paragraph that links into
  the running widgets."
  [[:p [:b "orrery"] " is an interactive tool for exploring e-graphs and learning how they work, its author's learning included. It embeds two libraries and runs them live in this page: " [:b "cromulent"] ", an e-graph that is an immutable, persistent value, and " [:b "bendix"] ", a nascent computer algebra system built on it. The panels are widgets over what those libraries really compute, there to be scrubbed, opened and changed until the behaviour makes sense."]
   [:p "It is largely written using LLMs."]])

(def colophon
  "The same in a line, under every page, with the way back to `about`."
  [:p "orrery is an interactive tool for exploring e-graphs, over cromulent and bendix run live in the page, and is largely written using LLMs. " [:lesson :basics "What this is"] "."])

;; ---------------------------------------------------------------------------
;; the two pages before the lessons

(def basics
  {:key :basics :nav "Start here" :title "Many ways to write one thing" :needs :cromulent :kind :embiggen
   :operation :saturate
   :inputs [term-input rules-input]
   :values {:term [:* [:+ :x 0] 1] :rules basics-rules}
   :opts {:scheduler :simple}
   :costs [:ast-size]
   :alternatives [{:label "(x + 0)·1" :values {:term [:* [:+ :x 0] 1]}}
                  {:label "nothing to do: x·y" :values {:term [:* :x :y]}}
                  {:label "two letters: (x + 0)·(y·1)" :values {:term [:* [:+ :x 0] [:* :y 1]]}}
                  {:label "twice as long: ((x + 0)·1 + 0)·1" :values {:term [:* [:+ [:* [:+ :x 0] 1] 0] 1]}}]
   :panels #{:graph}
   :prose
   [[:p "Six, half a dozen and 2·3 are three ways to write one number. " [:notation [:* [:+ :x 0] 1]] " is a long way to write " [:notation :x] ", whatever number " [:notation :x] " is: adding nothing changes nothing, and one times anything is that thing. (The dot is times.)"]
    [:p "A fact like that is a rule: a shape, and another shape that always means the same. " [:notation '[:+ ?a 0]] " → " [:notation '?a] " says that anything plus zero is the thing itself, " [:notation '?a] " standing for whatever is there; " [:notation '[:* ?a 1]] " → " [:notation '?a] " says the same of times one. Simplifying is using rules to find a shorter way to write something."]
    [:p "The usual way is to cross out and write over, and the longer form is gone. An e-graph" [:cite :nelson-1981] " crosses nothing out. It collects every way of writing a thing that the rules turn up, and keeps the ways that mean the same together, in a class: a box in the picture."]
    [:p [:step 0 "At the start"] " the five parts of the expression sit in five boxes, each written with pointers: # and a number is whatever that box holds. " [:step 1 "Run the rules once"] " and three boxes become one. " [:select [:* [:+ :x 0] 1] "Open it" 1] ": " [:notation :x] ", " [:notation [:+ :x 0]] " and " [:notation [:* [:+ :x 0] 1]] " are one thing written three ways, and more, since if " [:notation [:+ :x 0]] " is " [:notation :x] " then so is " [:notation [:+ [:+ :x 0] 0]] ", without end."]
    [:p "Running the rules until they turn up nothing new is saturating" [:cite :tate-2009] "; here " [:step 2 "the second pass"] " finds nothing. Then comes the choice: asked for the best way to write what it was given, the e-graph looks in that box and takes the shortest, " [:notation :x] ". Collect every way, keep the ways that mean the same together, choose one at the end: that is the whole idea."]
    [:p [:alternative "nothing to do: x·y" "Give it x·y"] ", where no rule applies, and it comes back as it went in; " [:alternative "two letters: (x + 0)·(y·1)" "give it two letters"] "; " [:alternative "(x + 0)·1" "put (x + 0)·1 back"] ", or type an expression of your own and run it. " [:lesson :intro "The next page"] " shows what collecting buys that crossing out cannot."]]})

(def intro
  {:key :intro :title "What is an e-graph?" :needs :cromulent :kind :embiggen
   :operation :saturate
   :inputs [term-input rules-input]
   :values {:term [:/ [:* :a 2] 2] :rules intro-rules}
   :opts {:scheduler :simple}
   :costs [:ast-size]
   :alternatives [{:label "(a·2)/2" :values {:term [:/ [:* :a 2] 2]}}
                  {:label "what shifting first leaves: (a << 1)/2" :values {:term [:/ [:<< :a 1] 2]}}
                  {:label "other numbers: (x·3)/3" :values {:term [:/ [:* :x 3] 3]}}
                  {:label "twice over: ((a·2)/2·2)/2" :values {:term [:/ [:* [:/ [:* :a 2] 2] 2] 2]}}]
   :panels #{:graph}
   :prose
   [[:p "A simplifier rewrites: it finds a pattern in a term and puts something better in its place. In " [:notation [:/ [:* :a 2] 2]] " the product is a shift, " [:notation [:<< :a 1]] ", which a machine prefers, and the twos cancel, which leaves " [:notation :a] ". A rewriter that shifts first holds " [:notation [:/ [:<< :a 1] 2]] " and can no longer cancel: " [:alternative "what shifting first leaves: (a << 1)/2" "start from there"] " and the same rules find nothing to do. No order of rules is right for every term: the phase-ordering problem" [:cite :tate-2009] "."]
    [:p "An e-graph" [:cite :nelson-1981] " does not choose. " [:alternative "(a·2)/2" "Give it (a·2)/2"] ": each distinct subterm is a node, stored once, and nodes known to be equal share a class, a box in the picture. " [:step 0 "At the input"] " that is four classes of one node each. A rule does not replace what it matches, it adds to its class: " [:step 1 "after one iteration"] " the " [:select [:* :a 2] "class of a·2" 1] " holds the shift beside the product, and the product is still there for the next rule."]
    [:p "Run the rules until none has anything to add: equality saturation" [:cite :tate-2009 :willsey-2021] ". " [:step 2 "The twos cancel"] "; " [:step 3 "a times one is a"] ", and the input's class and the class of " [:notation :a] " become one; " [:step 4 "a fourth iteration"] " finds nothing new. Nothing was thrown away, so the order the rules fired in never mattered. Choosing comes last: a cost function ranks the terms a class stands for, and extraction reads off the cheapest, here " [:notation :a] "."]
    [:p "That is why e-graphs are interesting: rewriting without regret, the choice of an answer kept apart from the search for one, and many terms in few nodes. " [:select [:/ [:* :a 2] 2] "The input's class" 4] " stands for infinitely many, since " [:notation :a] " is " [:notation [:/ [:* :a 2] 2]] ", whose " [:notation :a] " is " [:notation [:/ [:* :a 2] 2]] ". E-graphs are at work in theorem provers" [:cite :detlefs-nelson-saxe-2005 :de-moura-bjorner-2007] ", optimizing compilers" [:cite :tate-2009] " and tools that make floating-point arithmetic more accurate" [:cite :panchekha-2015] "."]
    [:p "The lessons take this page apart: " [:lesson :tree "a term as a tree"] ", " [:lesson :sharing "sharing"] ", " [:lesson :congruence "equality"] ", " [:lesson :rule "a rule"] ", " [:lesson :saturation "saturation"] " and " [:lesson :taste "the choice of an answer"] "; then " [:lesson :blowup "what holding everything costs"] ", " [:lesson :fix "the fix"] " and " [:lesson :polynomial-rule "a rule over it"] "; " [:lesson :what-if "a what-if"] "; and " [:lesson :differentiation "differentiation"] ". Everything below is the engine running, not a drawing of it: scrub the steps, click a box, change the term or the rules and run them."]]})

;; ---------------------------------------------------------------------------
;; the lessons

(def tree
  {:key :tree :n 1 :title "A term is a tree" :needs :cromulent :kind :script
   :operation :add
   :script tree-script
   :inputs [term-input]
   :values {:term [:+ [:* 2 :x] :y]}
   :alternatives [{:label "(x + 1)·(y − 2)" :values {:term [:* [:+ :x 1] [:- :y 2]]}}
                  {:label "sin(2·x)" :values {:term [:sin [:* 2 :x]]}}
                  {:label "(x + 1)³" :values {:term [:expt [:+ :x 1] 3]}}]
   :panels #{:tree}
   :surprise {:draw generate/a-term :wants {:size 1}}
   :prose
   [[:p [:notation [:+ [:* 2 :x] :y]] " is a tree: an addition at the root, a product and " [:notation :y] " beneath it, 2 and " [:notation :x] " beneath the product. The tree panel draws it. In the native spelling it is " [:native [:+ [:* 2 :x] :y]] ": a vector is a node, its first element the operator and the rest its children; a keyword is a variable and a number is a number. " [:print :native "Switch the page to native"] " and every panel prints it that way; " [:print :notation "switch back"] " and it is mathematics again."]
    [:p "An e-graph" [:cite :nelson-1981] " takes a term in bottom-up. Each subterm becomes an e-node, and each e-node is put in an e-class: a set of nodes the graph knows to be equal. Nothing is equal to anything else yet, so classes and nodes are one to one, five of each, one row of the list per class. Hover a node of the tree and its row lights up. A node in the list is written with its children as class ids rather than as subterms, because a class is a set of terms and a node points at sets."]
    [:p "Click a node of the tree, or a class in the list, to open the class: what it holds, how many terms it stands for, what it points at and what points at it. " [:select [:* 2 :x] "Open the product"] ": one node, one term, two classes beneath it and one above. Then type any term, in either spelling, and add it. Every other lesson starts from this step."]]})

(def sharing
  {:key :sharing :n 2 :title "Sharing" :needs :cromulent :kind :script
   :operation :add
   :script tree-script
   :inputs [term-input]
   :values {:term [:* [:+ :x 1] [:+ :x 1]]}
   :alternatives [{:label "a·b + a·b" :values {:term [:+ [:* :a :b] [:* :a :b]]}}
                  {:label "(x + 2·y)·(x + 2·y)" :values {:term [:* [:+ :x [:* 2 :y]] [:+ :x [:* 2 :y]]]}}
                  {:label "(a + a) + (a + a)" :values {:term [:+ [:+ :a :a] [:+ :a :a]]}}]
   :panels #{:tree}
   :surprise {:draw generate/a-shared-term :wants {:sharing 1 :size 0.5}}
   :prose
   [[:p [:notation [:* [:+ :x 1] [:+ :x 1]]] " has seven tree nodes; the counter says the graph has four. The two " [:notation [:+ :x 1]] " subtrees are one term, and an e-graph stores each distinct term once: adding a node looks it up first, in a table keyed by the node itself, and a node that is already there returns its class instead of making a new one. That table is the hashcons" [:cite :ershov-1958 :goto-1974] "."]
    [:p "Hover either " [:notation [:+ :x 1]] " in the tree: both light up, and one row of the list. " [:select [:+ :x 1] "Open that class"] ": one node, and one parent, the product, which holds it as both of its children. " [:select [:* [:+ :x 1] [:+ :x 1]] "Open the product"] ": its one node points at one class, twice."]
    [:p "Sharing is why a graph can hold every arrangement of a sum in far less room than a list of them: lesson 7 holds the 1680 arrangements of five atoms in 185 nodes. It is also where the arithmetic stops being kind, and lesson 8 is the fix."]]})

(def congruence
  {:key :congruence :n 3 :title "Equality and congruence" :needs :cromulent :kind :script
   :operation :union
   :script congruence-script
   :inputs [{:key :lhs :arg "lhs" :label "one side" :says "one side of the equation" :type :term}
            {:key :rhs :arg "rhs" :label "the other side" :says "the other side" :type :term}
            {:key :wrapper :arg "wrapper" :label "the term over each side" :says "a term over each side, ?x standing for the side" :type :pattern}]
   :values {:lhs [:* :a 2] :rhs [:<< :a 1] :wrapper '[:/ ?x 2]}
   :alternatives [{:label "x + 0 = x, under a sine" :values {:lhs [:+ :x 0] :rhs :x :wrapper '[:sin ?x]}}
                  {:label "deeper: (?x + 1)·(?x + 1)" :values {:lhs [:* :a 2] :rhs [:<< :a 1] :wrapper '[:* [:+ ?x 1] [:+ ?x 1]]}}]
   :panels #{}
   :surprise {:draw generate/an-equation :wants {:congruence 1}}
   :prose
   [[:p "Add " [:notation [:/ [:* :a 2] 2]] " and " [:notation [:/ [:<< :a 1] 2]] ": " [:step 0 "seven classes"] ", one per distinct subterm. Now assert that " [:notation [:* :a 2]] " equals " [:notation [:<< :a 1]] ". Union merges their two classes into one and marks the graph dirty, " [:step 1 "rebuild pending"] ". Nothing else has moved: " [:select [:* :a 2] "open the merged class" 1] ", and it is pointed at by two nodes that read the same, a quotient of it by 2, sitting in two different classes. That is the broken invariant: equal children, unequal parents."]
    [:p "Rebuild restores it. It re-keys every node whose child moved, finds the two quotient nodes now identical, and merges their classes too, " [:step 2 "without being told"] ": five classes, and " [:select [:* :a 2] "the merged class" 2] " has one parent. That is congruence closure" [:cite :nelson-oppen-1980 :downey-sethi-tarjan-1980] ", and it is the whole trick of an e-graph: say one equality, and every consequence that follows from the shape of the terms comes for free. The example is from egg's README" [:cite :willsey-2021] ", and the pending step you scrubbed through is egg's deferred rebuild."]
    [:p "Change the sides, or the term over them, " [:native '?x] " standing for the side. " [:alternative "x + 0 = x, under a sine" "Try x + 0 = x under a sine"] ": the two sines become one class the same way, and " [:alternative "deeper: (?x + 1)·(?x + 1)" "a deeper term over each side"] " collapses two levels at once."]]})

(def rule
  {:key :rule :n 4 :title "A rule" :needs :cromulent :kind :embiggen
   :operation :saturate
   :inputs [term-input rules-input]
   :values {:term [:+ [:* :a 2] [:* :b 2]]
            :rules '[["mul-2-to-shift" [:* ?x 2] [:<< ?x 1]]]}
   :opts {:scheduler :simple :iter-limit 1}
   :costs [:ast-size :prefer-shift]
   :alternatives [{:label "egg's rules on 0 + 1·a, one iteration" :values {:term [:+ 0 [:* 1 :a]] :rules egg-rules}}
                  {:label "commutativity on a + b" :values {:term [:+ :a :b] :rules '[["comm" [:+ ?a ?b] [:+ ?b ?a]]]}}]
   :panels #{:matches}
   :surprise {:draw generate/an-arithmetic-term :wants {:merges-per-node 1 :size 0.5}}
   :prose
   [[:p "A rule is a pattern and a replacement: " [:native '[:* ?x 2]] " → " [:native '[:<< ?x 1]] ", multiplying by two is shifting left by one. Running it has two phases. " [:b "Search"] ": match the pattern against the graph, e-matching" [:cite :detlefs-nelson-saxe-2005 :de-moura-bjorner-2007] ". Every class holding a node of the pattern's shape is a match, with " [:native '?x] " bound to the child's class. " [:step 0 "At the input"] " the matches panel lists two, " [:notation [:* :a 2]] " and " [:notation [:* :b 2]] ", and their rows are highlighted."]
    [:p [:b "Apply"] ": for each match, build the replacement under its bindings and union it with the matched class. " [:step 1 "Iteration 1"] " adds three nodes: the number 1, which the graph did not have, in a class of its own, and a shift in each matched class beside the product it equals. The term is not rewritten; it is joined. " [:select [:* :a 2] "Open the class of a·2" 1] ": two nodes, each with its cost, and under AST size they tie. " [:cost :prefer-shift "Charge additions and multiplications"] " and the shift is cheaper in both classes, so the best term reads " [:notation [:+ [:<< :a 1] [:<< :b 1]]] "; " [:cost :ast-size "count nodes"] " and the two forms tie again, so which one the page shows is an accident of order. Both stay."]
    [:p "Edit the rules, one per line as " [:code "name: pattern → replacement"] ", or as " [:native '["name" [:* ?x 2] [:<< ?x 1]]] " triples; a variable on the right must appear on the left. " [:alternative "egg's rules on 0 + 1·a, one iteration" "Try egg's five rules on 0 + 1·a"] ": only the two commutations fire, because " [:native '[:+ ?a 0]] " needs the 0 on the right, and that form is what the commutation adds. Lesson 5 runs the same rules to the end."]]})

(def saturation
  {:key :saturation :n 5 :title "Saturation" :needs :cromulent :kind :embiggen
   :operation :saturate
   :inputs [term-input rules-input]
   :values {:term (sum-of 4) :rules ac-rules}
   :opts {:scheduler :backoff :match-limit 4 :ban-length 2 :iter-limit 30}
   :costs [:ast-size]
   :alternatives [{:label "the same, every match every time" :values {:term (sum-of 4) :rules ac-rules} :opts {:scheduler :simple}}
                  {:label "egg's rules on 0 + 1·a" :values {:term [:+ 0 [:* 1 :a]] :rules egg-rules} :opts {:scheduler :simple}}
                  {:label "five atoms under a node limit of 100" :values {:term (sum-of 5) :rules ac-rules} :opts {:scheduler :simple :node-limit 100}}]
   :panels #{:stats}
   :surprise {:draw generate/a-small-arithmetic-term :wants {:iterations 1 :saturated 1 :merges-per-node 0.5}}
   :prose
   [[:p "Saturation runs the rules again and again until no application changes the graph: equality saturation" [:cite :tate-2009] ". Each iteration searches every rule against the same snapshot, applies every match, then rebuilds. The table below shows each iteration: how many matches each rule found, how many applications merged something, the counts and the time; click a row to scrub there. An iteration that applied nothing is the sign to stop."]
    [:p "This run uses egg's " [:b "backoff"] " scheduler" [:cite :willsey-2021] ": a rule whose matches exceed its budget is banned for a few iterations, and its budget and its ban double each time it happens. Saturation is declared only when an iteration applied nothing " [:i "and"] " no rule is banned; otherwise the bans are lifted and the run goes on. That is why fourteen iterations end with a quiet one, and why the stop reason says saturated rather than a limit. " [:alternative "the same, every match every time" "Apply every match every time"] ": six iterations, the same fifteen classes and fifty-four nodes."]
    [:p "The other stop reasons are limits: on iterations, on nodes, on time. " [:alternative "five atoms under a node limit of 100" "Five atoms under a node limit of 100"] " stops at the node limit on purpose, and the step tile says so. " [:alternative "egg's rules on 0 + 1·a" "Egg's rules on 0 + 1·a"] " saturate in three iterations, and the best term is " [:notation :a] "."]]})

(def taste
  {:key :taste :n 6 :title "Extraction is taste" :needs :cromulent :kind :embiggen
   :operation :saturate
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
   :surprise {:draw generate/a-doubled-term :wants {:disagreement 1 :size 0.3}}
   :prose
   [[:p "After saturation the class of the input holds three forms: " [:notation [:+ :a :a]] ", " [:notation [:* :a 2]] ", " [:notation [:<< :a 1]] ". Which is the answer? The e-graph does not say. A cost function does, and choosing by one is extraction" [:cite :tate-2009 :willsey-2021] ": walk the classes bottom-up, give every node the cost of itself plus its cheapest children, and pick the cheapest node of each class. " [:select [:+ :a :a] "Open the class"] ": three nodes, each with its cost under the cost in force, the cheapest outlined, and the three terms it stands for in cost order."]
    [:p "Now change the cost. " [:cost :prefer-add "Charge multiplications and shifts"] " and the answer is " [:notation [:+ :a :a]] "; " [:cost :prefer-mul "charge additions and shifts"] ", " [:notation [:* :a 2]] "; " [:cost :prefer-shift "charge additions and multiplications"] ", " [:notation [:<< :a 1]] ". Same graph, three answers. This is the thesis behind bendix: simplification is equality saturation plus taste, and the taste is a cost function. " [:cost :ast-size "AST size"] ", the default in the other lessons, ties here, since the three forms are the same size, and a tie falls to a fixed but arbitrary order."]
    [:p "The choice is made in every class, so it nests. " [:alternative "(b·2) + (b·2)" "Try (b·2) + (b·2)"] ": " [:notation [:<< [:<< :b 1] 1]] " under shifts, " [:notation [:+ [:+ :b :b] [:+ :b :b]]] " under additions, and the input's class stands for fifteen terms."]]})

(def blowup
  {:key :blowup :n 7 :title "The blowup" :needs :cromulent :kind :embiggen
   :operation :saturate
   :inputs [term-input rules-input]
   :values {:term (sum-of 5) :rules ac-rules}
   :opts {:scheduler :simple :iter-limit 12 :node-limit 5000}
   :costs [:ast-size]
   :alternatives [{:label "four atoms" :values {:term (sum-of 4) :rules ac-rules}}
                  {:label "six atoms" :values {:term (sum-of 6) :rules ac-rules}}
                  {:label "six atoms under a node limit of 500" :values {:term (sum-of 6) :rules ac-rules} :opts {:node-limit 500}}]
   :panels #{:stats}
   :surprise {:draw generate/a-sum :wants {:growth 1 :saturated 0.5} :n 8}
   :prose
   [[:p "A sum of five atoms, " [:notation (sum-of 5)] ", is one term of nine nodes: five atoms and four additions. The e-graph starts there, at " [:step 0 "nine classes and nine nodes"] "."]
    [:p "Two rules say that the order and the grouping of a sum do not matter. Commutativity: " [:native '[:+ ?a ?b]] " → " [:native '[:+ ?b ?a]] ". Associativity: " [:native '[:+ [:+ ?a ?b] ?c]] " → " [:native '[:+ ?a [:+ ?b ?c]]] ". Under them every arrangement of the sum equals every other, and an e-graph is obliged to hold them all."]
    [:p "Watch the counters as the iterations run, or scrub: 19 nodes after " [:step 1 "the first iteration"] ", then 45, 98, 162, and 187 after " [:step 5 "the fifth"] "; two fewer after the sixth, as the last merges fold nodes together; and the rules stop when there is nothing left to add. Every non-empty subset of the five atoms has become a class, 2⁵ − 1 = 31 of them, and every way of splitting a subset in two a node, 3⁵ − 2⁶ + 1 = 180 of them, plus the five atoms. " [:select (sum-of 5) "Open the input's class"] ": thirty nodes, standing for 1680 terms, every arrangement of the sum, all of them costing nine. The graph grows by three to the n. " [:alternative "six atoms" "Six atoms"] " climb past six hundred nodes for 30240 arrangements; nine would need eighteen thousand nodes."]
    [:p "This is the blowup. Commutativity and associativity are the two rules a computer algebra system cannot do without, and the two an e-graph cannot afford. Deciding equality modulo both is a solved problem" [:cite :bachmair-2000] ", but a rewriting engine has to hold the forms, and the usual answer is lesson 5's backoff" [:cite :willsey-2021] ": ban the rule that fires too often. " [:alternative "six atoms under a node limit of 500" "Six atoms under a node limit of 500"] " stop at the limit with the sum unfinished. Lesson 8 is the fix."]]})

(def what-if
  {:key :what-if :n 10 :title "What if" :needs :cromulent :kind :script
   :operation :what-if
   :script what-if-script
   :inputs [term-input
            {:key :lhs :arg "lhs" :label "what if this…" :says "what if this…" :type :term}
            {:key :rhs :arg "rhs" :label "…equalled this" :says "…equalled this" :type :term}]
   :values {:term [:+ [:* :x :x] [:* 2 :x]] :lhs :x :rhs 2}
   :alternatives [{:label "x·y + y·x, what if y = x" :values {:term [:+ [:* :x :y] [:* :y :x]] :lhs :y :rhs :x}}
                  {:label "sin x + sin y, what if x = y" :values {:term [:+ [:sin :x] [:sin :y]] :lhs :x :rhs :y}}]
   :panels #{:fork}
   :surprise {:draw generate/a-what-if :wants {:congruence 1 :size 0.3}}
   :prose
   [[:p "The e-graph is a value. Asserting something in it does not change it; it makes a new one, and the old one is still there. Take " [:notation [:+ [:* :x :x] [:* 2 :x]]] " and ask: what if " [:notation :x] " were 2? Union the classes of " [:notation :x] " and 2 in a copy, " [:step 1 "rebuild pending"] ". " [:select :x "Open the class of x" 1] ": it holds x and 2, and it is pointed at by two products that now read the same, that class times itself, in two classes. " [:step 2 "Rebuild"] " merges them by congruence, and the sum becomes a sum of a class with itself: " [:select [:+ [:* :x :x] [:* 2 :x]] "the input's class" 2] " stands for sixteen terms, from " [:notation [:+ [:* :x :x] [:* 2 :x]]] " to " [:notation [:+ [:* 2 2] [:* 2 2]]] ", all of one size, so AST size cannot choose between them."]
    [:p "The original has not moved: it is step 0, beside the copy. In a mutable e-graph this needs an undo log or a deep copy; here a fork is a " [:native 'let] ". The e-graph is persistent" [:cite :driscoll-1989] " because it is made of Clojure's collections, which are" [:cite :bagwell-2001 :hickey-2020] ". " [:alternative "x·y + y·x, what if y = x" "What if y were x"] ": the two products become one node, and the sum a class added to itself."]]})

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
   :operation :simplify
   :inputs [term-input rules-input]
   :values {:term (sum-of 5) :rules ac-rules}
   :opts (assoc bendix-opts :iter-limit 12 :node-limit 5000)
   :costs [:bendix :ast-size]
   :alternatives [{:label "another arrangement of the same sum" :values {:term [:+ :a4 [:+ :a3 [:+ :a2 [:+ :a1 :a0]]]] :rules ac-rules}}
                  {:label "no rules at all" :values {:term (sum-of 5) :rules []}}
                  {:label "six atoms" :values {:term (sum-of 6) :rules ac-rules}}]
   :panels #{:stats}
   :surprise {:draw generate/a-ring-term :wants {:analysis-merges 1 :shrink 0.5 :saturated 0.5 :size 0.5}}
   :prose
   [[:p "Lesson 7's sum, " [:notation (sum-of 5)] ", the same two rules, and one addition: this e-graph carries the polynomial analysis. Every class computes what it is worth as a polynomial over the atoms, " [:notation [:+ :a0 :a1 :a2 :a3 :a4]] " for the whole sum and " [:notation [:+ :a0 :a1]] " for its first pair, and two classes with the same polynomial are merged as the node is added, before any rule sees it. The column on the right is that polynomial."]
    [:p "Run it. " [:step 1 "One iteration"] ", and the rules merge nothing: commutativity proposes " [:notation [:+ :a1 :a0]] " for the class of " [:notation [:+ :a0 :a1]] ", the analysis has already put it there, and a union of a class with itself is not a merge. Associativity adds three classes, the right-nested pairs, and nothing more. Twelve classes and nineteen nodes where lesson 7 needed thirty-one and a hundred and eighty-five, and the runner stops there: an iteration that merges nothing is saturation."]
    [:p "The arrangement of the sum has stopped mattering. " [:step 2 "The last step"] " writes every polynomial into the graph as a term, so that the best term under bendix's cost is " [:notation [:+ :a0 :a1 :a2 :a3 :a4]] " whatever was typed. " [:select [:+ :a0 :a1 :a2 :a3 :a4] "Open the input's class" 2] ": the five-way sum the analysis wrote is its cheapest term, and the whole class stands for thirty-seven terms where lesson 7's stood for 1680. " [:alternative "another arrangement of the same sum" "Type another arrangement"] ": the same polynomial, the same best term. At the REPL, " [:native '(second (eg/add g [:+ :a4 [:+ :a3 [:+ :a2 [:+ :a1 :a0]]]]))] " returns the input's own class: the arrangement was already there."]
    [:p "This is the fix: commutativity, associativity, distributivity and cancellation are not rules but a decision procedure inside every class, a polynomial normal form, and the blowup never starts. The class carries it as an e-class analysis" [:cite :willsey-2021] ", a decision procedure cooperating with congruence closure" [:cite :nelson-oppen-1979] ", which makes this an e-graph modulo a theory" [:cite :zucker-2025] ". " [:alternative "six atoms" "Six atoms"] ", which needed six hundred nodes in lesson 7, take twenty-eight."]]})

(def polynomial-rule
  {:key :polynomial-rule :n 9 :title "A rule over the polynomial" :needs :bendix :kind :embiggen
   :operation :simplify
   :operation-says {:call ["(bx/simplify term"
                           "  {:rules rules/trig"
                           "   :cost cost"
                           "   opts})"]}
   :inputs [term-input]
   :values {:term [:+ [:+ [:+ :a s2] c2] :b] :rules rules/trig}
   :opts bendix-opts
   :costs [:bendix :ast-size]
   :alternatives [{:label "in another arrangement" :values {:term [:+ [:+ :a c2] [:+ s2 :b]]}}
                  {:label "1 − cos²x" :values {:term [:- 1 c2]}}
                  {:label "with a cofactor" :values {:term [:+ [:* :y s2] [:* :y c2]]}}
                  {:label "sin(x + y) and cos(y + x)" :values {:term [:+ [:expt [:sin [:+ :x :y]] 2] [:expt [:cos [:+ :y :x]] 2]]}}]
   :panels #{:stats}
   :surprise {:draw generate/a-pythagorean-term :wants {:rule-fired 1 :shrink 0.5}}
   :prose
   [[:p [:notation [:+ s2 c2]] " = 1 is not a ring identity. The ring sees " [:notation [:sin :x]] " and " [:notation [:cos :x]] " as two atoms it knows nothing about, and the class of " [:notation [:+ [:+ [:+ :a s2] c2] :b]] " is worth sin²x + cos²x + a + b, nothing less. A pattern rule would need the two squares side by side, and here they are not."]
    [:p "The pythagoras rule reads the polynomial instead of the nodes" [:cite :zucker-2025] ". For every class whose polynomial mentions a sine and a cosine of one argument, it reduces the polynomial modulo sin²x = 1 − cos²x, and modulo cos²x = 1 − sin²x, and where the result differs it proposes it as another form of the class: a + b + 1 here. The runner adds that form as a term and unions it in, the analysis keeps the smaller polynomial, and the classes that share one merge. " [:step 1 "Iteration 1"] " proposes a form for five classes, " [:step 2 "iteration 2"] " for two more, and the third finds nothing."]
    [:p "The arrangement never mattered, because the rule never looked at it. " [:step 4 "The last step"] " writes the polynomials in as terms, and the best term under bendix's cost is " [:notation [:+ :a :b 1]] ". " [:select [:expt [:sin :x] 2] "Open the class of sin²x" 4] ": it holds a second form, 1 plus a class that is at once −1·cos²x and sin²x − 1, so the class reaches itself and stands for infinitely many terms, which is what the panel says. " [:alternative "1 − cos²x" "Try 1 − cos²x"] ": the sine does not exist in the graph yet, and the rule's proposal creates it."]]})

(def differentiation
  {:key :differentiation :n 11 :title "Differentiation is simplification" :needs :bendix :kind :embiggen
   :operation :differentiate
   :inputs [{:key :term :arg "term" :label "the function" :says "the function to differentiate" :type :term}
            {:key :var :arg "x" :label "with respect to" :says "the variable to differentiate by" :type :term}]
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
   :surprise {:draw generate/a-function :wants {:derivative-free 1 :iterations 0.5 :saturated 0.5}}
   :prose
   [[:p "A derivative is a term like any other: " [:notation [:D [:sin [:* 2 :x]] :x]] " is a node with two children, and differentiating is saturating under rules" [:cite :willsey-2021] ". The ring part is not a rule at all: for a class worth a polynomial, its derivative is computed from the polynomial, so linearity, the product rule and the power rule are polynomial calculus. The chain rule is one pattern rule per operator: " [:native '[:D [:sin ?u] ?x]] " → " [:native '[:* [:cos ?u] [:D ?u ?x]]] "."]
    [:p [:step 1 "Iteration 1"] ": d-sin fires, and the class of the derivative gains " [:notation [:* [:cos [:* 2 :x]] [:D [:* 2 :x] :x]]] ". " [:step 2 "Iteration 2"] ": the ring differentiates 2·x to 2, so that product is worth 2·cos(2·x). The third iteration finds nothing, and " [:step 4 "the last step"] " writes the normal forms in. " [:select [:D [:sin [:* 2 :x]] :x] "Open the derivative's class" 4] ": the derivative node and the two products it equals, each with its cost, the derivative dearest under no-D."]
    [:p "Which term is the answer is the cost's decision. Bendix's default cost charges a derivative node no more than a sine, so for " [:alternative "sin(sin(sin x))" "sin(sin(sin x))"] " under " [:cost :bendix "bendix's default"] " it keeps the derivative unevaluated: the node is cheaper than the product of three cosines. The " [:cost :no-D "no-D cost"] " counts what is still under a derivative before it counts size, so a derivative-free spelling wins whenever one exists, and when none does, " [:alternative "x·|x|: no rule for abs" "x·|x|"] " say, the D stays and the answer says so."]]})

(def all
  [basics
   intro
   tree
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

(def start
  "Where the page opens when the address names no lesson: the
  basics."
  :basics)

(defn heading
  "What a lesson goes by, its number and its title; a page before
  the lessons has no number and goes by its title alone."
  [{:keys [n title]}]
  (if n (str n ". " title) title))

(defn nav-label
  "What the navigation calls a lesson: its heading, unless it says
  something shorter for itself."
  [lesson]
  (or (:nav lesson) (heading lesson)))

(defn address
  "A lesson's address, what follows # in the page's: its number, or
  for a page before the lessons its key's name."
  [{:keys [n key]}]
  (if n (str n) (name key)))

(defn by-address
  "The lesson at an address, or nil."
  [a]
  (some (fn [l] (when (= a (address l)) l)) all))

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
