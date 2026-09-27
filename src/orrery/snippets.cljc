(ns orrery.snippets
  "Code for the REPL's buffer, as data. Each snippet is code that
  does something useful with the e-graph on show. A button puts a
  snippet at the end of the buffer, where the learner can read it,
  change it and evaluate it.

  `panels` holds the code behind three parts of the page: the run's
  counters, the opened class and the export. `library` holds code
  for what no panel shows. The dock lists the library.

  Every snippet uses only the names in scope (orrery.names). The
  suites check that, and the browser suite evaluates every snippet
  on a lesson.")

(def panels
  "The code behind a part of the page, by the part's key. The code
  computes what that part shows."
  {:run
   ";; the counters of the run
{:classes (eg/class-count g)
 :nodes (eg/node-count g)
 :step (:step @state)
 :steps (dec (count timeline))}"

   :class
   ";; the opened class: sel, in g
(let [c (eg/find g sel)
      cost (costs/cost-fn (:cost @state) g)]
  {:class c
   :nodes (eg/nodes g c)
   :terms (eclass/term-count g c)
   :cheapest (nth (eclass/cheapest g cost 3) c)
   :children (eclass/children g c)
   :parents (eclass/parents g c)
   :history (eclass/history timeline (:step @state) c)})"

   :export
   ";; g as egraph-serialize JSON, each node costed under the cost in force
(export/json g {:cost (costs/cost-fn (:cost @state) g)})"})

(def library
  "Code for what no panel shows. Each row holds a label, one line
  that says what the code does (:says), and the code."
  [{:label "classes at every step"
    :says "The number of classes at each step of the run."
    :code "(mapv eg/class-count timeline)"}
   {:label "classes with more than one node"
    :says "The classes that hold two or more nodes. In each one, the rules found another way to write the same thing."
    :code "(filter #(> (count (eg/nodes g %)) 1) (eg/roots g))"}
   {:label "the cheapest term of every class"
    :says "Each class with its cheapest term under the cost in force, in the notation."
    :code "(let [cost (costs/cost-fn (:cost @state) g)
      best (ex/extractor g cost)]
  (into (sorted-map)
        (for [c (eg/roots g)]
          [c (notation/term->str (:term (best c)))])))"}
   {:label "how many terms each class stands for"
    :says "Each class with the number of terms it stands for. A class that reaches itself stands for infinitely many."
    :code "(into (sorted-map)
      (for [c (eg/roots g)]
        [c (eclass/term-count g c)]))"}
   {:label "where a pattern matches"
    :says "Every class that the pattern matches, with the class that each variable stands for. Change the pattern to try another."
    :code "(pat/ematch g '[:+ ?a ?b])"}
   {:label "what changed at this step"
    :says "What the step on show added and merged, compared with the step before it."
    :code "(let [k (:step @state)]
  (when (pos? k)
    (diff/between (nth timeline (dec k)) g)))"}
   {:label "the invariants"
    :says "What g breaks of the e-graph's invariants. It is empty when g breaks none."
    :code "(check/violations g)"}
   {:label "add a term as the next step"
    :says "Adds a term to g and makes the result the next step of the run on show."
    :code "(let [[g2 _] (eg/add g (:term (input/read-term \"x·1 + 0\")))]
  (push! (eg/rebuild g2) \"added x·1 + 0\"))"}
   {:label "merge the opened class with another"
    :says "Asserts that the opened class equals class 0, rebuilds, and makes the result the next step. Change 0 to the id of another class."
    :code "(push! (eg/rebuild (first (eg/union g sel 0)))
       (str \"merged \" sel \" and 0\"))"}
   {:label "saturate g under your own rules"
    :says "Runs rules that you write over g, and puts the run on show. Write one rule per line."
    :code "(show! (rw/saturate g
                    (lessons/rules-of (:rules (input/read-rules \"comm: ?a + ?b -> ?b + ?a\")))
                    {:iter-limit 5 :timeline? true}))"}])
