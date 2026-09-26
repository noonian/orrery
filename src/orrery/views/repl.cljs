(ns orrery.views.repl
  "The REPL panel: a prompt, the history, and results rendered by
  what they are: an e-graph as a class list, a [g id] pair with the
  class highlighted, a runner result as a summary with a button to
  scrub it, anything else printed."
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

(defn result-view [v {:keys [mode on-adopt on-select]}]
  (cond
    (diff/egraph? v)
    [:div.result-egraph
     [:div.row [:span.summary (summary v)]
      [:button {:on {:click #(on-adopt v)}} "show it"]]
     (classes/class-list {:g v :mode mode :on-select on-select})]

    (pair? v)
    (let [[g id] v]
      [:div.result-egraph
       [:div.row [:span.summary (str "[g " id "]: " (summary g))]
        [:button {:on {:click #(on-adopt g)}} "show it"]]
       (classes/class-list {:g g :mode mode :selected id :on-select on-select})])

    (run-result? v)
    (let [g (:egraph v)]
      [:div.result-run
       [:div.row
        [:span.summary (str (:iterations v) " iterations, " (common/stop-reason-text (:stop-reason v)) "; " (summary g)
                            (when (:timeline v) (str "; timeline of " (count (:timeline v)))))]
        [:button {:on {:click #(on-adopt v)}} "scrub it"]]])

    :else
    [:pre.result (pr-str v)]))

(defn repl-panel [{:keys [input history mode on-input on-eval on-clear] :as opts}]
  [:div.panel.repl {:id "repl"}
   [:h3 "the REPL"]
   [:p.hint
    [:code "g"] " is the e-graph you are looking at and " [:code "timeline"] " the whole run; "
    [:code "eg"] ", " [:code "pat"] ", " [:code "rw"] ", " [:code "ex"] ", " [:code "term"] ", "
    [:code "check"] " are cromulent's namespaces, " [:code "lay"] " and " [:code "run"] " orrery's. "
    "Ctrl-Enter evaluates."]
   (into [:div.history]
         (for [[i {:keys [in error] :as entry}] (map-indexed vector history)]
           [:div.entry {:replicant/key i}
            [:pre.in (str "user=> " in)]
            (if (contains? entry :error)
              [:pre.error error]
              (result-view (:ok entry) opts))]))
   [:textarea {:id "repl-input" :rows 3 :value input :placeholder "(eg/class-count g)"
               :on {:input (fn [e] (on-input (.. e -target -value)))
                    :keydown (fn [e]
                               (when (and (= "Enter" (.-key e)) (or (.-ctrlKey e) (.-metaKey e)))
                                 (.preventDefault e)
                                 (on-eval)))}}]
   [:div.row
    [:button.primary {:id "repl-eval" :on {:click #(on-eval)}} "eval"]
    [:button {:id "repl-clear" :on {:click #(on-clear)}} "clear"]]])
