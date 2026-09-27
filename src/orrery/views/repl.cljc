(ns orrery.views.repl
  "The REPL panel: an editor, the history, and results rendered by
  what they are: an e-graph as a class list, a [g id] pair with the
  class highlighted, a runner result or a run as a summary with a
  button to scrub it, anything else printed, abridged
  (orrery.printed). What an evaluation printed stands over its value.
  A button names its history entry by index; the value stays in the
  state. And the names in scope, as the REPL's page lists them
  (orrery.names)."
  (:require [orrery.diff :as diff]
            [orrery.names :as names]
            [orrery.printed :as printed]
            [orrery.views.classes :as classes]
            [orrery.views.common :as common]
            [orrery.workbench :as workbench]))

(def ^:private summary printed/egraph-summary)

;; ---------------------------------------------------------------------------
;; the history and the editor

(defn result-view
  "Entry i's value; text is the value as the evaluation printed it,
  when it did."
  [v i mode text]
  (cond
    (diff/egraph? v)
    [:div.result-egraph
     [:div.row [:span.summary (summary v)]
      [:button {:on {:click [:adopt i]}} "show it"]]
     (classes/class-list {:g v :mode mode})]

    (workbench/pair? v)
    (let [[g id] v]
      [:div.result-egraph
       [:div.row [:span.summary (str "[g " id "]: " (summary g))]
        [:button {:on {:click [:adopt i]}} "show it"]]
       (classes/class-list {:g g :mode mode :selected id})])

    (workbench/runner-result? v)
    (let [g (:egraph v)]
      [:div.result-run
       [:div.row
        [:span.summary (str (:iterations v) " iterations, " (common/stop-reason-text (:stop-reason v)) "; " (summary g)
                            (when (:timeline v) (str "; timeline of " (count (:timeline v)))))]
        [:button {:on {:click [:adopt i]}} "scrub it"]]])

    (workbench/run? v)
    [:div.result-run
     [:div.row
      [:span.summary (str "a run: " (printed/run-summary v))]
      [:button {:on {:click [:adopt i]}} "scrub it"]]]

    :else
    [:pre.result (or text (printed/printed v))]))

(defn repl-panel
  "The panel. beside? is the REPL's own page, where the panel stands
  beside the e-graph: the history scrolls inside it, down to the last
  entry after every evaluation, and the editor is taller. Its id is
  not `repl`: that is the address of the REPL's page, and a browser
  scrolls to the element an address names."
  [{:keys [input history mode beside?]}]
  [:div.panel.repl {:id "repl-panel"}
   (common/title "the REPL" (when beside? "user"))
   [:p.hint
    [:code "g"] " is the e-graph you are looking at, " [:code "timeline"] " the whole run and "
    [:code "state"] " the page; " [:code "eg"] ", " [:code "rw"] ", " [:code "ex"] " and " [:code "bx"]
    " are the engine, among the names "
    (if beside?
      "listed over this panel"
      (list "that " [:a {:href "#repl"} "the REPL's page"] " lists"))
    ". Ctrl-Enter evaluates; the up arrow brings back what was evaluated before."]
   (into [:div.history {:id "repl-history" :replicant/on-render [:repl/scroll]}]
         (for [[i {:keys [in out error] :as entry}] (map-indexed vector history)]
           [:div.entry {:replicant/key i}
            [:pre.in (str "user=> " in)]
            (when out [:pre.out out])
            (if (contains? entry :error)
              [:pre.error error]
              (result-view (:ok entry) i mode (:printed entry)))]))
   [:textarea {:id "repl-input" :rows (if beside? 5 3) :value input :placeholder "(eg/class-count g)"
               :aria-label "the REPL's editor"
               :spellcheck "false" :autocapitalize "off" :autocomplete "off"
               :on {:input [:repl/input]
                    :keydown [:repl/keydown]}}]
   [:div.row
    [:button.primary {:id "repl-eval" :on {:click [:repl/eval]}} "eval"]
    [:button {:id "repl-clear" :on {:click [:repl/clear]}} "clear"]]])

;; ---------------------------------------------------------------------------
;; the names in scope

(defn names-view
  "Every name in scope, beside the prose of the REPL's page: what is
  bound, then the namespaces, each under the short name it goes by."
  []
  [:aside.panel.names {:id "names"}
   (common/title "names in scope" "user")
   (into [:dl]
         (concat
          (for [{:keys [name says]} names/bound]
            (list [:dt {:replicant/key (str name)} [:code (str name)]]
                  [:dd {:replicant/key (str name " says")} says]))
          (for [{:keys [alias ns says]} names/namespaces]
            (list [:dt {:replicant/key (str alias)} [:code (str alias)]]
                  [:dd {:replicant/key (str alias " says")} [:code.ns (str ns)] " " says]))))])
