(ns orrery.views.repl
  "Renders the REPL panel: an editor, the history, and the results.
  A result is rendered by what it is:

  - an e-graph renders as a class list;
  - a `[g id]` pair renders as a class list with the class
    highlighted;
  - a runner result or a run renders as a summary with a button to
    scrub it;
  - anything else is printed, abridged (`orrery.printed`).

  What an evaluation printed is shown over its value. A button names
  its history entry by index, and the value stays in the state.

  This namespace also renders the names in scope, as the REPL's page
  lists them (`orrery.names`)."
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
  "Renders `v`, the value of history entry `i`. `text` is the value
  as the evaluation printed it, when the evaluation did print it."
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
  "Renders the REPL panel.

  `line-numbers?` numbers the lines of the editor.

  `beside?` is true on the REPL page, where the panel stands beside
  the e-graph. There the editor is taller, and the history scrolls
  inside the panel. The style sheet does the second
  (`.beside .repl .history`).

  On every page, the history scrolls down to its last entry when it
  is rendered (`[:repl/scroll]`).

  The id of the panel is not `repl`. That is the address of the
  REPL's page, and a browser scrolls to the element that an address
  names."
  [{:keys [input history mode line-numbers? beside?]}]
  [:div.panel.repl {:id "repl-panel"}
   (common/title "the REPL" (when beside? "user"))
   [:p.hint
    [:code "g"] " is the e-graph you are looking at, " [:code "timeline"] " is the whole run, and "
    [:code "state"] " is the page. " [:code "eg"] ", " [:code "rw"] ", " [:code "ex"] " and " [:code "bx"]
    " are the engine. "
    (if beside?
      "Every name is listed above this panel."
      (list [:a {:href "#repl"} "The REPL page"] " lists every name."))
    " Press Ctrl-Enter to evaluate. The up arrow brings back what you evaluated before. Tab indents the line; Ctrl-M (Ctrl-Shift-M on a Mac) lets Tab leave the editor."]
   (into [:div.history {:id "repl-history" :replicant/on-render [:repl/scroll]}]
         (for [[i {:keys [in out error] :as entry}] (map-indexed vector history)]
           [:div.entry {:replicant/key i}
            [:pre.in (str "user=> " in)]
            (when out [:pre.out out])
            (if (contains? entry :error)
              [:pre.error error]
              (result-view (:ok entry) i mode (:printed entry)))]))
   ;; prism-code-editor builds the editor inside this element, and
   ;; Replicant leaves what it builds alone (`orrery.editor`).
   [:div.editor {:class (when beside? "tall")
                 :replicant/on-render [:repl/editor {:text input
                                                     :line-numbers? line-numbers?
                                                     :attrs {:id "repl-input"
                                                             :aria-label "the REPL's editor"
                                                             :placeholder "(eg/class-count g)"}}]}]
   [:div.row
    [:button.primary {:id "repl-eval" :on {:click [:repl/eval]}} "eval"]
    [:button {:id "repl-clear" :on {:click [:repl/clear]}} "clear"]
    [:label.check
     [:input {:id "repl-line-numbers" :type "checkbox" :checked (boolean line-numbers?)
              :on {:change [:repl/line-numbers]}}]
     " line numbers"]]])

;; ---------------------------------------------------------------------------
;; the names in scope

(defn names-view
  "Renders every name in scope, beside the prose of the REPL's page.
  Lists what is bound first and then the namespaces, each under its
  alias."
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
