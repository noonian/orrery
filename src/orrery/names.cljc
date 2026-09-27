(ns orrery.names
  "The names the REPL uses, as data. The table lists the namespaces
  loaded into the REPL, each under a short name, and the names bound
  in its `user` namespace.

  orrery.repl makes its prelude from this table, and the REPL page
  (orrery.views.repl) prints the table. So what the page says is in
  scope is what is in scope. The suites check every name that the
  prose evaluates against this table."
  (:require [clojure.string :as str]))

(def namespaces
  "The namespaces of the REPL. Each row holds the short name
  (:alias), the namespace (:ns), the library the namespace belongs
  to (:of), and one line that says what is in it (:says)."
  [{:alias 'eg :ns 'cromulent.core :of "cromulent"
    :says "the e-graph: egraph, add, union, rebuild, find, lookup, nodes, roots, class-count, node-count"}
   {:alias 'pat :ns 'cromulent.pattern :of "cromulent"
    :says "patterns: ematch, the matches of a pattern in an e-graph, and instantiate"}
   {:alias 'rw :ns 'cromulent.rewrite :of "cromulent"
    :says "rules and the runner: rule, saturate, and start, step and finish, the runner an iteration at a time"}
   {:alias 'ex :ns 'cromulent.extract :of "cromulent"
    :says "extraction: extract, the cheapest term of a class under a cost, and ast-size"}
   {:alias 'term :ns 'cromulent.term :of "cromulent"
    :says "terms and nodes: compound?, operator, children, make"}
   {:alias 'check :ns 'cromulent.check :of "cromulent"
    :says "the invariants: violations, what an e-graph breaks, if anything"}
   {:alias 'export :ns 'cromulent.export :of "cromulent"
    :says "an e-graph as egraph-serialize data (serialize) or text (json)"}
   {:alias 'bx :ns 'bendix.core :of "bendix"
    :says "the algebra system: egraph, one carrying the polynomial analysis, saturate, simplify, differentiate, materialize-all, default-cost, no-D"}
   {:alias 'rules :ns 'bendix.rules :of "bendix"
    :says "bendix's rules: trig, powers, derivative"}
   {:alias 'an :ns 'bendix.analysis :of "bendix"
    :says "the polynomial analysis: what a class's data is, polynomial?, atom?, canonical"}
   {:alias 'poly :ns 'bendix.poly :of "bendix"
    :says "polynomials: add, mul, expt, derivative, ->term"}
   {:alias 'bt :ns 'bendix.term :of "bendix"
    :says "bendix's terms: variable?, constant?, placeholder?"}
   {:alias 'num :ns 'bendix.num :of "bendix"
    :says "exact numbers: ratio, add, mul, ->double, read-string"}
   {:alias 'wb :ns 'orrery.workbench :of "orrery"
    :says "the page's state and the steps over it: show, put, push, scrub, open, start, page, problem"}
   {:alias 'run :ns 'orrery.run :of "orrery"
    :says "a run, a timeline of e-graphs: start, step, run-all, stop, script"}
   {:alias 'lessons :ns 'orrery.lessons :of "orrery"
    :says "the lessons as data, their rules among them: all, by-key, rules-of, ac-rules, egg-rules"}
   {:alias 'eclass :ns 'orrery.eclass :of "orrery"
    :says "one class: class-of, the class of a term, term-count, cheapest, children, parents, history"}
   {:alias 'costs :ns 'orrery.costs :of "orrery"
    :says "the cost picker's costs: all, and cost-fn, the function behind a key"}
   {:alias 'diff :ns 'orrery.diff :of "orrery"
    :says "what changed between two e-graphs of one run: between"}
   {:alias 'notation :ns 'orrery.notation :of "orrery"
    :says "the printer: term->str, rules->str"}
   {:alias 'input :ns 'orrery.input :of "orrery"
    :says "the reader, of either spelling: read-term, read-pattern, read-rules"}])

(def bound
  "The names bound in the `user` namespace. Each row holds the name
  (:name) and one line that says what it is (:says). `g` and
  `timeline` are bound again before every evaluation."
  [{:name 'g :says "the e-graph on show, which is the e-graph of the run at the step on show"}
   {:name 'timeline :says "every step of the run on show, a vector of e-graphs"}
   {:name 'state :says "the page, held in an atom; swapping the atom changes the page"}
   {:name 'show! :says "(show! v) puts v on show: an e-graph, a [g id] pair, a runner's result or a run"}
   {:name 'push! :says "(push! g) or (push! g label) makes g the next step of the run on show"}
   {:name '*1 :says "the last value; *2 and *3 are the two values before it, and *e is the last error"}
   {:name 'doc :says "(doc eg/union) prints the docstring of a function, and (dir eg) lists a namespace"}])

(def aliases
  "Every short name a namespace goes by."
  (into #{} (map :alias) namespaces))

(defn prelude
  "Returns the text of the form that makes the `user` namespace. The
  form requires every namespace of the table under its short name,
  and refers `doc`, `dir`, `apropos` and `find-doc` from
  clojure.repl."
  []
  (str "(ns user (:require "
       (str/join " " (for [{:keys [alias ns]} namespaces] (str "[" ns " :as " alias "]")))
       " [clojure.repl :refer [doc dir apropos find-doc]]))"))

(defn qualifiers
  "Returns the namespace parts of the qualified symbols in `code`, as
  a set of symbols. These are the names that the code needs to be
  aliases. For `(eg/add g t)` the set is #{eg}."
  [code]
  (into #{} (map (comp symbol second)) (re-seq #"[\s(\[{'#@]([a-z][a-z0-9.-]*)/[a-zA-Z*+!?<>=-]" (str " " code))))
