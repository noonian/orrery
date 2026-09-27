(ns orrery.views.common
  "Small views that every panel uses: terms in both print modes,
  e-nodes with their class ids as links, the counters, and the stop
  reason in words. Every view is a function of values. Every handler
  in a view is data: an `[action & args]` vector over the actions in
  `orrery.actions`."
  (:require [orrery.notation :as notation]))

(defn term-view
  "Renders the term `t` in the print mode `mode`. In the notation,
  the tooltip is the native spelling, which tells a nested sum from
  a flat one."
  [t mode]
  (if (= :native mode)
    [:code.native (pr-str t)]
    [:span.notation {:title (pr-str t)} (notation/term->str t)]))

(defn enode-view
  "Renders an e-node with its children as class ids. In both print
  modes, each id takes its own colour and opens its class, so that an
  id never reads as a number."
  [node mode]
  (into (if (= :native mode) [:code.native] [:span.notation])
        (map (fn [part]
               (if (map? part)
                 [:span.ref {:on {:click [:select (:id part)]}} (:text part)]
                 part))
             (notation/enode-parts node mode))))

(defn snippet-button
  "Renders a button that adds `code` to the end of the REPL's buffer
  and opens the REPL's dock. `id`, when given, is the button's id."
  ([code] (snippet-button nil code))
  ([id code]
   [:button.to-buffer (cond-> {:title (str "add this code to the end of the buffer:\n\n" code)
                               :on {:click [:repl/code code]}}
                        id (assoc :id id))
    "to buffer"]))

(defn title
  "Renders the heading of a panel. `text` says what the panel shows.
  `of`, when given, is shown beside it and says what made the
  content of the panel: a function or a value, as the REPL under the
  page names it. `code`, when given, is the code that computes what
  the panel shows, and a button adds it to the REPL's buffer."
  ([text] (title text nil))
  ([text of] (title text of nil))
  ([text of code]
   [:div.panel-head
    [:h3 text]
    (when of [:code.of of])
    (when code (snippet-button code))]))

(defn round
  "Rounds `x`, a non-negative number of milliseconds, to the nearest
  integer."
  [x]
  (long (+ (or x 0) 0.5)))

(defn stop-reason-text [reason]
  (case reason
    :saturated "saturated: the rules merge nothing more"
    :node-limit "stopped at the node limit"
    :iter-limit "stopped at the iteration limit"
    :time-limit "stopped at the time limit"
    :stopped "stopped by you"
    :done "done"
    nil "running…"
    (name reason)))

(defn tiles
  "Renders the counters: the classes, the nodes, the step of the
  timeline with how the run ended, and the time the engine took. The
  last entry of the timeline may come after the last iteration."
  [{:keys [classes nodes step n status stop-reason ms]}]
  (let [running? (= :running status)]
    [:div.tiles
     [:div.tile {:id "tile-classes" :class (when running? "running")}
      [:div.tile-label "classes"] [:div.tile-value (str classes)]]
     [:div.tile {:id "tile-nodes" :class (when running? "running")}
      [:div.tile-label "nodes"] [:div.tile-value (str nodes)]]
     [:div.tile {:id "tile-iteration"}
      [:div.tile-label "step"]
      [:div.tile-value (str step " / " n)]
      [:div.tile-note (if running? "running…" (stop-reason-text stop-reason))]]
     [:div.tile {:id "tile-ms"}
      [:div.tile-label "engine time"] [:div.tile-value (str (round ms) " ms")]]]))

(defn print-toggle [mode]
  [:span.print-toggle
   [:button {:class (when (= :notation mode) "primary") :on {:click [:print :notation]}} "notation"]
   " "
   [:button {:class (when (= :native mode) "primary") :on {:click [:print :native]}} "native"]])
