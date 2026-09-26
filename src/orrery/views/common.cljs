(ns orrery.views.common
  "Small pieces every panel uses: terms in both modes, the counters,
  the stop reason in words. Every view is a function of values."
  (:require [orrery.lay :as lay]))

(defn term-view
  "A term in the mode in force."
  [t mode]
  (if (= :native mode)
    [:code.native (pr-str t)]
    [:span.lay (lay/term->str t)]))

(defn enode-view
  "An e-node, its children as class ids."
  [node mode on-ref]
  (if (= :native mode)
    [:code.native (pr-str node)]
    [:span.lay (lay/enode->str node)]))

(defn stop-reason-text [reason]
  (case reason
    :saturated "saturated: no rule can add anything"
    :node-limit "stopped at the node limit"
    :iter-limit "stopped at the iteration limit"
    :time-limit "stopped at the time limit"
    :stopped "stopped by you"
    :done "done"
    nil "running…"
    (name reason)))

(defn tiles
  "The counters: classes, nodes, iteration, and how it ended."
  [{:keys [classes nodes step iterations status stop-reason ms]}]
  (let [running? (= :running status)]
    [:div.tiles
     [:div.tile {:id "tile-classes" :class (when running? "running")}
      [:div.tile-label "classes"] [:div.tile-value (str classes)]]
     [:div.tile {:id "tile-nodes" :class (when running? "running")}
      [:div.tile-label "nodes"] [:div.tile-value (str nodes)]]
     [:div.tile {:id "tile-iteration"}
      [:div.tile-label "iteration"]
      [:div.tile-value (str step " / " iterations)]
      [:div.tile-note (if running? "running…" (stop-reason-text stop-reason))]]
     [:div.tile {:id "tile-ms"}
      [:div.tile-label "engine time"] [:div.tile-value (str (js/Math.round (or ms 0)) " ms")]]]))

(defn print-toggle [mode on-change]
  [:span.print-toggle
   [:button {:class (when (= :lay mode) "primary") :on {:click #(on-change :lay)}} "lay"]
   " "
   [:button {:class (when (= :native mode) "primary") :on {:click #(on-change :native)}} "native"]])
