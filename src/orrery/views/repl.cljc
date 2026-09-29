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

  The history also holds traces of what the page's buttons and links
  did to what the REPL sees. A trace is quieter than an evaluation,
  and it shows the code that does the same.

  The REPL stands in a dock along the bottom of every page. Closed,
  the dock is one line: the editor and what was evaluated last. Open,
  it is the editor beside the history. One popover lists the keys and
  the names in scope (`orrery.names`), and another lists snippets of
  code for the buffer (`orrery.snippets`)."
  (:require [clojure.string :as str]
            [orrery.diff :as diff]
            [orrery.names :as names]
            [orrery.printed :as printed]
            [orrery.snippets :as snippets]
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

(defn- to-buffer-button [i]
  [:button.to-buffer {:title "add this code to the end of the buffer"
                      :on {:click [:repl/to-buffer i]}}
   "to buffer"])

(defn- trace-view
  "Renders trace `i`: what a button or a link of the page did, what
  it changed, and the code that does the same, when there is such
  code."
  [i {:keys [trace says in]}]
  [:div.trace {:replicant/key i}
   [:div.trace-row
    [:span.mark "page"]
    [:span.what trace]
    (when says [:span.says says])]
   (when in
     [:div.in-row
      [:code.in in]
      (to-buffer-button i)])])

(defn- history-view
  "Renders the history. Each entry has a button that adds its code to
  the buffer. The history scrolls down to its last entry when it is
  rendered (`[:repl/scroll]`)."
  [history mode]
  (into [:div.history {:id "repl-history" :replicant/on-render [:repl/scroll]}
         (when (empty? history)
           [:p.empty "Nothing is evaluated yet. The ? button lists the keys and the names in scope."])]
        (for [[i {:keys [in out error] :as entry}] (map-indexed vector history)]
          (if (:trace entry)
            (trace-view i entry)
            [:div.entry {:replicant/key i}
           [:div.in-row
            [:pre.in (str "user=> " in)]
            (to-buffer-button i)]
           (when out [:pre.out out])
           (if (contains? entry :error)
             [:pre.error error]
             (result-view (:ok entry) i mode (:printed entry)))]))))

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
  "Renders every name in scope in two sections. The first lists the
  vars bound in the `user` namespace. The second lists the
  namespaces, each under its alias, below a divider."
  []
  [:div.names {:id "names"}
   [:h3 "names in scope"]
   [:section.names-vars {:id "names-vars"}
    [:h4 "vars"]
    [:p.note "These are bound in the user namespace, so you use them by name."]
    (into [:dl]
          (for [{:keys [name says]} names/bound]
            (list [:dt {:replicant/key (str name)} [:code (str name)]]
                  [:dd {:replicant/key (str name " says")} says])))]
   [:section.names-namespaces {:id "names-namespaces"}
    [:h4 "namespaces"]
    [:p.note "Each namespace is loaded under a short name. You call a function in it through that name, as in " [:code "(eg/union g a b)"] "."]
    (into [:dl]
          (for [{:keys [alias ns says]} names/namespaces]
            (list [:dt {:replicant/key (str alias)} [:code (str alias)]]
                  [:dd {:replicant/key (str alias " says")} [:code.ns (str ns)] " " says])))]])

;; ---------------------------------------------------------------------------
;; the keys

(def keys-table
  "The keys of the REPL's line and buffer, each with what it does."
  [["Enter" "On the line, evaluates it when its brackets are closed, and starts a new line when one is open. In the buffer, starts a new line, indented."]
   ["Shift-Enter" "Starts a new line."]
   ["Ctrl-Enter" "In the buffer, evaluates the form at the caret. On the line, evaluates the line."]
   ["Ctrl-Shift-Enter" "In the buffer, evaluates every form in order, and stops at the first error."]
   ["↑ ↓" "On the line, bring back what you evaluated before."]
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
;; the snippets

(defn- snippets-view
  "Renders the popover that lists the snippets of the library, each
  with what it does, its code, and a button that adds it to the
  buffer."
  []
  [:div.repl-help.snippets {:id "repl-snippets" :role "dialog" :aria-label "snippets for the buffer"}
   [:div.help-head
    [:h3 "snippets"]
    [:button.close {:id "repl-snippets-close" :aria-label "close" :on {:click [:repl/snippets]}} "×"]]
   [:p.note "Each snippet does something with g, the e-graph on show. Add one to the buffer, change it if you like, and press Ctrl-Enter in it."]
   (into [:ul.snippet-list]
         (for [{:keys [label says code]} snippets/library]
           [:li {:replicant/key label}
            [:div.snippet-head [:span.label label] (common/snippet-button code)]
            [:p.says says]
            [:pre.code code]]))])

;; ---------------------------------------------------------------------------
;; the dock

(defn- editor
  "Renders the element that prism-code-editor builds the editor of
  `role` in, :line or :buffer (`orrery.editor`). Replicant leaves what
  it builds alone."
  [role opts attrs]
  [:div.editor {:replicant/key (name role)
                :class (name role)
                :replicant/on-render [:repl/editor (assoc opts :role role :attrs attrs)]}])

(defn dock
  "Renders the dock along the bottom of the page that holds the REPL.

  The REPL has two editors, each with its own text. The line is the
  REPL's input: `input`. The buffer is for longer code, and nothing
  the REPL does empties it: `buffer`.

  Closed, the dock is the line, one line about the last evaluation,
  and the buttons. Open, the dock is the buffer on the left, and the
  history over the line on the right, under a row of buttons, with a
  handle along its top edge that resizes it (`[:repl/resize]`).

  The line is the first element of the dock's row, closed or open, so
  Replicant never builds it again.

  `here` is the key of the page on show. `help?` shows the keys and
  the names in scope, and `snippets?` the snippets."
  [{:keys [open? input buffer history mode line-numbers? help? snippets? here]}]
  [:section.dock {:id "repl-dock" :class (if open? "open" "closed") :aria-label "the REPL"}
   [:div.dock-handle {:id "repl-resize" :title "drag to resize the REPL"
                      :on {:pointerdown [:repl/resize]}}]
   [:div.dock-row
    (editor :line {:text input}
            {:id "repl-input" :aria-label "the REPL's line" :placeholder "(eg/class-count g)"})
    (when open?
      (editor :buffer {:text buffer :line-numbers? line-numbers?}
              {:id "repl-buffer" :aria-label "the REPL's buffer"
               :placeholder ";; the buffer: Ctrl-Enter evaluates the form at the caret"}))
    (if open?
      (history-view history mode)
      (last-view (last (remove :trace history))))
    (into [:div.dock-tools]
          (concat
           (when open?
             [[:label.check
               [:input {:id "repl-line-numbers" :type "checkbox" :checked (boolean line-numbers?)
                        :on {:change [:repl/line-numbers]}}]
               " line numbers"]
              [:button.primary {:id "repl-eval" :title "Ctrl-Enter in the buffer" :on {:click [:repl/eval :form]}}
               "eval form"]
              [:button {:id "repl-eval-all" :title "Ctrl-Shift-Enter in the buffer" :on {:click [:repl/eval :all]}}
               "eval buffer"]
              [:button {:id "repl-clear" :title "empty the history" :on {:click [:repl/clear]}} "clear history"]
              [:button {:id "repl-clear-buffer" :title "empty the buffer" :on {:click [:repl/clear-buffer]}} "clear buffer"]])
           [[:button {:id "repl-snippets-toggle" :aria-expanded (str (boolean snippets?))
                      :title "code that does something with g, for the buffer" :on {:click [:repl/snippets]}}
             "snippets"]
            [:button.icon {:id "repl-help-toggle" :aria-expanded (str (boolean help?))
                           :title "the keys and the names in scope" :on {:click [:repl/help]}} "?"]
            [:button.icon {:id "repl-toggle" :aria-expanded (str (boolean open?))
                           :title (if open? "close the REPL (Ctrl-`)" "open the REPL (Ctrl-`)")
                           :on {:click [:repl/dock]}}
             (if open? "▾" "▴")]]))]
   (when help? (help here))
   (when snippets? (snippets-view))])
