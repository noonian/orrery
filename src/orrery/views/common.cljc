(ns orrery.views.common
  "Small pieces every panel uses: terms in both modes, e-nodes with
  their class ids as links, the counters, the stop reason in words.
  Every view is a function of values, and every handler in it is
  data over orrery.actions."
  (:require [orrery.notation :as notation]))

(defn term-view
  "A term in the mode in force; in the notation, the native spelling
  is the tooltip, which tells a nested sum from a flat one."
  [t mode]
  (if (= :native mode)
    [:code.native (pr-str t)]
    [:span.notation {:title (pr-str t)} (notation/term->str t)]))

(def ^:private digits (zipmap "0123456789" (range 10)))

(defn- ref-id
  "The class id in a printed #id."
  [tok]
  (reduce (fn [n c] (+ (* 10 n) (digits c))) 0 (subs tok 1)))

(defn enode-view
  "An e-node, its children as class ids; in the notation each id is a
  link that opens its class."
  [node mode]
  (if (= :native mode)
    [:code.native (pr-str node)]
    (into [:span.notation]
          (map (fn [tok]
                 (if (re-matches #"#\d+" tok)
                   [:span.ref {:on {:click [:select (ref-id tok)]}} tok]
                   tok))
               (re-seq #"#\d+|[^#]+" (notation/enode->str node))))))

(defn round
  "A non-negative number of milliseconds to the nearest integer."
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
  "The counters: classes, nodes, the step of the timeline (its last
  entry may follow the last iteration), and how it ended."
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
