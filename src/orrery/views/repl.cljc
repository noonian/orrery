(ns orrery.views.repl
  "Renders the REPL: an editor, the history, and the results.
  A result is rendered by what it is:

  - an e-graph renders as a class list;
  - a `[g id]` pair renders as a class list with the class
    highlighted;
  - a runner result or a run renders as a summary with a button to
    scrub it;
  - anything else is printed, abridged (`orrery.printed`).

  What an evaluation printed is shown over its value. A button names
  its history entry by index, and the value stays in the state.

  The REPL stands in a dock along the bottom of every page. Closed,
  the dock is one line: the editor and what was evaluated last. Open,
  it is the editor beside the history. A popover lists the keys and
  the names in scope (`orrery.names`)."
  (:require [clojure.string :as str]
            [orrery.diff :as diff]
            [orrery.names :as names]
            [orrery.printed :as printed]
            [orrery.views.classes :as classes]
            [orrery.views.common :as common]
            [orrery.workbench :as workbench]))

(def ^:private summary printed/egraph-summary)

;; ---------------------------------------------------------------------------
;; the history

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

(defn- history-view
  "Renders the history. It scrolls down to its last entry when it is
  rendered (`[:repl/scroll]`)."
  [history mode]
  (into [:div.history {:id "repl-history" :replicant/on-render [:repl/scroll]}
         (when (empty? history)
           [:p.empty "Nothing is evaluated yet. The ? button lists the keys and the names in scope."])]
        (for [[i {:keys [in out error] :as entry}] (map-indexed vector history)]
          [:div.entry {:replicant/key i}
           [:pre.in (str "user=> " in)]
           (when out [:pre.out out])
           (if (contains? entry :error)
             [:pre.error error]
             (result-view (:ok entry) i mode (:printed entry)))])))

;; ---------------------------------------------------------------------------
;; the last evaluation, in a line

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

(defn- last-view
  "Renders one line about the last evaluation, `last`, for the closed
  dock. Clicking it opens the dock."
  [last]
  [:button.last {:id "repl-last" :class (when (contains? last :error) "error")
                 :title "open the REPL to see the history" :on {:click [:repl/dock]}}
   (when last (str "user=> " (first-line (:in last)) "  ⇒  " (result-line last)))])

;; ---------------------------------------------------------------------------
;; the names in scope

(defn names-view
  "Renders every name in scope. Lists what is bound first and then
  the namespaces, each under its alias."
  []
  [:div.names {:id "names"}
   [:h3 "names in scope"]
   (into [:dl]
         (concat
          (for [{:keys [name says]} names/bound]
            (list [:dt {:replicant/key (str name)} [:code (str name)]]
                  [:dd {:replicant/key (str name " says")} says]))
          (for [{:keys [alias ns says]} names/namespaces]
            (list [:dt {:replicant/key (str alias)} [:code (str alias)]]
                  [:dd {:replicant/key (str alias " says")} [:code.ns (str ns)] " " says]))))])

;; ---------------------------------------------------------------------------
;; the keys

(def keys-table
  "The keys of the REPL's editor, each with what it does."
  [["Enter" "In the closed dock, evaluates the line. In the open dock, starts a new line, indented."]
   ["Shift-Enter" "Starts a new line, and opens the dock."]
   ["Ctrl-Enter" "Evaluates the form at the caret. In the closed dock, evaluates the line."]
   ["Ctrl-Shift-Enter" "Evaluates every form in the editor, in order, and stops at the first error."]
   ["↑ ↓" "In the closed dock, bring back what you evaluated before."]
   ["Ctrl-`" "Opens the dock, or closes it. Escape closes it too."]
   ["Tab" "Indents the lines of the selection. Shift-Tab outdents them. Ctrl-M (Ctrl-Shift-M on a Mac) lets Tab leave the editor."]])

(defn- help
  "Renders the popover that lists the keys and the names in scope.
  On a page other than the REPL's, it links to the REPL's page."
  [here]
  [:div.repl-help {:id "repl-help" :role "dialog" :aria-label "the REPL's keys and names"}
   [:div.help-head
    [:h3 "keys"]
    [:button.close {:id "repl-help-close" :aria-label "close" :on {:click [:repl/help]}} "×"]]
   [:p.note "On a Mac, Cmd works in place of Ctrl, except for Ctrl-`."]
   (into [:dl.keys]
         (for [[k says] keys-table]
           (list [:dt {:replicant/key k} [:kbd k]]
                 [:dd {:replicant/key (str k " says")} says])))
   (names-view)
   (when (not= :repl here)
     [:p.note [:a {:href "#repl"} "The REPL page"] " shows what you can do with these names."])])

;; ---------------------------------------------------------------------------
;; the dock

(defn- line-count [s] (inc (count (re-seq #"\n" (str s)))))

(defn dock
  "Renders the dock along the bottom of the page that holds the REPL.

  Closed, the dock is one line: the editor, one line about the last
  evaluation, and the buttons. The editor shows its first line, and
  says how many lines it has when it has more than one. Open, the dock
  is the editor beside the history, under a row of buttons, with a
  handle along its top edge that resizes it (`[:repl/resize]`).

  The editor is the first element of the dock's row, closed or open,
  so Replicant never builds it again. prism-code-editor builds the
  editor inside that element (`orrery.editor`).

  `here` is the key of the page on show. `help?` shows the keys and
  the names in scope."
  [{:keys [open? input history mode line-numbers? help? here]}]
  (let [n (line-count input)]
    [:section.dock {:id "repl-dock" :class (if open? "open" "closed") :aria-label "the REPL"}
     [:div.dock-handle {:id "repl-resize" :title "drag to resize the REPL"
                        :on {:pointerdown [:repl/resize]}}]
     [:div.dock-row
      [:div.dock-editor
       [:div.editor {:replicant/key "editor"
                     :replicant/on-render [:repl/editor {:text input
                                                         :open? (boolean open?)
                                                         :line-numbers? line-numbers?
                                                         :attrs {:id "repl-input"
                                                                 :aria-label "the REPL's editor"
                                                                 :placeholder "(eg/class-count g)"}}]}]
       (when (and (not open?) (> n 1))
         [:button.more {:id "repl-more" :on {:click [:repl/dock]}} (str "+" (dec n) " lines")])]
      (if open?
        (history-view history mode)
        (last-view (peek history)))
      (into [:div.dock-tools]
            (concat
             (when open?
               [[:button.primary {:id "repl-eval" :title "Ctrl-Enter" :on {:click [:repl/eval :form]}} "eval form"]
                [:button {:id "repl-eval-all" :title "Ctrl-Shift-Enter" :on {:click [:repl/eval :all]}} "eval all"]
                [:button {:id "repl-clear" :title "empty the history" :on {:click [:repl/clear]}} "clear"]
                [:label.check
                 [:input {:id "repl-line-numbers" :type "checkbox" :checked (boolean line-numbers?)
                          :on {:change [:repl/line-numbers]}}]
                 " line numbers"]])
             [[:button.icon {:id "repl-help-toggle" :aria-expanded (str (boolean help?))
                             :title "the keys and the names in scope" :on {:click [:repl/help]}} "?"]
              [:button.icon {:id "repl-toggle" :aria-expanded (str (boolean open?))
                             :title (if open? "close the REPL (Ctrl-`)" "open the REPL (Ctrl-`)")
                             :on {:click [:repl/dock]}}
               (if open? "▾" "▴")]]))]
     (when help? (help here))]))

