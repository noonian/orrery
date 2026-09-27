(ns orrery.repl
  "The REPL: SCI embedded as a library, with the compiled engine
  namespaces copied into its context, so `(eg/add g [:+ :x 1])` at
  the REPL calls the same function the scrubber called. This is the
  one namespace that knows SCI exists; the page hands it a string and
  gets a value or an error back, with what the evaluation printed.
  What is in scope is orrery.names' table: the `user` namespace is
  made from it, so a namespace copied here must be a row there."
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
  "The user namespace with the engine's aliases."
  (names/prelude))

(defonce ^:private ctx
  (let [c (sci/init {:namespaces namespaces
                     :classes {'js js/globalThis :allow :all}})]
    ;; what is printed once an evaluation has returned, by a timer
    ;; or a watch, goes to the console
    (sci/alter-var-root sci/print-newline (constantly true))
    (sci/alter-var-root sci/print-fn (constantly (fn [s] (js/console.log s))))
    (sci/eval-string* c prelude)
    c))

(defonce ^:private recent
  ;; the last three values, the latest first, and the last error
  (atom {:values () :error nil}))

(defn bind!
  "g and timeline in the user namespace: the e-graph the page shows
  and the whole run."
  [g timeline]
  (sci/intern ctx 'user 'g g)
  (sci/intern ctx 'user 'timeline timeline))

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
  "{:ok value :printed text} or {:error message}, with :out, what the
  evaluation printed, when it printed anything. print makes the text
  of the value, and is called while the evaluation's bindings hold
  and its errors are caught, so a lazy value is walked here: what it
  prints is in :out, and what it throws is the evaluation's error.
  *1, *2 and *3 are the values of the last three evaluations and *e
  the last error, as at any REPL."
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
