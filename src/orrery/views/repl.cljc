(ns orrery.views.repl
  "The REPL panel: a prompt, the history, and results rendered by
  what they are: an e-graph as a class list, a [g id] pair with the
  class highlighted, a runner result as a summary with a button to
  scrub it, anything else printed. A button names its history entry
  by index; the value stays in the state."
  (:require [cromulent.core :as eg]
            [orrery.diff :as diff]
            [orrery.views.classes :as classes]
            [orrery.views.common :as common]))

(defn- run-result? [v]
  (and (map? v) (diff/egraph? (:egraph v)) (vector? (:stats v))))

(defn- pair? [v]
  (and (vector? v) (= 2 (count v)) (diff/egraph? (first v)) (integer? (second v))))

(defn- summary [g]
  (str (eg/class-count g) " classes, " (eg/node-count g) " nodes"
       (when (:dirty? g) ", dirty: rebuild pending")))

(defn result-view
  "Entry i's value."
  [v i mode]
  (cond
    (diff/egraph? v)
    [:div.result-egraph
     [:div.row [:span.summary (summary v)]
      [:button {:on {:click [:adopt i]}} "show it"]]
     (classes/class-list {:g v :mode mode})]

    (pair? v)
    (let [[g id] v]
      [:div.result-egraph
       [:div.row [:span.summary (str "[g " id "]: " (summary g))]
        [:button {:on {:click [:adopt i]}} "show it"]]
       (classes/class-list {:g g :mode mode :selected id})])

    (run-result? v)
    (let [g (:egraph v)]
      [:div.result-run
       [:div.row
        [:span.summary (str (:iterations v) " iterations, " (common/stop-reason-text (:stop-reason v)) "; " (summary g)
                            (when (:timeline v) (str "; timeline of " (count (:timeline v)))))]
        [:button {:on {:click [:adopt i]}} "scrub it"]]])

    :else
    [:pre.result (pr-str v)]))

(defn repl-panel [{:keys [input history mode]}]
  [:div.panel.repl {:id "repl"}
   [:h3 "the REPL"]
   [:p.hint
    [:code "g"] " is the e-graph you are looking at and " [:code "timeline"] " the whole run; "
    [:code "eg"] ", " [:code "pat"] ", " [:code "rw"] ", " [:code "ex"] ", " [:code "term"] ", "
    [:code "check"] " are cromulent's namespaces, " [:code "notation"] " and " [:code "run"] " orrery's, "
    [:code "bx"] ", " [:code "rules"] ", " [:code "an"] ", " [:code "poly"] " and " [:code "num"] " bendix's. "
    "Ctrl-Enter evaluates."]
   (into [:div.history]
         (for [[i {:keys [in error] :as entry}] (map-indexed vector history)]
           [:div.entry {:replicant/key i}
            [:pre.in (str "user=> " in)]
            (if (contains? entry :error)
              [:pre.error error]
              (result-view (:ok entry) i mode))]))
   [:textarea {:id "repl-input" :rows 3 :value input :placeholder "(eg/class-count g)"
               :on {:input [:repl/input]
                    :keydown [:repl/keydown]}}]
   [:div.row
    [:button.primary {:id "repl-eval" :on {:click [:repl/eval]}} "eval"]
    [:button {:id "repl-clear" :on {:click [:repl/clear]}} "clear"]]])
