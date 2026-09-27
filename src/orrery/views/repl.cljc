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

  The panel stands in a dock along the bottom of a lesson, or beside
  the e-graph on the REPL's page. This namespace also renders the
  dock, and the names in scope as the REPL's page lists them
  (`orrery.names`)."
  (:require [clojure.string :as str]
            [orrery.diff :as diff]
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

(defn- hint
  "Renders what the REPL has in scope and the keys of its editor. On
  the REPL's page the hint heads the panel. In the dock it is the
  first thing in the history and scrolls away with it, so a short
  dock gives its room to the history."
  [beside?]
  [:p.hint
   [:code "g"] " is the e-graph you are looking at, " [:code "timeline"] " is the whole run, and "
   [:code "state"] " is the page. " [:code "eg"] ", " [:code "rw"] ", " [:code "ex"] " and " [:code "bx"]
   " are the engine. "
   (if beside?
     "Every name is listed above this panel."
     (list [:a {:href "#repl"} "The REPL page"] " lists every name."))
   " Press Ctrl-Enter to evaluate. The up arrow brings back what you evaluated before. Tab indents the line; Ctrl-M (Ctrl-Shift-M on a Mac) lets Tab leave the editor."])

(defn repl-panel
  "Renders the REPL panel.

  `place` is where the panel stands:

  - :dock is the bar along the bottom of a lesson. The bar names the
    REPL, so the panel has no heading of its own.
  - :beside is the REPL's page, where the panel stands beside the
    e-graph. There the editor is taller.

  In both places the history scrolls inside the panel, which the
  style sheet does, and in the dock the hint scrolls with it. The
  history scrolls down to its last entry when it is rendered
  (`[:repl/scroll]`).

  `line-numbers?` numbers the lines of the editor.

  The id of the panel is not `repl`. That is the address of the
  REPL's page, and a browser scrolls to the element that an address
  names."
  [{:keys [input history mode line-numbers? place]}]
  (let [beside? (= :beside place)
        h (hint beside?)]
    [:div.panel.repl {:id "repl-panel"}
     (when beside? (common/title "the REPL" "user"))
     (when beside? h)
     (into [:div.history {:id "repl-history" :replicant/on-render [:repl/scroll]}
            (when-not beside? h)]
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
                                                       :focus? (= :dock place)
                                                       :attrs {:id "repl-input"
                                                               :aria-label "the REPL's editor"
                                                               :placeholder "(eg/class-count g)"}}]}]
     [:div.row
      [:button.primary {:id "repl-eval" :on {:click [:repl/eval]}} "eval"]
      [:button {:id "repl-clear" :on {:click [:repl/clear]}} "clear"]
      [:label.check
       [:input {:id "repl-line-numbers" :type "checkbox" :checked (boolean line-numbers?)
                :on {:change [:repl/line-numbers]}}]
       " line numbers"]]]))

;; ---------------------------------------------------------------------------
;; the dock

(defn- first-line [s]
  (first (str/split-lines (str s))))

(defn result-line
  "Returns one line that says what history entry `entry` evaluated
  to: the first line of its error, a summary of an e-graph or a run,
  or the start of the value as printed."
  [{:keys [error printed] :as entry}]
  (if (contains? entry :error)
    (first-line error)
    (let [v (:ok entry)]
      (cond
        (diff/egraph? v) (summary v)
        (workbench/pair? v) (str "[g " (second v) "]: " (summary (first v)))
        (workbench/runner-result? v) (str (:iterations v) " iterations; " (summary (:egraph v)))
        (workbench/run? v) (str "a run: " (printed/run-summary v))
        :else (first-line (or printed (printed/printed v)))))))

(defn dock
  "Renders the bar along the bottom of a lesson that holds the REPL.

  Closed, the bar holds the button that opens it and one line about
  the last evaluation, `last`. Open, it holds `panel`, the REPL
  panel, under the button, and a handle along its top edge that
  resizes it (`[:repl/resize]`). Ctrl-` opens and closes it from
  anywhere on the page."
  [{:keys [open? last panel]}]
  [:section.dock {:id "repl-dock" :class (when open? "open") :aria-label "the REPL"}
   (when open?
     [:div.dock-handle {:id "repl-resize" :title "drag to resize the REPL"
                        :on {:pointerdown [:repl/resize]}}])
   [:div.dock-bar
    [:button {:id "repl-toggle" :aria-expanded (str (boolean open?)) :on {:click [:repl/dock]}}
     (if open? "▾ the REPL" "▴ the REPL")]
    (when (and last (not open?))
      [:code.last {:id "repl-last"} (str "user=> " (first-line (:in last)) "  ⇒  " (result-line last))])
    [:span.key "Ctrl-` opens and closes it"]]
   (when open? panel)])

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
