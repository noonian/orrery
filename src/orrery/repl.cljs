(ns orrery.repl
  "The page's REPL. SCI is embedded as a library, and the compiled
  engine namespaces are copied into its context. So
  `(eg/add g [:+ :x 1])` at the REPL calls the same function the
  scrubber called.

  This is the only namespace that knows SCI exists. The page hands
  it a string and gets back a value or an error, together with what
  the evaluation printed.

  The table in orrery.names decides what is in scope. The `user`
  namespace is made from that table, so a namespace copied here must
  also be a row there."
  (:require [bendix.analysis]
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
            [orrery.names :as names]
            [orrery.notation]
            [orrery.run]
            [orrery.workbench]
            [sci.core :as sci])
  (:import [goog.string StringBuffer]))

(def ^:private namespaces
  {'cromulent.core    (sci/copy-ns cromulent.core (sci/create-ns 'cromulent.core))
   'cromulent.pattern (sci/copy-ns cromulent.pattern (sci/create-ns 'cromulent.pattern))
   'cromulent.rewrite (sci/copy-ns cromulent.rewrite (sci/create-ns 'cromulent.rewrite))
   'cromulent.extract (sci/copy-ns cromulent.extract (sci/create-ns 'cromulent.extract))
   'cromulent.term    (sci/copy-ns cromulent.term (sci/create-ns 'cromulent.term))
   'cromulent.check   (sci/copy-ns cromulent.check (sci/create-ns 'cromulent.check))
   'cromulent.export  (sci/copy-ns cromulent.export (sci/create-ns 'cromulent.export))
   'orrery.workbench  (sci/copy-ns orrery.workbench (sci/create-ns 'orrery.workbench))
   'orrery.notation   (sci/copy-ns orrery.notation (sci/create-ns 'orrery.notation))
   'orrery.input      (sci/copy-ns orrery.input (sci/create-ns 'orrery.input))
   'orrery.run        (sci/copy-ns orrery.run (sci/create-ns 'orrery.run))
   'orrery.lessons    (sci/copy-ns orrery.lessons (sci/create-ns 'orrery.lessons))
   'orrery.eclass     (sci/copy-ns orrery.eclass (sci/create-ns 'orrery.eclass))
   'orrery.costs      (sci/copy-ns orrery.costs (sci/create-ns 'orrery.costs))
   'orrery.diff       (sci/copy-ns orrery.diff (sci/create-ns 'orrery.diff))
   'bendix.core       (sci/copy-ns bendix.core (sci/create-ns 'bendix.core))
   'bendix.rules      (sci/copy-ns bendix.rules (sci/create-ns 'bendix.rules))
   'bendix.analysis   (sci/copy-ns bendix.analysis (sci/create-ns 'bendix.analysis))
   'bendix.poly       (sci/copy-ns bendix.poly (sci/create-ns 'bendix.poly))
   'bendix.term       (sci/copy-ns bendix.term (sci/create-ns 'bendix.term))
   'bendix.num        (sci/copy-ns bendix.num (sci/create-ns 'bendix.num))})

(def prelude
  "The text of the form that makes the user namespace with the
  engine's aliases."
  (names/prelude))

(defonce ^:private ctx
  (let [c (sci/init {:namespaces namespaces
                     :classes {'js js/globalThis :allow :all}})]
    ;; Anything printed after an evaluation has returned goes to the
    ;; console. A timer or a watch can print that late.
    (sci/alter-var-root sci/print-newline (constantly true))
    (sci/alter-var-root sci/print-fn (constantly (fn [s] (js/console.log s))))
    (sci/eval-string* c prelude)
    c))

(defonce ^:private recent
  ;; the last three values, the latest first, and the last error
  (atom {:values () :error nil}))

(defn bind!
  "Binds `g`, `timeline` and `sel` in the user namespace. `g` is the
  e-graph the page shows, `timeline` is every step of the run, and
  `sel` is the id of the class that is open, or nil."
  [g timeline sel]
  (sci/intern ctx 'user 'g g)
  (sci/intern ctx 'user 'timeline timeline)
  (sci/intern ctx 'user 'sel sel))

(defn bind-page!
  "Makes the page's atom and functions available at the REPL.

  Interns `state` as `user/state`, and each var in `vars` under its
  own name with its docstring and arglists, so `(doc show!)` works.

  orrery.state calls this at load. It passes its vars in because this
  namespace cannot require orrery.state: orrery.state already
  requires this one."
  [state vars]
  (sci/intern ctx 'user 'state state)
  (doseq [v vars
          :let [m (meta v)]]
    (sci/intern ctx 'user (with-meta (symbol (name (:name m))) (select-keys m [:doc :arglists])) @v)))

(defn eval-string
  "Evaluates the string `s`. Returns {:ok value :printed text}, or
  {:error message} when the evaluation throws. Adds :out, the text
  the evaluation printed, when it printed anything.

  `print` is a function that makes the text of the value. It is
  called while the evaluation's bindings hold and its errors are
  caught, so a lazy value is walked here. What the value prints while
  it is walked goes to :out, and what it throws is the evaluation's
  error.

  *1, *2 and *3 are the values of the last three evaluations, and *e
  is the last error, as at any REPL."
  [s print]
  (let [out (StringBuffer.)
        print! (fn [x] (.append out x))
        {[v1 v2 v3] :values e :error} @recent
        r (sci/with-bindings {sci/print-newline true
                              sci/print-fn print!
                              sci/print-err-fn print!
                              sci/*1 v1 sci/*2 v2 sci/*3 v3 sci/*e e}
            (try (let [v (sci/eval-string* ctx s)
                       text (print v)]
                   (swap! recent update :values #(take 3 (cons v %)))
                   {:ok v :printed text})
                 (catch :default e
                   (swap! recent assoc :error e)
                   {:error (str (ex-message e)
                                (when-let [{:keys [line column]} (ex-data e)]
                                  (when line (str " (line " line ", column " column ")"))))})))
        printed (str out)]
    (cond-> r (seq printed) (assoc :out printed))))
