(ns orrery.repl
  "The REPL: SCI embedded as a library, with the compiled engine
  namespaces copied into its context, so `(eg/add g [:+ :x 1])` at
  the prompt calls the same function the scrubber called. This is the
  one namespace that knows SCI exists; the page hands it a string and
  gets a value or an error back."
  (:require [bendix.analysis]
            [bendix.core]
            [bendix.num]
            [bendix.poly]
            [bendix.rules]
            [bendix.term]
            [cromulent.check]
            [cromulent.core]
            [cromulent.extract]
            [cromulent.pattern]
            [cromulent.rewrite]
            [cromulent.term]
            [orrery.diff]
            [orrery.notation]
            [orrery.lessons]
            [orrery.run]
            [sci.core :as sci]))

(def ^:private namespaces
  {'cromulent.core    (sci/copy-ns cromulent.core (sci/create-ns 'cromulent.core))
   'cromulent.pattern (sci/copy-ns cromulent.pattern (sci/create-ns 'cromulent.pattern))
   'cromulent.rewrite (sci/copy-ns cromulent.rewrite (sci/create-ns 'cromulent.rewrite))
   'cromulent.extract (sci/copy-ns cromulent.extract (sci/create-ns 'cromulent.extract))
   'cromulent.term    (sci/copy-ns cromulent.term (sci/create-ns 'cromulent.term))
   'cromulent.check   (sci/copy-ns cromulent.check (sci/create-ns 'cromulent.check))
   'orrery.notation        (sci/copy-ns orrery.notation (sci/create-ns 'orrery.notation))
   'orrery.run        (sci/copy-ns orrery.run (sci/create-ns 'orrery.run))
   'orrery.lessons    (sci/copy-ns orrery.lessons (sci/create-ns 'orrery.lessons))
   'orrery.diff       (sci/copy-ns orrery.diff (sci/create-ns 'orrery.diff))
   'bendix.core       (sci/copy-ns bendix.core (sci/create-ns 'bendix.core))
   'bendix.rules      (sci/copy-ns bendix.rules (sci/create-ns 'bendix.rules))
   'bendix.analysis   (sci/copy-ns bendix.analysis (sci/create-ns 'bendix.analysis))
   'bendix.poly       (sci/copy-ns bendix.poly (sci/create-ns 'bendix.poly))
   'bendix.term       (sci/copy-ns bendix.term (sci/create-ns 'bendix.term))
   'bendix.num        (sci/copy-ns bendix.num (sci/create-ns 'bendix.num))})

(def prelude
  "The user namespace with the engine's aliases."
  "(ns user (:require [cromulent.core :as eg] [cromulent.pattern :as pat]
                     [cromulent.rewrite :as rw] [cromulent.extract :as ex]
                     [cromulent.term :as term] [cromulent.check :as check]
                     [orrery.notation :as notation] [orrery.run :as run]
                     [orrery.lessons :as lessons] [orrery.diff :as diff]
                     [bendix.core :as bx] [bendix.rules :as rules]
                     [bendix.analysis :as an] [bendix.poly :as poly]
                     [bendix.term :as bt] [bendix.num :as num]))")

(defonce ^:private ctx
  (let [c (sci/init {:namespaces namespaces
                     :classes {'js js/globalThis :allow :all}})]
    (sci/eval-string* c prelude)
    c))

(defn bind!
  "g and timeline in the user namespace: the e-graph the page shows
  and the whole run."
  [g timeline]
  (sci/intern ctx 'user 'g g)
  (sci/intern ctx 'user 'timeline timeline))

(defn eval-string
  "{:ok value} or {:error message}."
  [s]
  (try {:ok (sci/eval-string* ctx s)}
       (catch :default e
         {:error (str (ex-message e)
                      (when-let [{:keys [line column]} (ex-data e)]
                        (when line (str " (line " line ", column " column ")"))))})))
