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

  Four pages are shaped like lessons and have no number. Two come
  before the lessons: the basics, where the page opens, which
  assumes nothing, and the introduction, which says what an e-graph
  is and why it matters. Two come after the lessons. The REPL page's
  prose is the documentation of the REPL, and its widgets are lines
  of code that the reader evaluates. It opens with the REPL's dock
  open. The cheat sheet is for a reader who knows the subject: it
  has little prose, and it is lists of terms to run and code to
  evaluate.

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
   {:name "union, keeping the original" :fn "eg/union · eg/rebuild"
    :says "Add the term. Then say that lhs equals rhs. The union returns a new e-graph, and the rebuild runs on the new one. The original is a value and does not change."
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
  - `[:math s]` sets math that is not a term, such as a polynomial or
    an equation, in the style of notation. The print mode leaves it
    alone.
  - `[:step k label]` scrubs to step k.
  - `[:select t label]` opens the class of t. `[:select t label k]`
    scrubs to step k first.
  - `[:cost key label]` sets the cost.
  - `[:alternative label text]` chooses one of the alternatives of
    the lesson.
  - `[:print mode label]` switches the print mode.
  - `[:tab key label]` shows a tab of the e-graph column: `:egraph`,
    `:inputs` or `:results`.
  - `[:lesson key label]` goes to another lesson.
  - `[:eval code]` evaluates code at the REPL as if the reader had
    typed it. The code itself is the link.
  - `[:cite key …]` cites works of `reading`. It follows the idea it
    credits, and shows as short author-year links.
  - `[:working op term …]` works one operation of bendix's ring
    through, monomial by monomial (`orrery.working`). It stands
    between paragraphs, not inside one.

  The prose is written in the explorable voice. Each paragraph says
  what is on the page and gives the reader one of these widgets to
  use. It cites a work; it does not tell the story of the work. It
  is written in plain sentences: subject, verb, object."
  #{:notation :native :math :step :select :cost :alternative :print :tab :lesson :eval :cite :working})

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
  asserts an equation, which returns a new e-graph. The third
  rebuilds the new e-graph. The original is step 0 and does not
  change."
  [{:keys [term lhs rhs]}]
  (let [[g id] (eg/add (eg/egraph) term)
        [g' a] (eg/add g lhs)
        [g' b] (eg/add g' rhs)
        [g1 _] (eg/union g' a b)
        g2 (eg/rebuild g1)]
    (run/script [["the original" g]
                 [(str "a new e-graph, with " (notation/term->str lhs) " = " (notation/term->str rhs) " asserted; rebuild pending") g1]
                 ["the new e-graph, after rebuild" g2]]
                id)))

;; ---------------------------------------------------------------------------
;; what the site is

(def about
  "Says what the site is. It has no widgets. The page shows it above
  the page the site opens on, before any paragraph that links into
  the running widgets. The wording is the author's (IDEA.md section
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
   :panels #{}
   :prose
   [[:p [:notation [:* [:+ :x 0] 1]] " is a long way to write " [:notation :x] ". Two rules say why: " [:notation '[:+ ?a 0]] " → " [:notation '?a] " and " [:notation '[:* ?a 1]] " → " [:notation '?a] ", where " [:notation '?a] " matches any subexpression. Simplifying applies rules like these to find a shorter form."]
    [:p "A conventional simplifier rewrites in place. The short form replaces the long one, and the long one is gone. An e-graph" [:cite :nelson-1981] " keeps both. It stores each subexpression as an " [:i "e-node"] " and groups equal e-nodes into an " [:i "e-class"] ", an equivalence class, drawn as a box."]
    [:p [:step 0 "At the start"] " each of the five e-nodes is alone in its own e-class. An e-node's children are e-classes, not subexpressions, so " [:code "#n"] " points at box " [:math "n"] ". " [:step 1 "Run the rules once"] " and three e-classes merge. " [:select [:* [:+ :x 0] 1] "Open it" 1] ": " [:notation :x] ", " [:notation [:+ :x 0]] " and " [:notation [:* [:+ :x 0] 1]] " are now one e-class. It represents infinitely many expressions, because " [:notation [:+ [:+ :x 0] 0]] " is " [:notation :x] " too, and so on."]
    [:p "Applying the rules until they add nothing is " [:i "saturation"] [:cite :tate-2009] ", and " [:step 2 "the second pass"] " adds nothing. Then " [:i "extraction"] " takes the smallest form from the input's class, " [:notation :x] ". That is the whole idea: collect every form, group the equal ones, and choose at the end."]
    [:p [:alternative "nothing to do: x·y" "Give it x·y"] " and no rule applies. " [:alternative "two letters: (x + 0)·(y·1)" "Give it two letters"] " and both simplify, or " [:alternative "(x + 0)·1" "put (x + 0)·1 back"] ". The term field takes any expression. " [:lesson :intro "The next page"] " shows what keeping every form buys."]]})

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
   :panels #{}
   :prose
   [[:p "A conventional rewriter replaces one form with another, and each rewrite commits it. In " [:notation [:/ [:* :a 2] 2]] " the product can become a shift, " [:notation [:<< :a 1]] ", which is cheaper on a machine, or the twos can cancel to leave " [:notation :a] ". Shift first and you hold " [:notation [:/ [:<< :a 1] 2]] ", where nothing cancels: " [:alternative "what shifting first leaves: (a << 1)/2" "start from there"] " and the same rules find nothing to do. No rule order is best for every term. This is the " [:i "phase-ordering problem"] [:cite :tate-2009] "."]
    [:p "An e-graph" [:cite :nelson-1981] " avoids the choice. " [:alternative "(a·2)/2" "Give it (a·2)/2"] ". " [:step 0 "At the input"] " there are four e-classes of one e-node each, because the two 2s share an e-node. A rule adds its result to the e-class it matched and removes nothing, so " [:step 1 "after one iteration"] " the " [:select [:* :a 2] "class of a·2" 1] " holds the shift beside the product, and the product is still there for the next rule."]
    [:p "Saturation" [:cite :tate-2009 :willsey-2021] " continues from there. " [:step 2 "The twos cancel"] ", then " [:step 3 "a times one is a"] ", which merges the input's e-class with the e-class of " [:notation :a] ". " [:step 4 "The fourth iteration"] " adds nothing. Since nothing was discarded, the order in which rules fired never mattered. A cost function ranks the terms each e-class represents, and extraction picks the cheapest, " [:notation :a] "."]
    [:p "So an e-graph rewrites without regret, separates searching for an answer from choosing one, and packs many terms into few e-nodes. " [:select [:/ [:* :a 2] 2] "The input's class" 4] " represents infinitely many terms: " [:notation :a] " equals " [:notation [:/ [:* :a 2] 2]] ", and the " [:notation :a] " inside that equals " [:notation [:/ [:* :a 2] 2]] " again. E-graphs are used in theorem provers" [:cite :detlefs-nelson-saxe-2005 :de-moura-bjorner-2007] ", optimizing compilers" [:cite :tate-2009] " and tools that make floating-point arithmetic more accurate" [:cite :panchekha-2015] "."]
    [:p "The lessons take this page apart: " [:lesson :tree "a term as a tree"] ", " [:lesson :sharing "sharing"] ", " [:lesson :congruence "equality"] ", " [:lesson :rule "a rule"] ", " [:lesson :saturation "saturation"] " and " [:lesson :taste "the choice of an answer"] "; then " [:lesson :blowup "what holding everything costs"] ", " [:lesson :fix "the fix"] ", " [:lesson :normal-form "what is inside it"] " and " [:lesson :polynomial-rule "a rule over it"] "; then " [:lesson :what-if "a what-if"] " and " [:lesson :differentiation "differentiation"] ". Every panel is the engine running, not a drawing of it. Scrub the steps, click a box, or edit the term and the rules."]]})

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
   [[:p [:notation [:+ [:* 2 :x] :y]] " is a tree: an addition at the root, a product and " [:notation :y] " beneath it, and 2 and " [:notation :x] " beneath the product. The tree panel draws it. Its native spelling is " [:native [:+ [:* 2 :x] :y]] ". A vector is a node whose first element is the operator and whose other elements are its children. A keyword is a variable and a number is a number. " [:print :native "Switch the page to native"] " to print every panel that way, and " [:print :notation "switch back"] " for notation."]
    [:p "An e-graph" [:cite :nelson-1981] " takes a term in from the bottom up. Each subterm becomes an e-node in an e-class of its own, since nothing is known to be equal yet. That makes five e-nodes, five e-classes and one row of the list per e-class. Hover a node of the tree to light up its row. The list writes an e-node's children as e-class ids, not subterms, because an e-class is a set of terms and an e-node points at sets."]
    [:p "Click a node of the tree or a row of the list to open its e-class. The panel shows its e-nodes, how many terms it represents, its children and its parents. " [:select [:* 2 :x] "Open the product"] ": one e-node, one term, two e-classes beneath it and one above. Then type any term, in either spelling, and add it. Every other lesson starts from this step."]]})

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
   [[:p [:notation [:* [:+ :x 1] [:+ :x 1]]] " has seven tree nodes, but the counter shows four e-nodes. The two " [:notation [:+ :x 1]] " subtrees are the same term, and an e-graph stores each distinct e-node once. Before it adds an e-node, it looks the e-node up in a table keyed by the e-node itself. If the e-node is there, the lookup returns its e-class and nothing new is made. That table is the " [:i "hashcons"] [:cite :ershov-1958 :goto-1974] "."]
    [:p "Hover either " [:notation [:+ :x 1]] " in the tree. Both light up, and so does one row of the list. " [:select [:+ :x 1] "Open that class"] ": it has one e-node and one parent. " [:select [:* [:+ :x 1] [:+ :x 1]] "Open the product"] ": its one e-node points at that e-class twice."]
    [:p "Sharing lets an e-graph hold every arrangement of a sum in far less room than a list of them would take. " [:lesson :blowup "Lesson 7"] " holds the 1680 arrangements of five atoms in 185 e-nodes. Even shared, that count grows as " [:math "3ⁿ"] " in the number of atoms, and " [:lesson :fix "lesson 8"] " is the fix."]]})

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
   [[:p "Add " [:notation [:/ [:* :a 2] 2]] " and " [:notation [:/ [:<< :a 1] 2]] " to get " [:step 0 "seven e-classes"] ", one per distinct subterm. Now assert that " [:notation [:* :a 2]] " equals " [:notation [:<< :a 1]] ". " [:i "Union"] " merges their two e-classes and leaves a " [:step 1 "rebuild pending"] ". Nothing else moves. " [:select [:* :a 2] "Open the merged class" 1] ": two e-nodes point at it. Both read as this e-class divided by 2, but they sit in different e-classes. That breaks the e-graph's invariant: the children are equal and the parents are not."]
    [:p [:i "Rebuild"] " restores the invariant. It re-canonicalizes every e-node whose child moved, finds that the two quotients are now identical, and merges their e-classes " [:step 2 "without being told"] ". That leaves five e-classes, and " [:select [:* :a 2] "the merged class" 2] " has one parent. This is " [:i "congruence closure"] [:cite :nelson-oppen-1980 :downey-sethi-tarjan-1980] ", the core of an e-graph: state one equality, and every consequence that follows from the structure of the terms comes for free. The example is from egg's README" [:cite :willsey-2021] ", and the pending step is egg's deferred rebuild."]
    [:p "Change the sides, or the term over them, where " [:native '?x] " stands for the side. " [:alternative "x + 0 = x, under a sine" "Try x + 0 = x under a sine"] ": the two sines merge the same way. With " [:alternative "deeper: (?x + 1)·(?x + 1)" "a deeper term over each side"] ", two levels collapse at once."]]})

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
   [[:p "A rule is a pattern and a replacement. " [:native '[:* ?x 2]] " → " [:native '[:<< ?x 1]] " says that multiplying by two is shifting left by one. Running a rule has two phases. " [:b "Search"] " matches the pattern against the e-graph, which is called " [:i "e-matching"] [:cite :detlefs-nelson-saxe-2005 :de-moura-bjorner-2007] ". Every e-class that holds an e-node of the pattern's shape is a match, and " [:native '?x] " is bound to the e-class of that e-node's child. " [:step 0 "At the input"] " the matches panel lists two matches, " [:notation [:* :a 2]] " and " [:notation [:* :b 2]] "."]
    [:p [:b "Apply"] " builds the replacement under each match's bindings and unions it with the matched e-class. " [:step 1 "Iteration 1"] " adds three e-nodes: the number 1, which the e-graph did not have, and a shift in each matched e-class beside the product it equals. Nothing is rewritten. The new forms join the old ones. " [:select [:* :a 2] "Open the class of a·2" 1] ": its two e-nodes tie under AST size. " [:cost :prefer-shift "Charge additions and multiplications"] " and the shift wins in both e-classes, which gives " [:notation [:+ [:<< :a 1] [:<< :b 1]]] ". " [:cost :ast-size "Count nodes"] " again and the forms tie, so the page shows whichever comes first in a fixed order. Both forms stay in the e-graph."]
    [:p "Edit the rules, one per line as " [:code "name: pattern → replacement"] " or as " [:native '["name" [:* ?x 2] [:<< ?x 1]]] " triples. Every variable on the right must appear on the left. " [:alternative "egg's rules on 0 + 1·a, one iteration" "Try egg's five rules on 0 + 1·a"] ": only the two commutations fire. " [:native '[:+ ?a 0]] " needs the 0 on the right, and commutation is what adds that form. " [:lesson :saturation "Lesson 5"] " runs the same rules to the end."]]})

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
   [[:p [:i "Saturation"] " repeats the rules until an iteration changes nothing" [:cite :tate-2009] ". Each iteration searches every rule against the same snapshot, applies every match, and then rebuilds. " [:tab :results "The iterations table"] " has a row per iteration: the matches each rule found, the applications that merged something, the counts and the time. Click a row to scrub to it."]
    [:p "This run uses egg's " [:b "backoff"] " scheduler" [:cite :willsey-2021] ". A rule whose matches exceed its budget is banned for a few iterations, and each ban doubles both the budget and the length of the next ban. The run counts as saturated only when an iteration applies nothing " [:i "and"] " no rule is banned. Otherwise the bans lift and the run continues. That is why the fourteen iterations end with a quiet one, and why the stop reason is saturated and not a limit. " [:alternative "the same, every match every time" "Apply every match every time"] " and the run reaches the same fifteen e-classes and fifty-four e-nodes in six iterations."]
    [:p "A run can also stop at a limit on iterations, e-nodes or time. " [:alternative "five atoms under a node limit of 100" "Five atoms under a node limit of 100"] " stops at the node limit on purpose, and the step tile says so. " [:alternative "egg's rules on 0 + 1·a" "Egg's rules on 0 + 1·a"] " saturate in three iterations, and the best term is " [:notation :a] "."]]})

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
   [[:p "After saturation the input's e-class holds three forms: " [:notation [:+ :a :a]] ", " [:notation [:* :a 2]] " and " [:notation [:<< :a 1]] ". The e-graph does not say which one is the answer. A cost function does, and choosing by cost is " [:i "extraction"] [:cite :tate-2009 :willsey-2021] ". Extraction walks the e-classes from the bottom up. It gives each e-node its own cost plus the cost of its cheapest children, and it keeps the cheapest e-node of each e-class. " [:select [:+ :a :a] "Open the class"] ": it shows three e-nodes with their costs, the cheapest outlined, and the terms the e-class represents in order of cost."]
    [:p [:cost :prefer-add "Charge multiplications and shifts"] " and the answer is " [:notation [:+ :a :a]] ". " [:cost :prefer-mul "Charge additions and shifts"] " and it is " [:notation [:* :a 2]] ". " [:cost :prefer-shift "Charge additions and multiplications"] " and it is " [:notation [:<< :a 1]] ". One e-graph gives three answers. This is the thesis behind " (library :bendix) ": simplification is equality saturation plus taste, and the taste is a cost function. " [:cost :ast-size "AST size"] ", the default in the other lessons, ties here because the three forms are the same size, and a tie falls to a fixed but arbitrary order."]
    [:p "The choice is made in every e-class, so it nests. " [:alternative "(b·2) + (b·2)" "Try (b·2) + (b·2)"] ": the answer is " [:notation [:<< [:<< :b 1] 1]] " under shifts and " [:notation [:+ [:+ :b :b] [:+ :b :b]]] " under additions, and the input's e-class represents fifteen terms."]]})

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
   [[:p "A sum of five atoms, " [:notation (sum-of 5)] ", is one term of nine nodes: five atoms and four additions. The e-graph starts at " [:step 0 "nine e-classes and nine e-nodes"] ". Two rules say that the order and the grouping of a sum do not matter: commutativity, " [:native '[:+ ?a ?b]] " → " [:native '[:+ ?b ?a]] ", and associativity, " [:native '[:+ [:+ ?a ?b] ?c]] " → " [:native '[:+ ?a [:+ ?b ?c]]] ". Under these rules every arrangement of the sum equals every other, so the e-graph has to hold them all."]
    [:p "Scrub the steps and watch the counts in the replay bar. There are 19 e-nodes after " [:step 1 "the first iteration"] ", then 45, 98 and 162, and 187 after " [:step 5 "the fifth"] ". The sixth leaves two fewer, because its merges fold e-nodes together, and the seventh adds nothing. Every non-empty subset of the atoms is now an e-class, which makes " [:math "2⁵ − 1 = 31"] ". Every way of splitting a subset in two is an e-node, which makes " [:math "3⁵ − 2⁶ + 1 = 180"] ", plus the five atoms. " [:select (sum-of 5) "Open the input's class"] ": thirty e-nodes represent 1680 terms, every arrangement of the sum, each of cost 9. The e-graph grows as " [:math "3ⁿ"] ". " [:alternative "six atoms" "Six atoms"] " pass six hundred e-nodes for 30240 arrangements, and nine atoms would need eighteen thousand."]
    [:p "This is the blowup. A computer algebra system cannot do without commutativity and associativity, and an e-graph cannot afford them. Deciding equality modulo both is a solved problem" [:cite :bachmair-2000] ", but a rewriting engine has to hold the forms. The usual answer is the backoff of " [:lesson :saturation "lesson 5"] [:cite :willsey-2021] ", which bans a rule that fires too often. " [:alternative "six atoms under a node limit of 500" "Six atoms under a node limit of 500"] " stop at the limit with the sum unfinished. " [:lesson :fix "Lesson 8"] " is the fix."]]})

(def what-if
  {:key :what-if :n 11 :title "What if" :needs :cromulent :kind :script
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
   [[:p "Sometimes you want to know what would follow from an equation without committing to it. Take " [:notation [:+ [:* :x :x] [:* 2 :x]]] " and ask what would follow if " [:notation :x] " were 2. The run unions the e-classes of " [:notation :x] " and 2. The union leaves the e-graph it was given alone and returns a new one, with a " [:step 1 "rebuild pending"] ". " [:select :x "Open the class of x" 1] ": it holds " [:notation :x] " and 2, and two products point at it. Both products now read as that e-class times itself, but they sit in two e-classes. " [:step 2 "Rebuild"] " merges them by congruence, as in " [:lesson :congruence "lesson 3"] ", and the sum becomes that e-class added to itself. Now " [:select [:+ [:* :x :x] [:* 2 :x]] "the input's class" 2] " represents sixteen terms, from " [:notation [:+ [:* :x :x] [:* 2 :x]]] " to " [:notation [:+ [:* 2 2] [:* 2 2]]] ". They are all the same size, so AST size cannot choose between them."]
    [:p "The original has not changed. It is step 0, and the page shows it beside the new e-graph. This is a property of " (library :cromulent) ", not of e-graphs in general. Most e-graphs are mutable, egg's among them" [:cite :willsey-2021] ", so a union changes the e-graph in place, and asking what if takes a deep copy or an undo log. In cromulent the e-graph is an immutable value. A union returns a new e-graph and leaves the old one as it was, so a fork is only a " [:native 'let] " that names the new e-graph. The two e-graphs share every part that the union did not touch, which makes the structure " [:i "persistent"] [:cite :driscoll-1989] ". Cromulent gets persistence from the collections of Clojure, which are persistent themselves" [:cite :bagwell-2001 :hickey-2020] "."]
    [:p "Change the term or the equation. " [:alternative "x·y + y·x, what if y = x" "What if y were x"] ": the two products become one e-node, and the sum becomes an e-class added to itself. " [:alternative "sin x + sin y, what if x = y" "What if x were y, under sines"] ": the two sines merge the same way."]]})

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
   [[:p "This is the sum of " [:lesson :blowup "lesson 7"] ", " [:notation (sum-of 5)] ", under the same two rules, with one difference: this e-graph carries the " [:i "polynomial analysis"] ". Every e-class computes its value as a polynomial over the atoms. The whole sum is worth " [:notation [:+ :a0 :a1 :a2 :a3 :a4]] ", and its first pair is worth " [:notation [:+ :a0 :a1]] ". When an e-node is added, its e-class merges with any e-class of the same polynomial, before any rule sees it. The class list shows each polynomial in a column of its own."]
    [:p "Run it. In " [:step 1 "one iteration"] " the rules merge nothing. Commutativity proposes " [:notation [:+ :a1 :a0]] " for the e-class of " [:notation [:+ :a0 :a1]] ", but the analysis has already put it there, and a union of an e-class with itself is not a merge. Associativity adds three e-classes, the right-nested pairs, and nothing more. That makes twelve e-classes and nineteen e-nodes, where lesson 7 needed 31 and 185. An iteration that merges nothing is saturation, so the run stops."]
    [:p "The arrangement no longer matters. " [:step 2 "The last step"] " writes every polynomial into the e-graph as a term, so the best term under " (library :bendix) "'s cost is " [:notation [:+ :a0 :a1 :a2 :a3 :a4]] ", whatever was typed. " [:select [:+ :a0 :a1 :a2 :a3 :a4] "Open the input's class" 2] ": its cheapest term is that five-way sum, and it represents 37 terms, where the input's e-class in lesson 7 represented 1680. " [:alternative "another arrangement of the same sum" "Type another arrangement"] " and you get the same polynomial and the same answer. At the last step, " [:eval "(second (eg/add g [:+ :a4 [:+ :a3 [:+ :a2 [:+ :a1 :a0]]]]))"] " returns the input's e-class, because that arrangement is already there."]
    [:p "This is the fix. Commutativity, associativity, distributivity and cancellation stop being rules. They become a decision procedure inside every e-class, a polynomial normal form, and the blowup never starts. The e-graph carries it as an " [:i "e-class analysis"] [:cite :willsey-2021] ". A decision procedure that cooperates with congruence closure" [:cite :nelson-oppen-1979] " makes this an e-graph modulo a theory" [:cite :zucker-2025] ". " [:alternative "six atoms" "Six atoms"] " take twenty-eight e-nodes here, against six hundred in lesson 7. " [:lesson :normal-form "Lesson 9"] " opens the polynomial up and says what the fix costs."]]})

(def inside-term
  "The term of lesson 9. Two of its subterms are worth x², and the
  whole term is worth 0."
  [:- [:+ [:* [:+ :x 1] [:- :x 1]] 1] [:* :x :x]])

(def normal-form
  {:key :normal-form :n 9 :title "Inside the polynomial" :needs :bendix :kind :embiggen
   :operation :simplify
   :operation-says {:call ["(bx/simplify term"
                           "  {:rules []"
                           "   :cost cost"
                           "   opts})"]}
   :inputs [term-input]
   :values {:term inside-term :rules []}
   :opts bendix-opts
   :costs [:bendix :ast-size]
   :alternatives [{:label "sin(x + y) − sin(y + x)" :values {:term [:- [:sin [:+ :x :y]] [:sin [:+ :y :x]]]}}
                  {:label "(a + b + c + d)²⁰" :values {:term [:expt [:+ :a :b :c :d] 20]}}
                  {:label "x/x" :values {:term [:/ :x :x]}}
                  {:label "(x + y)²" :values {:term [:expt [:+ :x :y] 2]}}
                  {:label "x² + 2·x·y + y²" :values {:term [:+ [:expt :x 2] [:* 2 :x :y] [:expt :y 2]]}}]
   :panels #{:stats}
   :surprise {:draw generate/a-ring-term :wants {:shrink 1 :size 0.5}}
   :prose
   [[:p [:lesson :fix "Lesson 8"] " said that every e-class carries a polynomial. This lesson opens the polynomial up, over one term and no rules: " [:notation inside-term] ". A polynomial is a sum of " [:i "monomials"] ", and a monomial is a number times a product of atoms, each raised to a whole power. So " [:math "x² − 1"] " has two monomials: " [:math "x²"] " with the number 1, and the empty product with the number −1. " (library :bendix) " stores a polynomial as a map from each monomial to its number and drops any monomial whose number is 0. Two equal polynomials are therefore the same map, however their terms were written."]
    [:p "An e-class computes its polynomial from the polynomials of its children, from the bottom up" [:cite :willsey-2021] ". The e-class of " [:notation :x] " is worth " [:math "x"] ", and the e-class of 1 is worth 1. " [:select [:+ :x 1] "The class of x + 1" 0] " adds the two. " [:select [:* [:+ :x 1] [:- :x 1]] "The class of the product" 0] " multiplies " [:math "x + 1"] " by " [:math "x − 1"] " and gets " [:math "x² − 1"] ". Distributivity and cancellation happen here, as arithmetic on maps, and no rule fires. Adding 1 then gives " [:math "x²"] ". Below, each monomial of " [:math "x + 1"] " meets each monomial of " [:math "x − 1"] ", and the two that cancel are struck out."]
    [:working :product [:+ :x 1] [:- :x 1]]
    [:p "The analysis keeps a table from each polynomial to the e-class that is worth it. When a new e-node is worth a polynomial that the table already holds, its e-class merges with the one in the table. " [:select [:* :x :x] "Open the class of x·x" 0] ": it holds " [:notation [:* :x :x]] " and " [:notation [:+ [:* [:+ :x 1] [:- :x 1]] 1]] ", because both are worth " [:math "x²"] ". The merge happened at " [:step 0 "the input"] ", before any rule could run, so the difference is an e-class minus itself, which is worth 0. " [:step 2 "The last step"] " writes every polynomial in as a term, and the answer is 0."]
    [:p "Anything that is not a sum, a difference, a product or a whole power is an atom, and the ring knows nothing about it. " [:alternative "sin(x + y) − sin(y + x)" "Try sin(x + y) − sin(y + x)"] ". The two sums are worth " [:math "x + y"] ", so they merge. The two sines then have the same child, so congruence makes them one e-node, as in " [:lesson :congruence "lesson 3"] ". Congruence and the ring feed each other" [:cite :nelson-oppen-1979] ". The e-class of the sine becomes an unknown of its own, named by its id, and the difference is that unknown minus itself, which is 0."]
    [:p "This is why the fix works. Two polynomials with rational numbers agree for every value of their atoms exactly when they have the same monomials with the same numbers. So the table decides every equation that follows from the laws of a commutative ring: commutativity, associativity, distributivity, the identities and cancellation. The rules of " [:lesson :blowup "lesson 7"] " found those equations only by listing arrangements, and the cost of the table grows with the size of the polynomials instead. It merges two e-classes only when they are equal, so it never asserts anything false. At the REPL, " [:eval "(bx/simplify [:- [:* [:+ :a :b] [:+ :a :b]]\n                 [:+ [:* :a :a] [:* 2 :a :b] [:* :b :b]]])"] " returns 0."]
    [:p "The fix has four costs. " [:b "Size"] ": the normal form is expanded, and " [:alternative "(a + b + c + d)²⁰" "(a + b + c + d)²⁰"] " has 1771 monomials. " (library :bendix) " gives up on an e-class whose polynomial passes 200 monomials and leaves it to the rules, and the column says so. " [:b "Division"] ": " [:alternative "x/x" "x/x"] " is not 1 when " [:math "x"] " is 0, so the ring divides only by numbers other than 0, and " [:notation [:/ :x :x]] " stays " [:notation [:/ :x :x]] "."]
    [:p [:b "Choice"] ": the e-graph now holds only the forms that were typed and the expanded form that the last step writes in, and the cost chooses among them. " [:alternative "(x + y)²" "(x + y)²"] " stays as typed, because it is cheaper than its expansion. But " [:alternative "x² + 2·x·y + y²" "x² + 2·x·y + y²"] " never becomes " [:notation [:expt [:+ :x :y] 2]] ", because the analysis expands and never factors. " [:b "Reach"] ": a pattern rule matches e-nodes, and the arrangements now live only in the polynomial. " [:lesson :polynomial-rule "The next lesson"] " writes a rule that reads the polynomial instead."]]})

(def polynomial-rule
  {:key :polynomial-rule :n 10 :title "A rule over the polynomial" :needs :bendix :kind :embiggen
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
   [[:p [:notation [:+ s2 c2]] " = 1 is not a ring identity. The ring sees " [:notation [:sin :x]] " and " [:notation [:cos :x]] " as two atoms that it knows nothing about, so the e-class of " [:notation [:+ [:+ [:+ :a s2] c2] :b]] " is worth " [:math "sin²x + cos²x + a + b"] " and nothing less. A pattern rule would need the two squares side by side, and here they are not."]
    [:p "The " [:i "pythagoras rule"] " reads the polynomial instead of the e-nodes" [:cite :zucker-2025] ". For every e-class whose polynomial mentions a sine and a cosine of the same argument, it reduces the polynomial modulo " [:math "sin²x = 1 − cos²x"] " and modulo " [:math "cos²x = 1 − sin²x"] ". Where the result differs, the rule proposes it as another form of the e-class, here " [:math "a + b + 1"] ". The runner adds the form as a term and unions it in. The analysis keeps the smaller polynomial, and e-classes that share a polynomial merge. " [:step 1 "Iteration 1"] " proposes forms for five e-classes and " [:step 2 "iteration 2"] " for two more, and " [:step 3 "the third"] " finds nothing. Below is the reduction of the input's polynomial."]
    [:working :reduction [:+ [:+ [:+ :a s2] c2] :b] [:sin :x] [:- 1 c2]]
    [:p "The arrangement never mattered, because the rule never looked at it. " [:step 4 "The last step"] " writes the polynomials in as terms, and the best term under " (library :bendix) "'s cost is " [:notation [:+ :a :b 1]] ". " [:select [:expt [:sin :x] 2] "Open the class of sin²x" 4] ": it holds a second form, 1 plus an e-class that is both " [:math "−1·cos²x"] " and " [:math "sin²x − 1"] ". The two e-nodes make different polynomials, and the panel shows which one the e-class keeps and why. So the e-class reaches itself and represents infinitely many terms. " [:alternative "1 − cos²x" "Try 1 − cos²x"] ": the sine is not in the e-graph yet, and the rule's proposal creates it."]]})

(def differentiation
  {:key :differentiation :n 12 :title "Differentiation is simplification" :needs :bendix :kind :embiggen
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
   [[:p "A derivative is a term like any other. " [:notation [:D [:sin [:* 2 :x]] :x]] " is an e-node with two children, and differentiating is saturating under rules" [:cite :willsey-2021] ". The ring part needs no rule. When an e-class is worth a polynomial, its derivative is computed from the polynomial, so linearity, the product rule and the power rule are polynomial calculus. The chain rule is one pattern rule per operator, such as " [:native '[:D [:sin ?u] ?x]] " → " [:native '[:* [:cos ?u] [:D ?u ?x]]] ". Below, the polynomial of " [:alternative "(x + 1)³" "(x + 1)³"] " is differentiated one monomial at a time."]
    [:working :derivative [:expt [:+ :x 1] 3] :x]
    [:p [:step 1 "Iteration 1"] ": d-sin fires, and the e-class of the derivative gains " [:notation [:* [:cos [:* 2 :x]] [:D [:* 2 :x] :x]]] ". " [:step 2 "Iteration 2"] ": the ring differentiates " [:math "2·x"] " to 2, so that product is worth " [:math "2·cos(2·x)"] ". " [:step 3 "The third iteration"] " finds nothing, and " [:step 4 "the last step"] " writes the normal forms in. " [:select [:D [:sin [:* 2 :x]] :x] "Open the derivative's class" 4] ": it holds the derivative e-node and the two products it equals. Under no-D the derivative is the most expensive of the three."]
    [:p "The cost decides which term is the answer. " (library :bendix "Bendix") "'s default cost charges a derivative e-node no more than a sine. So for " [:alternative "sin(sin(sin x))" "sin(sin(sin x))"] " under " [:cost :bendix "bendix's default"] " the answer keeps the derivative unevaluated, because the e-node is cheaper than the product of three cosines. The " [:cost :no-D "no-D cost"] " counts what is still under a derivative before it counts size, so a form without a derivative wins whenever one exists. When none exists, as for " [:alternative "x·|x|: no rule for abs" "x·|x|"] ", the D stays and the answer says so."]]})

;; ---------------------------------------------------------------------------
;; the pages after the lessons: the REPL page and the cheat sheet

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
   :panels #{:tree :matches :stats}
   :prose
   [[:p "Every panel on this site shows a value that a Clojure program made, and this page is the REPL for that program. The REPL is the dock along the bottom of every page, and on this page it starts open. " [:code "g"] " is the e-graph on show, and " [:code "timeline"] " holds every step of its run: " [:eval "(eg/class-count g)"] ", " [:eval "(count timeline)"] ". Click a line of code to evaluate it, or type code on the REPL's line and press Enter. The up arrow brings back earlier input, and " [:code "*1"] " is the last value. For longer code the open dock has a buffer, which nothing clears. In the buffer, Ctrl-Enter evaluates the form at the caret, and Ctrl-Shift-Enter evaluates every form. The button beside an entry of the history copies its code into the buffer. The engine is loaded under short names, the ? button at the end of the dock lists them with the keys, and " [:eval "(doc eg/union)"] " prints the docstring of a function."]
    [:p "An e-graph is a value" [:cite :hickey-2020] ", so nothing you evaluate here changes " [:code "g"] ". " [:eval "(eg/add g [:+ :b :b])"] " returns a pair: a new e-graph and the e-class of the term in it. The history prints the e-graph as its class list, with that e-class marked, and " [:eval "(eg/class-count g)"] " is still what it was. To see a value in every panel, put it on show with the button beside it, or evaluate " [:eval "(show! (eg/add g [:+ :b :b]))"] "."]
    [:p [:code "push!"] " makes an e-graph the next step of the run on show, so you can build a run by hand and scrub it like the run of a lesson. To assert that " [:notation :b] " equals " [:notation :a] ", evaluate "
     [:eval "(let [[g a] (eg/add g :a)\n      [g b] (eg/add g :b)]\n  (push! (first (eg/union g a b)) \"a = b, rebuild pending\"))"]
     " Then evaluate " [:eval "(push! (eg/rebuild g) \"rebuilt\")"] ". The e-class of " [:notation [:+ :b :b]] " has joined the e-class of " [:notation [:+ :a :a]] ", although nobody asserted that. This is the congruence of " [:lesson :congruence "lesson 3"] ", done by hand."]
    [:p "The page is a value too, held in an atom called " [:code "state"] ". " [:eval "(:step @state)"] " is the step on show, and scrubbing is a swap: " [:eval "(swap! state wb/scrub 0)"] ". " [:code "wb"] " holds the steps that the buttons of the page take, and " [:code "show!"] " and " [:code "push!"] " are two of those steps with the swap written in. Anything else is a key in the state: " [:eval "(swap! state assoc :cost :prefer-shift)"] ". A run is the exception. Put a run on show with " [:code "show!"] ", because the page works out what it shows again only for a run that arrives that way. If the page ever looks wrong, show the run again or reload the page. The atom refuses a value that the page could not draw, and it says why: " [:eval "(swap! state assoc :step 99)"] "."]
    [:p "A run is a value as well. Equality saturation" [:cite :tate-2009 :willsey-2021] " is one call: " [:eval "(rw/saturate (first (eg/add (eg/egraph) [:+ :a :b]))\n             (lessons/rules-of lessons/ac-rules)\n             {:timeline? true})"] " The button beside the result puts its timeline on show. The next call hands the page a run that has not run yet, and the page steps it like the run of a lesson: " [:eval "(show! (run/start [:+ [:+ :a :b] :c]\n                  (lessons/rules-of lessons/ac-rules)\n                  {}))"] " " (library :bendix) " is loaded beside " (library :cromulent) ": " [:eval "(bx/simplify [:+ [:* 2 :x] [:* 3 :x]])"] ". An e-graph that carries the analysis of bendix shows the polynomial of each e-class in the class list, as in " [:lesson :fix "lesson 8"] ": " [:eval "(show! (bx/saturate [:+ [:expt [:sin :x] 2] [:expt [:cos :x] 2]]\n                     {:rules rules/trig :timeline? true}))"]]
    [:p "Type terms in the native spelling, such as " [:native [:+ [:* 2 :x] :y]] ", and " [:eval "(input/read-term \"2·x + y\")"] " reads the notation. The code you type is interpreted, but every function it calls is the compiled function that the page runs, so the engine is as fast here as in a lesson. There is no interrupt. An evaluation that never ends takes the tab with it."]]})

;; The terms and rules of the cheat sheet. Each link runs one of them,
;; so each is an alternative.

(def taste-rules
  '[["double" [:+ ?x ?x] [:* ?x 2]]
    ["shift" [:* ?x 2] [:<< ?x 1]]])

(def distribute-rules
  '[["distribute" [:* ?a [:+ ?b ?c]] [:+ [:* ?a ?b] [:* ?a ?c]]]
    ["factor" [:+ [:* ?a ?b] [:* ?a ?c]] [:* ?a [:+ ?b ?c]]]])

(def cheat-sheet
  {:key :cheat-sheet :nav "Cheat sheet" :title "Cheat sheet" :needs :cromulent :kind :embiggen
   :operation :saturate
   :inputs [term-input rules-input]
   :values {:term [:/ [:* :a 2] 2] :rules intro-rules}
   :opts {:scheduler :simple}
   :costs [:ast-size :prefer-add :prefer-mul :prefer-shift :no-shift :bendix :no-D]
   :alternatives [{:label "(x + 0)·1" :values {:term [:* [:+ :x 0] 1] :rules basics-rules}}
                  {:label "(a·2)/2" :values {:term [:/ [:* :a 2] 2] :rules intro-rules}}
                  {:label "0 + 1·a" :values {:term [:+ 0 [:* 1 :a]] :rules egg-rules}}
                  {:label "a + a" :values {:term [:+ :a :a] :rules taste-rules}}
                  {:label "a·b + a·c" :values {:term [:+ [:* :a :b] [:* :a :c]] :rules distribute-rules}}
                  {:label "a·(b + c) + a·d" :values {:term [:+ [:* :a [:+ :b :c]] [:* :a :d]] :rules distribute-rules}}
                  {:label "four atoms" :values {:term (sum-of 4) :rules ac-rules}}
                  {:label "five atoms" :values {:term (sum-of 5) :rules ac-rules}}
                  {:label "five atoms, node limit 100" :values {:term (sum-of 5) :rules ac-rules} :opts {:node-limit 100}}
                  {:label "five atoms, backoff" :values {:term (sum-of 5) :rules ac-rules}
                   :opts {:scheduler :backoff :match-limit 4 :ban-length 2 :iter-limit 30}}]
   :panels #{:matches :stats}
   :prose
   [[:p "Each link below loads a term and its rules and runs " [:code "rw/saturate"] ", and " [:tab :inputs "the inputs tab"] " holds them for editing. Each line of code runs at the REPL."]
    [:h3 "Terms and rules"]
    [:ul.sheet
     [:li [:alternative "(x + 0)·1" "(x + 0)·1"] ": add-0 and mul-1. " [:lesson :basics "Start here"]]
     [:li [:alternative "(a·2)/2" "(a·2)/2"] ": the four rules of egg's paper" [:cite :willsey-2021] ". " [:lesson :intro "What is an e-graph?"]]
     [:li [:alternative "0 + 1·a" "0 + 1·a"] ": the rules of egg's README. " [:lesson :rule "Lesson 4"]]
     [:li [:alternative "a + a" "a + a"] ": double and shift, which make a tie. " [:lesson :taste "Lesson 6"]]
     [:li [:alternative "a·b + a·c" "a·b + a·c"] " and " [:alternative "a·(b + c) + a·d" "a·(b + c) + a·d"] ": distribute and factor."]
     [:li [:alternative "four atoms" "a0 + a1 + a2 + a3"] " and " [:alternative "five atoms" "five atoms"] ": commutativity and associativity. " [:lesson :blowup "Lesson 7"]]
     [:li "Five atoms " [:alternative "five atoms, node limit 100" "under a node limit of 100"] " and " [:alternative "five atoms, backoff" "under the backoff scheduler"] ". " [:lesson :saturation "Lesson 5"]]]
    [:h3 "Costs"]
    [:ul.sheet
     [:li [:cost :ast-size "AST size"] ", " [:cost :prefer-add "prefer additions"] ", " [:cost :prefer-mul "prefer multiplications"] ", " [:cost :prefer-shift "prefer shifts"] ", " [:cost :no-shift "no shifts"] ". " [:tab :results "The results tab"] " shows the best term."]
     [:li "For bendix: " [:cost :bendix "bendix's default"] ", and " [:cost :no-D "no D"] ", which evaluates a derivative whenever a rule can."]
     [:li "Print terms as " [:print :notation "notation"] " or as " [:print :native "native"] " vectors."]]
    [:h3 "The e-graph on show"]
    [:ul.sheet
     [:li [:eval "(eg/class-count g)"] " and " [:eval "(eg/node-count g)"]]
     [:li [:eval "(mapv eg/class-count timeline)"]]
     [:li [:eval "(filter #(> (count (eg/nodes g %)) 1) (eg/roots g))"]]
     [:li [:eval "(eg/add g [:+ :b :b])"]]
     [:li [:eval "(swap! state wb/scrub 0)"]]
     [:li [:eval "(export/json g)"]]]
    [:h3 "Runs"]
    [:ul.sheet
     [:li [:eval "(show! (run/start [:+ [:+ :a :b] :c]\n                  (lessons/rules-of lessons/ac-rules)\n                  {}))"]]
     [:li [:eval "(let [[g a] (eg/add g :a)\n      [g b] (eg/add g :b)]\n  (push! (eg/rebuild (first (eg/union g a b))) \"a = b\"))"]]]
    [:h3 "bendix"]
    [:ul.sheet
     [:li [:eval "(bx/simplify [:+ [:* 2 :x] [:* 3 :x]])"] " " [:lesson :fix "Lesson 8"]]
     [:li [:eval "(bx/simplify [:- [:* [:+ :a :b] [:+ :a :b]]\n                 [:+ [:* :a :a] [:* 2 :a :b] [:* :b :b]]])"] " " [:lesson :normal-form "Lesson 9"]]
     [:li [:eval "(show! (bx/saturate [:+ [:expt [:sin :x] 2] [:expt [:cos :x] 2]]\n                     {:rules rules/trig :timeline? true}))"] " " [:lesson :polynomial-rule "Lesson 10"]]
     [:li [:eval "(bx/differentiate [:sin [:* 2 :x]] :x)"] " " [:lesson :differentiation "Lesson 12"]]
     [:li [:eval "(show! (bx/saturate [:D [:* :x [:sin :x]] :x]\n                     {:rules rules/derivative :timeline? true}))"]]]
    [:h3 "Help"]
    [:ul.sheet
     [:li [:eval "(doc rw/saturate)"] " and " [:eval "(doc bx/simplify)"]]
     [:li [:eval "(input/read-term \"2·x + y\")"]]
     [:li "The ? button on the dock lists the names in scope and the keys, and " [:lesson :repl "the REPL page"] " says more."]]]})

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
   normal-form
   polynomial-rule
   what-if
   differentiation
   repl
   cheat-sheet])

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
