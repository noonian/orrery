(ns orrery.views.panels
  "Renders the panels that read the run on show: the replay bar, the
  tools row, the graph, the classes, the run's counters, the best
  term, the matches, the iterations, the tree and the REPL. A page
  arranges them. orrery.views.lesson arranges them for a lesson and
  for the REPL's page.

  Each panel takes the state value `s` and what else it needs as
  plain values. None of them reads the lesson, so a page that is not
  a lesson can use them too."
  (:require [cromulent.core :as eg]
            [orrery.costs :as costs]
            [orrery.derived :as derived]
            [orrery.run :as run]
            [orrery.views.classes :as classes]
            [orrery.views.common :as common]
            [orrery.views.detail :as detail]
            [orrery.views.graph :as graph]
            [orrery.views.matches :as matches]
            [orrery.views.repl :as repl]
            [orrery.views.scrubber :as scrubber]
            [orrery.views.stats :as stats]
            [orrery.views.tree :as tree]))

(defn run-panel
  "Renders the counters and how the run ended. The heading names
  `of`, the function that made the run, or says \"from the REPL\"
  when the REPL put the run on show. The transport is not in this
  panel. It is in the replay bar, over the class list."
  [s r g of]
  [:div.panel
   (common/title "the run" (if (= :repl (:from r)) "from the REPL" of))
   (common/tiles {:classes (eg/class-count g) :nodes (eg/node-count g)
                  :step (:step s) :n (run/last-step r)
                  :status (:status r) :stop-reason (:stop-reason r) :ms (:ms r)})
   [:div {:id "run-status" :data-status (name (:status r))
          :data-stop-reason (some-> (:stop-reason r) name)
          :data-dirty (str (boolean (:dirty? g)))}]
   (when (:dirty? g)
     [:div.status [:span.badge.dirty "rebuild pending: the invariants are not restored yet"]])])

(defn replay-bar
  "Renders the replay bar: the transport and, under it, the opened
  class at the step on show. The bar sits directly above the class
  list and sticks to the top of what scrolls it. So the classes
  change in view as you scrub, and the opened class never scrolls
  away."
  [s r g]
  [:div.replay {:id "replay"}
   (scrubber/scrubber {:step (:step s) :n (run/last-step r) :labels (:labels r)
                       :playing? (some? (get-in s [:ui :playing])) :status (:status r)
                       :summary (str (eg/class-count g) " classes · " (eg/node-count g) " nodes")})
   (when-let [d (derived/detail-at s (:step s))]
     (detail/detail-view d {:mode (get-in s [:ui :print])
                            :cost-label (costs/label (:cost s))
                            :labels (:labels r)
                            :root-id (derived/root-at s)}))])

(defn tools
  "Renders the row under the replay bar. The row holds the switch
  for the graph picture, and the buttons that copy or download the
  e-graph on show as egraph-serialize JSON."
  [s]
  (let [graph? (derived/graph? s)]
    [:div.tools {:id "tools"}
     [:button {:id "graph-toggle" :class (when graph? "current") :on {:click [:graph/toggle]}}
      (if graph? "hide the graph" "draw the graph")]
     [:span.spacer]
     [:span.status {:title "the e-graph on show in the egraph-serialize format, which egg's and egglog's tools read"}
      "this step as egraph-serialize JSON:"]
     [:button {:id "export-copy" :on {:click [:export/copy]}} "copy"]
     [:button {:id "export-download" :on {:click [:export/download]}} "download"]
     (when-let [m (get-in s [:ui :export-status])]
       [:span.status {:id "export-status"} m])]))

(defn graph-panel
  "Renders the graph picture, when it is switched on."
  [s matches]
  (when (derived/graph? s)
    (let [selected (get-in s [:ui :selected])]
      (graph/graph-view {:layout (derived/graph-at s)
                         :mode (get-in s [:ui :print])
                         :root (derived/root-at s)
                         :selected selected
                         :hovered (get-in s [:ui :hover])
                         :diff (derived/diff-at s)
                         :matches (derived/matched-classes matches)
                         :zoom (get-in s [:ui :graph-zoom])
                         :filter? (get-in s [:ui :graph-filter?])
                         :filterable? (some? selected)}))))

(defn best-panel
  "Renders the cheapest term of the root class under the cost in
  force. `cost-keys` are the costs the picker offers. The picker is
  left out when it would offer one cost or none."
  [s cost-keys]
  (let [best (derived/best-at s)
        root (derived/root-at s)
        {:keys [cost term]} (best root)
        picker (filter (comp (set cost-keys) :key) costs/all)]
    [:div.panel.best
     (common/title "best so far" "ex/extract")
     [:div.term {:id "best-notation"} (common/term-view term :notation)]
     [:div {:id "best-term"} (common/term-view term :native)]
     [:div.changed (str "cost " (costs/cost-str cost) " under " (costs/label (:cost s)))]
     [:p.says {:id "extract-says"}
      [:code "(ex/extract g root cost)"] " returns the cheapest term of " [:code "root"] " in " [:code "g"] ". "
      [:code "root"] " is the class of the input, and " [:code "g"] " is the e-graph at this step."]
     (when (> (count picker) 1)
       (into [:div.cost-picker [:code.arg "cost"]]
             (for [c picker]
               [:label {:replicant/key (:key c) :title (:blurb c)}
                [:input {:type "radio" :name "cost" :value (name (:key c)) :checked (= (:key c) (:cost s))
                         :on {:change [:cost (:key c)]}}]
                " " (:label c)])))]))

(defn class-panel
  "Renders the class list over `g`, the e-graph at step `k`. The
  heading is `title`, with `of` beside it, which is what the REPL
  calls that e-graph. The class that is open at that step marks its
  own row and the rows of its relatives."
  [title of g s k opts]
  [:div.panel
   (common/title title of)
   (classes/class-list (merge {:g g :mode (get-in s [:ui :print])
                               :selected (get-in s [:ui :selected])
                               :hovered (get-in s [:ui :hover])
                               :detail (derived/detail-at s k)}
                              opts))])

(defn classes-panel
  "Renders the class list of the e-graph on show, with what changed
  at this step, the best term of each class and the classes the
  rules matched."
  [s g matches]
  (class-panel "the classes" "g at this step" g s (:step s)
               {:diff (derived/diff-at s) :best (derived/best-at s) :root (derived/root-at s)
                :matches (derived/matched-classes matches)}))

(defn fork-panels
  "Renders the original e-graph, step 0, beside the e-graph on show."
  [s r g]
  [:div.fork
   (class-panel "the original, step 0" "(first timeline)" (derived/egraph-at s 0) s 0 {:root (:root r)})
   (class-panel (str "this step: " (nth (:labels r) (:step s) "")) "g at this step" g s (:step s)
                {:diff (derived/diff-at s) :best (derived/best-at s) :root (derived/root-at s)})])

(defn matches-panel [matches]
  (matches/matches-panel {:matches matches}))

(defn stats-panel [s r]
  [:div.panel
   (common/title "the iterations" ":stats")
   (stats/stats-table {:stats (:stats r) :rules (:rules r) :step (:step s)})])

(defn tree-panel
  "Renders the term as a tree over the e-graph on show."
  [s g term]
  [:div.panel.tree-panel
   (common/title "the tree" "term")
   (tree/tree-view {:g g :term term
                    :hovered (or (get-in s [:ui :hover]) (get-in s [:ui :selected]))})])

(defn repl-panel
  "Renders the REPL panel. `place` is where it stands: :dock, in the
  bar along the bottom of a lesson, or :beside, next to the e-graph
  on the REPL's page."
  [s place]
  (repl/repl-panel {:input (get-in s [:repl :input])
                    :history (get-in s [:repl :history])
                    :mode (get-in s [:ui :print])
                    :line-numbers? (get-in s [:ui :line-numbers?])
                    :place place}))

(defn repl-dock
  "Renders the REPL in a bar along the bottom of the viewport. Closed,
  the bar holds a button to open it and the last thing evaluated.
  Open, it holds the REPL panel too, and a handle along its top edge
  that resizes it."
  [s]
  (repl/dock {:open? (get-in s [:ui :repl-open?])
              :last (peek (get-in s [:repl :history]))
              :panel (repl-panel s :dock)}))
