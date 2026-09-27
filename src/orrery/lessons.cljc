(ns orrery.lessons
  "Defines the lessons as data.

  A lesson is a map. It holds:

  - a number and a title
  - prose, as hiccup, with the widgets of `widget-kinds` in it
  - the inputs the learner may edit, and their curated values
  - for a saturation, the rules and the runner options; for a
    script, the function from values to steps
  - the alternatives for \"try another\"
  - the optional panels the page shows
  - for \"surprise me\", the draw that makes a candidate and the
    features the lesson wants to be high (orrery.generate,
    orrery.score)
  - its operation, which is what its workbench runs (`operations`).
    The inputs are the arguments of that operation.

  Prose is data so that the JVM tests can walk it and the page can
  render it. There is no markdown.

  Three pages are shaped like lessons and have no number. Two come
  before the lessons: the basics, where the page opens, which
  assumes nothing, and the introduction, which says what an e-graph
  is and why it matters. One comes after the lessons: the REPL page.
  Its prose is the documentation of the REPL, and its widgets are
  lines of code that the reader evaluates. It opens with the REPL's
  dock open.

  `about` says what the site is. It is shown once, above the page
  the site opens on. `colophon` says the same in one line under
  every page."
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
  "Returns a left-nested sum of n atoms, a0 + a1 + …. This is the
  fixture of experiment 1 in ../design/ac-problem.md."
  [n]
  (reduce (fn [acc i] [:+ acc (keyword (str "a" i))]) :a0 (range 1 n)))

(def ac-rules
  "Commutativity and associativity of +, as [name lhs rhs] data."
  '[["comm" [:+ ?a ?b] [:+ ?b ?a]]
    ["assoc" [:+ [:+ ?a ?b] ?c] [:+ ?a [:+ ?b ?c]]]])

(def egg-rules
  "The rule set from egg's README."
  '[["commute-add" [:+ ?a ?b] [:+ ?b ?a]]
    ["commute-mul" [:* ?a ?b] [:* ?b ?a]]
    ["add-0" [:+ ?a 0] ?a]
    ["mul-0" [:* ?a 0] 0]
    ["mul-1" [:* ?a 1] ?a]])

(def basics-rules
  "Two rules that anyone can check with a number in hand."
  '[["add-0" [:+ ?a 0] ?a]
    ["mul-1" [:* ?a 1] ?a]])

(def intro-rules
  "The four rules of the example that egg's paper opens with. One
  rule spoils (a·2)/2 for a rewriter, and the other three take it
  to a."
  '[["mul-2-to-shift" [:* ?x 2] [:<< ?x 1]]
    ["regroup" [:/ [:* ?x ?y] ?z] [:* ?x [:/ ?y ?z]]]
    ["cancel" [:/ ?x ?x] 1]
    ["mul-1" [:* ?x 1] ?x]])

(defn rules-of
  "Returns rule maps made from [name lhs rhs] data. A rule that is
  already a map, such as one of bendix's normal-form rules, is
  returned as it is."
  [rule-data]
  (mapv (fn [r] (if (map? r) r (let [[n lhs rhs] r] (rw/rule n lhs rhs)))) rule-data))

(def term-input
  {:key :term :arg "term" :label "the term" :says "the expression to start from" :type :term})

(def rules-input
  {:key :rules :arg "rules" :label "the rules" :type :rules
   :says "what may be written as what, as [name pattern replacement]"
   :notation-says "what may be written as what, one per line as name: pattern → replacement"})

(defn input-label
  "Returns the input's label: the words used for it in a sentence,
  such as an error message."
  [input]
  (:label input))

(defn input-says
  "Returns the description of an input in the given print mode. The
  page shows it above the field, beside the name of the argument."
  [{:keys [says notation-says]} mode]
  (if (and (= :notation mode) notation-says) notation-says says))

(def operations
  "Maps a key to an operation, which is what a workbench runs. An
  operation has:

  - `:name`, the algorithm in a word
  - `:fn`, the function that implements it, under the name the REPL
    uses for it
  - `:says`, what it does, in a sentence or two
  - `:call`, the call, in lines narrow enough for the panel. Its
    arguments have the names of the fields. `g` is the e-graph, and
    `opts` stands for the runner options in force (see `call`).

  The page steps the same functions one iteration or one call at a
  time, so that there is something to scrub."
  {:add
   {:name "add" :fn "eg/add"
    :says "Put the term into an empty e-graph. Subterms go in first, and each distinct subterm goes in once."
    :call ["(eg/add (eg/egraph) term)"]}
   :union
   {:name "union, then rebuild" :fn "eg/union · eg/rebuild"
    :says "Add the wrapper over each side, say that the two sides are equal, and let rebuild find what follows from it."
    :call ["(eg/add g wrapper)  ; for each side"
           "(eg/union g lhs rhs)"
           "(eg/rebuild g)"]}
   :what-if
   {:name "union, in a copy" :fn "eg/union · eg/rebuild"
    :says "Add the term. Then, in a copy of the e-graph, say that lhs equals rhs, and rebuild. The original is a value and does not change."
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
    :says "Simplify D(term, x), the derivative of term by x, under the derivative rules and under the cost that charges for what is still under a D."
    :call ["(bx/differentiate term x"
           "  {opts})"]}})

(defn operation
  "Returns the operation that the workbench of a lesson runs. This
  is the entry of `operations` that the lesson names, with the
  lesson's own `:operation-says` merged over it."
  [lesson]
  (merge (get operations (:operation lesson)) (:operation-says lesson)))

(def ^:private shown-opts
  "The runner options a call shows, in this order."
  [:scheduler :match-limit :ban-length :iter-limit :node-limit])

(defn call
  "Returns the lines of a lesson's call, with the runner options in
  force written out. The options are the lesson's options with
  `opts` merged over them. The line that says `opts` becomes one
  line for each option, aligned under the first."
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
  "The kinds of widget. A widget is a vector in the prose that the
  page resolves, where it would render any other vector as hiccup.

  - `[:notation t]` and `[:native t]` print a term.
  - `[:step k label]` scrubs to step k.
  - `[:select t label]` opens the class of t. `[:select t label k]`
    scrubs to step k first.
  - `[:cost key label]` sets the cost.
  - `[:alternative label text]` chooses one of the alternatives of
    the lesson.
  - `[:print mode label]` switches the print mode.
  - `[:lesson key label]` goes to another lesson.
  - `[:eval code]` evaluates code at the REPL as if the reader had
    typed it. The code itself is the link.
  - `[:cite key …]` cites works of `reading`. It follows the idea it
    credits, and shows as short author-year links.

  The prose is written in the explorable voice. Each paragraph says
  what is on the page and gives the reader one of these widgets to
  use. It cites a work; it does not tell the story of the work. It
  is written in plain sentences: subject, verb, object."
  #{:notation :native :step :select :cost :alternative :print :lesson :eval :cite})

(def reading
  "Maps a key to a work that the prose cites. A work has `:who`,
  `:what`, `:where` and `:year`, the `:short` author-year form that
  a citation shows, and a `:url` when the paper has one. The link is
  a DOI where one exists. The links were verified on 2026-09-26."
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

(def libraries
  "Maps the key of a library that orrery runs to its repository."
  {:cromulent "https://github.com/noonian/cromulent"
   :bendix "https://github.com/noonian/bendix"})

(defn library
  "Returns a link to the repository of the library `k`, as hiccup the
  prose renders as it is. `label` is the text of the link, the name
  of the library by default. It is a link out of the page, not a
  widget."
  ([k] (library k (name k)))
  ([k label] [:a {:href (libraries k) :target "_blank" :rel "noreferrer"} label]))

(defn widgets
  "Returns every widget in the prose of a lesson, in reading order."
  [lesson]
  (letfn [(walk [x]
            (cond (and (vector? x) (contains? widget-kinds (first x))) [x]
                  (vector? x) (mapcat walk (rest x))
                  :else nil))]
    (vec (mapcat walk (:prose lesson)))))

(defn credits
  "Returns the keys of `reading` that the prose of a lesson cites, in
  order of first citation. The page shows them as the reading line
  of the lesson."
  [lesson]
  (vec (distinct (mapcat rest (filter #(= :cite (first %)) (widgets lesson))))))

(defn- fill
  "Returns wrapper with ?x replaced by t."
  [wrapper t]
  (walk/postwalk-replace {'?x t} wrapper))

;; ---------------------------------------------------------------------------
;; scripts

(defn- add-all [g terms]
  (reduce (fn [[g ids] t] (let [[g id] (eg/add g t)] [g (conj ids id)])) [g []] terms))

(defn tree-script
  "Returns a run of one step, in which the term has been added."
  [{:keys [term]}]
  (let [[g id] (eg/add (eg/egraph) term)]
    (run/script [["the term" g]] id)))

(defn congruence-script
  "Returns a run of three steps. The first adds the wrapper over
  each side of the equation. The second asserts the equation. The
  third rebuilds."
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
  "Returns a run of three steps. The first adds the term. The second
  asserts an equation in a copy. The third rebuilds the copy. The
  original is step 0 and does not change."
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
  "Says what the site is. It has no widgets. The page shows it above
  the page the site opens on, before any paragraph that links into
  the running widgets. The wording is the Captain's (IDEA.md section
  12, decision 13)."
  [[:p [:b "orrery"] " is an interactive tool for exploring e-graphs and learning how they work, its author's learning included. It embeds two libraries and runs them live in this page: " [:b (library :cromulent)] ", an e-graph that is an immutable, persistent value, and " [:b (library :bendix)] ", a nascent computer algebra system built on it. The panels are widgets over what those libraries really compute, there to be scrubbed, opened and changed until the behaviour makes sense."]
   [:p "It is largely written using LLMs."]])

(def colophon
  "Says what the site is in one line. The page shows it under every
  page, with a link back to `about`."
  [:p "orrery is an interactive tool for exploring e-graphs. It runs " (library :cromulent) " and " (library :bendix) " live in the page, and it is largely written using LLMs. " [:lesson :basics "What this is"] "."])

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
   [[:p "Six, half a dozen and 2·3 are three ways to write one number. " [:notation [:* [:+ :x 0] 1]] " is a long way to write " [:notation :x] ", whatever number " [:notation :x] " is. Adding nothing changes nothing, and one times anything is that thing. (The dot means times.)"]
    [:p "A fact like that is a rule. A rule has two shapes that always mean the same thing. " [:notation '[:+ ?a 0]] " → " [:notation '?a] " says that anything plus zero is the thing itself. Here " [:notation '?a] " stands for whatever is there. " [:notation '[:* ?a 1]] " → " [:notation '?a] " says the same about times one. Simplifying means using rules to find a shorter way to write something."]
    [:p "The usual way to simplify is to cross out the long form and write the short one over it. Then the long form is gone. An e-graph" [:cite :nelson-1981] " crosses nothing out. It collects every way of writing a thing that the rules turn up. It keeps the ways that mean the same thing together in a class, which is a box in the picture."]
    [:p [:step 0 "At the start"] " the five parts of the expression sit in five boxes. Each part is written with pointers: # and a number stands for whatever that box holds. " [:step 1 "Run the rules once"] " and three boxes become one. " [:select [:* [:+ :x 0] 1] "Open it" 1] ": " [:notation :x] ", " [:notation [:+ :x 0]] " and " [:notation [:* [:+ :x 0] 1]] " are one thing written three ways. There are more: if " [:notation [:+ :x 0]] " is " [:notation :x] ", then so is " [:notation [:+ [:+ :x 0] 0]] ", and so on without end."]
    [:p "Running the rules until they turn up nothing new is called saturating" [:cite :tate-2009] ". Here " [:step 2 "the second pass"] " finds nothing. Then the e-graph chooses. When it is asked for the best way to write what it was given, it looks in that box and takes the shortest way, " [:notation :x] ". That is the whole idea: collect every way, keep the ways that mean the same thing together, and choose one at the end."]
    [:p [:alternative "nothing to do: x·y" "Give it x·y"] ", where no rule applies, and it comes back unchanged. You can also " [:alternative "two letters: (x + 0)·(y·1)" "give it two letters"] " or " [:alternative "(x + 0)·1" "put (x + 0)·1 back"] ", or type an expression of your own and run it. " [:lesson :intro "The next page"] " shows what collecting can do that crossing out cannot."]]})

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
   [[:p "A simplifier rewrites a term: it finds a pattern and puts something better in its place. In " [:notation [:/ [:* :a 2] 2]] " the product can become a shift, " [:notation [:<< :a 1]] ", which a machine prefers, or the twos can cancel, which leaves " [:notation :a] ". A rewriter that shifts first holds " [:notation [:/ [:<< :a 1] 2]] " and can no longer cancel: " [:alternative "what shifting first leaves: (a << 1)/2" "start from there"] " and the same rules find nothing to do. No order of rules is right for every term. This is the phase-ordering problem" [:cite :tate-2009] "."]
    [:p "An e-graph" [:cite :nelson-1981] " does not choose. " [:alternative "(a·2)/2" "Give it (a·2)/2"] ". Each distinct subterm is a node that is stored once, and nodes known to be equal share a class, which is a box in the picture. " [:step 0 "At the input"] " there are four classes of one node each. A rule does not replace what it matches. It adds to the same class. So " [:step 1 "after one iteration"] " the " [:select [:* :a 2] "class of a·2" 1] " holds the shift beside the product, and the product is still there for the next rule."]
    [:p "Running the rules until none of them has anything to add is called equality saturation" [:cite :tate-2009 :willsey-2021] ". " [:step 2 "The twos cancel"] ". Then " [:step 3 "a times one is a"] ", so the class of the input and the class of " [:notation :a] " become one. Finally " [:step 4 "a fourth iteration"] " finds nothing new. Nothing was thrown away, so the order in which the rules fired never mattered. Choosing comes last. A cost function ranks the terms that a class stands for, and extraction reads off the cheapest one, which here is " [:notation :a] "."]
    [:p "That is why e-graphs are interesting. They rewrite without regret, they keep the choice of an answer apart from the search for one, and they hold many terms in few nodes. " [:select [:/ [:* :a 2] 2] "The input's class" 4] " stands for infinitely many terms, because " [:notation :a] " equals " [:notation [:/ [:* :a 2] 2]] ", and the " [:notation :a] " inside that equals " [:notation [:/ [:* :a 2] 2]] " again. E-graphs are used in theorem provers" [:cite :detlefs-nelson-saxe-2005 :de-moura-bjorner-2007] ", optimizing compilers" [:cite :tate-2009] " and tools that make floating-point arithmetic more accurate" [:cite :panchekha-2015] "."]
    [:p "The lessons take this page apart: " [:lesson :tree "a term as a tree"] ", " [:lesson :sharing "sharing"] ", " [:lesson :congruence "equality"] ", " [:lesson :rule "a rule"] ", " [:lesson :saturation "saturation"] " and " [:lesson :taste "the choice of an answer"] "; then " [:lesson :blowup "what holding everything costs"] ", " [:lesson :fix "the fix"] " and " [:lesson :polynomial-rule "a rule over it"] "; then " [:lesson :what-if "a what-if"] " and " [:lesson :differentiation "differentiation"] ". Everything below is the engine running, not a drawing of it. Scrub the steps, click a box, or change the term or the rules and run them."]]})

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
   [[:p [:notation [:+ [:* 2 :x] :y]] " is a tree. An addition is at the root, a product and " [:notation :y] " are beneath it, and 2 and " [:notation :x] " are beneath the product. The tree panel draws it. In the native spelling it is " [:native [:+ [:* 2 :x] :y]] ". A vector is a node: its first element is the operator, and the rest are its children. A keyword is a variable, and a number is a number. " [:print :native "Switch the page to native"] " and every panel prints it that way. Then " [:print :notation "switch back"] " and it is mathematics again."]
    [:p "An e-graph" [:cite :nelson-1981] " takes a term in from the bottom up. Each subterm becomes an e-node, and each e-node is put in an e-class, which is a set of nodes that the graph knows to be equal. Nothing is equal to anything else yet, so there is one class for each node: five of each, and one row of the list for each class. Hover a node of the tree and its row lights up. The list writes a node with class ids for its children, not subterms, because a class is a set of terms and a node points at sets."]
    [:p "Click a node of the tree, or a class in the list, to open the class. The opened class shows what it holds, how many terms it stands for, what it points at and what points at it. " [:select [:* 2 :x] "Open the product"] ": it has one node and one term, with two classes beneath it and one above. Then type any term, in either spelling, and add it. Every other lesson starts from this step."]]})

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
   [[:p [:notation [:* [:+ :x 1] [:+ :x 1]]] " has seven tree nodes, but the counter says the graph has four. The two " [:notation [:+ :x 1]] " subtrees are one term, and an e-graph stores each distinct term once. Adding a node first looks it up in a table that is keyed by the node itself. If the node is already there, the lookup returns its class and no new class is made. That table is the hashcons" [:cite :ershov-1958 :goto-1974] "."]
    [:p "Hover either " [:notation [:+ :x 1]] " in the tree. Both light up, and so does one row of the list. " [:select [:+ :x 1] "Open that class"] ": it has one node and one parent. The parent is the product, which holds this class as both of its children. " [:select [:* [:+ :x 1] [:+ :x 1]] "Open the product"] ": its one node points at the same class twice."]
    [:p "Sharing is why a graph can hold every arrangement of a sum in far less room than a list of them would take. Lesson 7 holds the 1680 arrangements of five atoms in 185 nodes. Sharing is also where the arithmetic stops being kind, and lesson 8 is the fix."]]})

(def congruence
  {:key :congruence :n 3 :title "Equality and congruence" :needs :cromulent :kind :script
   :operation :union
   :script congruence-script
   :inputs [{:key :lhs :arg "lhs" :label "one side" :says "one side of the equation" :type :term}
            {:key :rhs :arg "rhs" :label "the other side" :says "the other side" :type :term}
            {:key :wrapper :arg "wrapper" :label "the term over each side" :says "a term over each side, where ?x stands for the side" :type :pattern}]
   :values {:lhs [:* :a 2] :rhs [:<< :a 1] :wrapper '[:/ ?x 2]}
   :alternatives [{:label "x + 0 = x, under a sine" :values {:lhs [:+ :x 0] :rhs :x :wrapper '[:sin ?x]}}
                  {:label "deeper: (?x + 1)·(?x + 1)" :values {:lhs [:* :a 2] :rhs [:<< :a 1] :wrapper '[:* [:+ ?x 1] [:+ ?x 1]]}}]
   :panels #{}
   :surprise {:draw generate/an-equation :wants {:congruence 1}}
   :prose
   [[:p "Add " [:notation [:/ [:* :a 2] 2]] " and " [:notation [:/ [:<< :a 1] 2]] ". That makes " [:step 0 "seven classes"] ", one for each distinct subterm. Now assert that " [:notation [:* :a 2]] " equals " [:notation [:<< :a 1]] ". Union merges their two classes into one and marks the graph dirty, with a " [:step 1 "rebuild pending"] ". Nothing else has moved. If you " [:select [:* :a 2] "open the merged class" 1] ", you see that two nodes point at it. Both nodes read the same, as this class divided by 2, but they sit in two different classes. That is the broken invariant: the children are equal and the parents are not."]
    [:p "Rebuild restores the invariant. It re-keys every node whose child moved, finds that the two quotient nodes are now identical, and merges their classes too, " [:step 2 "without being told"] ". Now there are five classes, and " [:select [:* :a 2] "the merged class" 2] " has one parent. This is congruence closure" [:cite :nelson-oppen-1980 :downey-sethi-tarjan-1980] ", and it is the whole trick of an e-graph. You state one equality, and every consequence that follows from the shape of the terms comes for free. The example is from egg's README" [:cite :willsey-2021] ". The pending step that you scrubbed through is egg's deferred rebuild."]
    [:p "Change the sides, or change the term over them, where " [:native '?x] " stands for the side. " [:alternative "x + 0 = x, under a sine" "Try x + 0 = x under a sine"] ": the two sines become one class in the same way. With " [:alternative "deeper: (?x + 1)·(?x + 1)" "a deeper term over each side"] ", two levels collapse at once."]]})

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
   [[:p "A rule is a pattern and a replacement. " [:native '[:* ?x 2]] " → " [:native '[:<< ?x 1]] " says that multiplying by two is shifting left by one. Running a rule has two phases. " [:b "Search"] " matches the pattern against the graph, which is called e-matching" [:cite :detlefs-nelson-saxe-2005 :de-moura-bjorner-2007] ". Every class that holds a node of the pattern's shape is a match, and " [:native '?x] " is bound to the class of that node's child. " [:step 0 "At the input"] " the matches panel lists two matches, " [:notation [:* :a 2]] " and " [:notation [:* :b 2]] ", and their rows are highlighted."]
    [:p [:b "Apply"] " takes each match, builds the replacement under its bindings and unions it with the matched class. " [:step 1 "Iteration 1"] " adds three nodes. One is the number 1, which the graph did not have, in a class of its own. The other two are shifts, one in each matched class, beside the product it equals. The term is not rewritten. It is joined. " [:select [:* :a 2] "Open the class of a·2" 1] ": it has two nodes, each with its cost, and under AST size they tie. " [:cost :prefer-shift "Charge additions and multiplications"] " and the shift is cheaper in both classes, so the best term reads " [:notation [:+ [:<< :a 1] [:<< :b 1]]] ". Then " [:cost :ast-size "count nodes"] " and the two forms tie again, so which one the page shows is an accident of order. Both forms stay in the graph."]
    [:p "Edit the rules. Write one per line as " [:code "name: pattern → replacement"] ", or write them as " [:native '["name" [:* ?x 2] [:<< ?x 1]]] " triples. A variable on the right must appear on the left. " [:alternative "egg's rules on 0 + 1·a, one iteration" "Try egg's five rules on 0 + 1·a"] ": only the two commutations fire. " [:native '[:+ ?a 0]] " needs the 0 on the right, and the commutation is what adds that form. Lesson 5 runs the same rules to the end."]]})

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
   [[:p "Saturation runs the rules again and again until no application changes the graph. This is equality saturation" [:cite :tate-2009] ". Each iteration searches every rule against the same snapshot, applies every match, and then rebuilds. The table below has a row for each iteration. A row shows how many matches each rule found, how many applications merged something, the counts and the time. Click a row to scrub to it. An iteration that applied nothing is the sign to stop."]
    [:p "This run uses egg's " [:b "backoff"] " scheduler" [:cite :willsey-2021] ". A rule whose matches exceed its budget is banned for a few iterations, and its budget and its ban double each time that happens. The run is declared saturated only when an iteration applied nothing " [:i "and"] " no rule is banned. Otherwise the bans are lifted and the run goes on. That is why the fourteen iterations end with a quiet one, and why the stop reason says saturated and not a limit. " [:alternative "the same, every match every time" "Apply every match every time"] " and the run takes six iterations to reach the same fifteen classes and fifty-four nodes."]
    [:p "The other stop reasons are limits on iterations, on nodes and on time. " [:alternative "five atoms under a node limit of 100" "Five atoms under a node limit of 100"] " stops at the node limit on purpose, and the step tile says so. " [:alternative "egg's rules on 0 + 1·a" "Egg's rules on 0 + 1·a"] " saturate in three iterations, and the best term is " [:notation :a] "."]]})

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
   [[:p "After saturation the class of the input holds three forms: " [:notation [:+ :a :a]] ", " [:notation [:* :a 2]] " and " [:notation [:<< :a 1]] ". Which one is the answer? The e-graph does not say. A cost function does, and choosing by a cost function is called extraction" [:cite :tate-2009 :willsey-2021] ". Extraction walks the classes from the bottom up, gives every node its own cost plus the cost of its cheapest children, and picks the cheapest node of each class. " [:select [:+ :a :a] "Open the class"] ": it shows three nodes, each with its cost and the cheapest outlined, and the three terms the class stands for in order of cost."]
    [:p "Now change the cost. " [:cost :prefer-add "Charge multiplications and shifts"] " and the answer is " [:notation [:+ :a :a]] ". If you " [:cost :prefer-mul "charge additions and shifts"] ", the answer is " [:notation [:* :a 2]] ". If you " [:cost :prefer-shift "charge additions and multiplications"] ", it is " [:notation [:<< :a 1]] ". One graph gives three answers. This is the thesis behind " (library :bendix) ": simplification is equality saturation plus taste, and the taste is a cost function. " [:cost :ast-size "AST size"] " is the default in the other lessons. It ties here, because the three forms are the same size, and a tie falls to a fixed but arbitrary order."]
    [:p "The choice is made in every class, so it nests. " [:alternative "(b·2) + (b·2)" "Try (b·2) + (b·2)"] ": the answer is " [:notation [:<< [:<< :b 1] 1]] " under shifts and " [:notation [:+ [:+ :b :b] [:+ :b :b]]] " under additions, and the class of the input stands for fifteen terms."]]})

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
    [:p "Two rules say that the order and the grouping of a sum do not matter. Commutativity is " [:native '[:+ ?a ?b]] " → " [:native '[:+ ?b ?a]] ". Associativity is " [:native '[:+ [:+ ?a ?b] ?c]] " → " [:native '[:+ ?a [:+ ?b ?c]]] ". Under these rules every arrangement of the sum equals every other arrangement, and an e-graph has to hold them all."]
    [:p "Watch the counters as the iterations run, or scrub. There are 19 nodes after " [:step 1 "the first iteration"] ", then 45, 98 and 162, and 187 after " [:step 5 "the fifth"] ". There are two fewer after the sixth, because the last merges fold nodes together. The rules stop when there is nothing left to add. Every non-empty subset of the five atoms has become a class, which makes 2⁵ − 1 = 31 classes. Every way of splitting a subset in two has become a node, which makes 3⁵ − 2⁶ + 1 = 180 nodes, plus the five atoms. " [:select (sum-of 5) "Open the input's class"] ": it has thirty nodes and stands for 1680 terms. These are all the arrangements of the sum, and each costs nine. The graph grows as three to the power n. " [:alternative "six atoms" "Six atoms"] " climb past six hundred nodes for 30240 arrangements, and nine atoms would need eighteen thousand nodes."]
    [:p "This is the blowup. Commutativity and associativity are the two rules that a computer algebra system cannot do without, and they are the two that an e-graph cannot afford. Deciding equality modulo both is a solved problem" [:cite :bachmair-2000] ", but a rewriting engine has to hold the forms. The usual answer is the backoff of lesson 5" [:cite :willsey-2021] ", which bans the rule that fires too often. " [:alternative "six atoms under a node limit of 500" "Six atoms under a node limit of 500"] " stop at the limit with the sum unfinished. Lesson 8 is the fix."]]})

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
   [[:p "The e-graph is a value. Asserting something in it does not change it. It makes a new e-graph, and the old one is still there. Take " [:notation [:+ [:* :x :x] [:* 2 :x]]] " and ask what would follow if " [:notation :x] " were 2. Union the classes of " [:notation :x] " and 2 in a copy, which leaves a " [:step 1 "rebuild pending"] ". " [:select :x "Open the class of x" 1] ": it holds x and 2. Two products point at it. They now read the same, as that class times itself, but they are in two classes. " [:step 2 "Rebuild"] " merges them by congruence, and the sum becomes the sum of a class with itself. Now " [:select [:+ [:* :x :x] [:* 2 :x]] "the input's class" 2] " stands for sixteen terms, from " [:notation [:+ [:* :x :x] [:* 2 :x]]] " to " [:notation [:+ [:* 2 2] [:* 2 2]]] ". They are all the same size, so AST size cannot choose between them."]
    [:p "The original has not moved. It is step 0, beside the copy. A mutable e-graph needs an undo log or a deep copy to do this. Here a fork is a " [:native 'let] ". The e-graph is persistent" [:cite :driscoll-1989] " because it is made of Clojure's collections, and they are persistent" [:cite :bagwell-2001 :hickey-2020] ". " [:alternative "x·y + y·x, what if y = x" "What if y were x"] ": the two products become one node, and the sum becomes a class added to itself."]]})

;; ---------------------------------------------------------------------------
;; bendix: the polynomial analysis, a rule over it, differentiation

(def s2 [:expt [:sin :x] 2])
(def c2 [:expt [:cos :x] 2])

(def materialize-step
  "The step that every bendix lesson ends with. It writes the normal
  form of each class into the graph as a term, so that extraction
  can choose it."
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
   [[:p "This is the sum of lesson 7, " [:notation (sum-of 5)] ", under the same two rules, with one addition: this e-graph carries the polynomial analysis. Every class computes its value as a polynomial over the atoms. The whole sum is worth " [:notation [:+ :a0 :a1 :a2 :a3 :a4]] ", and its first pair is worth " [:notation [:+ :a0 :a1]] ". Two classes with the same polynomial are merged when the node is added, before any rule sees it. The column on the right shows that polynomial."]
    [:p "Run it. " [:step 1 "One iteration"] " runs, and the rules merge nothing. Commutativity proposes " [:notation [:+ :a1 :a0]] " for the class of " [:notation [:+ :a0 :a1]] ", but the analysis has already put it there, and a union of a class with itself is not a merge. Associativity adds three classes, which are the right-nested pairs, and nothing more. The graph has twelve classes and nineteen nodes, where lesson 7 needed thirty-one and a hundred and eighty-five. The runner stops there, because an iteration that merges nothing means saturation."]
    [:p "The arrangement of the sum no longer matters. " [:step 2 "The last step"] " writes every polynomial into the graph as a term, so the best term under " (library :bendix) "'s cost is " [:notation [:+ :a0 :a1 :a2 :a3 :a4]] ", whatever was typed. " [:select [:+ :a0 :a1 :a2 :a3 :a4] "Open the input's class" 2] ": its cheapest term is the five-way sum that the analysis wrote. The whole class stands for thirty-seven terms, where the input's class in lesson 7 stood for 1680. " [:alternative "another arrangement of the same sum" "Type another arrangement"] " and you get the same polynomial and the same best term. At the REPL, " [:native '(second (eg/add g [:+ :a4 [:+ :a3 [:+ :a2 [:+ :a1 :a0]]]]))] " returns the class of the input, because the arrangement was already there."]
    [:p "This is the fix. Commutativity, associativity, distributivity and cancellation are not rules here. They are a decision procedure inside every class, a polynomial normal form, and the blowup never starts. The class carries it as an e-class analysis" [:cite :willsey-2021] ", which is a decision procedure that cooperates with congruence closure" [:cite :nelson-oppen-1979] ", which makes this an e-graph modulo a theory" [:cite :zucker-2025] ". " [:alternative "six atoms" "Six atoms"] " needed six hundred nodes in lesson 7, and here they take twenty-eight."]]})

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
   [[:p [:notation [:+ s2 c2]] " = 1 is not a ring identity. The ring sees " [:notation [:sin :x]] " and " [:notation [:cos :x]] " as two atoms that it knows nothing about, so the class of " [:notation [:+ [:+ [:+ :a s2] c2] :b]] " is worth sin²x + cos²x + a + b and nothing less. A pattern rule would need the two squares to be side by side, and here they are not."]
    [:p "The pythagoras rule reads the polynomial instead of the nodes" [:cite :zucker-2025] ". It looks at every class whose polynomial mentions a sine and a cosine of the same argument. It reduces that polynomial modulo sin²x = 1 − cos²x and modulo cos²x = 1 − sin²x. Where the result differs, the rule proposes the result as another form of the class. Here that form is a + b + 1. The runner adds the form as a term and unions it in, the analysis keeps the smaller polynomial, and classes that share a polynomial merge. " [:step 1 "Iteration 1"] " proposes a form for five classes and " [:step 2 "iteration 2"] " for two more. The third iteration finds nothing."]
    [:p "The arrangement never mattered, because the rule never looked at it. " [:step 4 "The last step"] " writes the polynomials in as terms, and the best term under " (library :bendix) "'s cost is " [:notation [:+ :a :b 1]] ". " [:select [:expt [:sin :x] 2] "Open the class of sin²x" 4] ": it holds a second form, which is 1 plus a class that is both −1·cos²x and sin²x − 1. So the class reaches itself and stands for infinitely many terms, and the panel says so. " [:alternative "1 − cos²x" "Try 1 − cos²x"] ": the sine does not exist in the graph yet, and the proposal of the rule creates it."]]})

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
   [[:p "A derivative is a term like any other. " [:notation [:D [:sin [:* 2 :x]] :x]] " is a node with two children, and differentiating is saturating under rules" [:cite :willsey-2021] ". The ring part is not a rule at all. When a class is worth a polynomial, its derivative is computed from the polynomial, so linearity, the product rule and the power rule are polynomial calculus. The chain rule is one pattern rule for each operator, such as " [:native '[:D [:sin ?u] ?x]] " → " [:native '[:* [:cos ?u] [:D ?u ?x]]] "."]
    [:p [:step 1 "Iteration 1"] ": d-sin fires, and the class of the derivative gains " [:notation [:* [:cos [:* 2 :x]] [:D [:* 2 :x] :x]]] ". " [:step 2 "Iteration 2"] ": the ring differentiates 2·x to 2, so that product is worth 2·cos(2·x). The third iteration finds nothing, and " [:step 4 "the last step"] " writes the normal forms in. " [:select [:D [:sin [:* 2 :x]] :x] "Open the derivative's class" 4] ": it holds the derivative node and the two products it equals, each with its cost. The derivative node is the most expensive under no-D."]
    [:p "The cost decides which term is the answer. " (library :bendix "Bendix") "'s default cost charges a derivative node no more than a sine. So for " [:alternative "sin(sin(sin x))" "sin(sin(sin x))"] " under " [:cost :bendix "bendix's default"] ", the answer keeps the derivative unevaluated, because the node is cheaper than the product of three cosines. The " [:cost :no-D "no-D cost"] " counts what is still under a derivative before it counts size, so a spelling without a derivative wins whenever one exists. When none exists, as for " [:alternative "x·|x|: no rule for abs" "x·|x|"] ", the D stays and the answer says so."]]})

;; ---------------------------------------------------------------------------
;; the page after the lessons: the REPL page

(def repl
  {:key :repl :nav "The REPL" :title "The REPL" :needs :cromulent :kind :embiggen
   :operation :saturate
   :dock :open
   :inputs []
   :values {:term [:+ :a :a]
            :rules '[["double" [:+ ?x ?x] [:* ?x 2]]
                     ["shift" [:* ?x 2] [:<< ?x 1]]]}
   :opts {:scheduler :simple}
   :costs [:ast-size :prefer-add :prefer-mul :prefer-shift :no-shift :bendix :no-D]
   :panels #{:graph :tree :matches :stats}
   :prose
   [[:p "Every panel on this site shows a value that a Clojure program made. This page is the REPL for that program. The REPL is the dock along the bottom of every page, and on this page it starts open. " [:code "g"] " is the e-graph on show, and " [:code "timeline"] " holds every step of its run: " [:eval "(eg/class-count g)"] ", " [:eval "(count timeline)"] ". Click a line of code to evaluate it, or type code on the REPL's line and press Enter. The up arrow brings back what you evaluated before, and " [:code "*1"] " is the last value. The open dock also has a buffer for longer code, which nothing clears. In the buffer, Ctrl-Enter evaluates the form at the caret, and Ctrl-Shift-Enter evaluates every form. The button beside an entry of the history copies its code into the buffer. The engine is loaded under short names, and the ? button at the end of the dock lists them with the keys. " [:eval "(doc eg/union)"] " prints the docstring of a function."]
    [:p "An e-graph is a value" [:cite :hickey-2020] ", so nothing you evaluate here changes " [:code "g"] ". " [:eval "(eg/add g [:+ :b :b])"] " returns a pair: a new e-graph, and the class of the term in it. The history prints the e-graph as its class list, with that class marked. " [:eval "(eg/class-count g)"] " is still what it was. To see a value in every panel, put it on show. Use the button beside the value, or evaluate " [:eval "(show! (eg/add g [:+ :b :b]))"] "."]
    [:p [:code "push!"] " makes an e-graph the next step of the run on show. This lets you build a run by hand and scrub it like the run of a lesson. To say that " [:notation :b] " equals " [:notation :a] ", evaluate "
     [:eval "(let [[g a] (eg/add g :a)\n      [g b] (eg/add g :b)]\n  (push! (first (eg/union g a b)) \"a = b, rebuild pending\"))"]
     " Then evaluate " [:eval "(push! (eg/rebuild g) \"rebuilt\")"] ". The class of " [:notation [:+ :b :b]] " has now joined the class of " [:notation [:+ :a :a]] ", although nobody asserted that. This is the congruence of lesson 3, done by hand."]
    [:p "The page is a value too. It is held in an atom called " [:code "state"] ". " [:eval "(:step @state)"] " is the step on show, and scrubbing is a swap: " [:eval "(swap! state wb/scrub 0)"] ". " [:code "wb"] " holds the steps that the buttons of the page take. " [:code "show!"] " and " [:code "push!"] " are two of those steps with the swap already written in. Anything else is a key in the state: " [:eval "(swap! state assoc :cost :prefer-shift)"] ". The atom refuses a value that the page could not draw, and it says why: " [:eval "(swap! state assoc :step 99)"] "."]
    [:p "A run is a value as well. Equality saturation" [:cite :tate-2009 :willsey-2021] " is one call: " [:eval "(rw/saturate (first (eg/add (eg/egraph) [:+ :a :b]))\n             (lessons/rules-of lessons/ac-rules)\n             {:timeline? true})"] " The button beside the result puts its timeline on show. The next call hands the page a run that has not run yet, and the page steps it as it steps the run of a lesson: " [:eval "(show! (run/start [:+ [:+ :a :b] :c]\n                  (lessons/rules-of lessons/ac-rules)\n                  {}))"] " " (library :bendix) " is loaded beside " (library :cromulent) ": " [:eval "(bx/simplify [:+ [:* 2 :x] [:* 3 :x]])"] ". An e-graph that carries the analysis of bendix shows the polynomial of each class in the list, as in lesson 9: " [:eval "(show! (bx/saturate [:+ [:expt [:sin :x] 2] [:expt [:cos :x] 2]]\n                     {:rules rules/trig :timeline? true}))"]]
    [:p "Type terms in the native spelling, such as " [:native [:+ [:* 2 :x] :y]] ". " [:eval "(input/read-term \"2·x + y\")"] " reads the notation. The code you type here is interpreted, but every function it calls is the compiled function that the page runs, so the engine is as fast as it is in a lesson. There is no interrupt. An evaluation that never ends takes the tab with it."]]})

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
   differentiation
   repl])

(defn by-key [k] (some (fn [l] (when (= k (:key l)) l)) all))

(def start
  "The key of the page that opens when the address names no lesson.
  This is the basics."
  :basics)

(defn heading
  "Returns the heading of a lesson: its number and its title. A page
  without a number is headed by its title alone."
  [{:keys [n title]}]
  (if n (str n ". " title) title))

(defn nav-label
  "Returns the label of a lesson in the navigation. This is the
  lesson's `:nav` when it has one, and its heading when it has
  not."
  [lesson]
  (or (:nav lesson) (heading lesson)))

(defn address
  "Returns the address of a lesson, which is what follows # in the
  address of the page. This is the number of the lesson. A page
  without a number uses the name of its key."
  [{:keys [n key]}]
  (if n (str n) (name key)))

(defn by-address
  "Returns the lesson at an address, or nil."
  [a]
  (some (fn [l] (when (= a (address l)) l)) all))

(defn live?
  "Returns true when the lesson is live, and false when it is still
  to come."
  [lesson]
  (not= :coming (:status lesson)))

(defn make-run
  "Returns the run for a lesson over its input values. The runner
  options are the lesson's options with `opts` merged over them."
  ([lesson] (make-run lesson (:values lesson) {}))
  ([lesson values opts]
   (case (:kind lesson)
     :embiggen (run/start ((or (:build lesson) :term) values) (rules-of (:rules values)) (merge (:opts lesson) opts))
     :script ((:script lesson) values))))
