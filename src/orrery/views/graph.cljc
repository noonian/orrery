(ns orrery.views.graph
  "The picture of the e-graph on show (orrery.graph), as SVG: a box
  per class with its nodes inside, an edge from each node's child
  slot to the class it points at, and the marks the class list uses,
  the input's class, the opened and the hovered class, what the step
  added or merged, where a rule matches. Hovering a box lights its
  row in the list and clicking it opens the class, as in the tree.
  The controls: zoom in and out or fit the width, and restrict the
  picture to what the opened class reaches. Off by default, since it
  takes the room; the tools row above it turns it on and off.
  Every handler is data over orrery.actions."
  (:require [orrery.graph :as graph]
            [orrery.notation :as notation]))

(defn- edge-path
  "A cubic from the node's slot to the class's top; a cycle-closing
  edge dips under both and comes back up to the class's foot."
  [{:keys [x1 y1 x2 y2 back?]}]
  (if back?
    (let [d 36]
      (str "M" x1 " " y1 " C" x1 " " (+ y1 d) " " x2 " " (+ y2 d) " " x2 " " y2))
    (let [m (quot (+ y1 y2) 2)]
      (str "M" x1 " " y1 " C" x1 " " m " " x2 " " m " " x2 " " y2))))

(defn- marker [id]
  [:marker {:id id :viewBox "0 0 8 8" :refX 7 :refY 4 :markerWidth 7 :markerHeight 7 :orient "auto"}
   [:path {:d "M0 0 L8 4 L0 8 z"}]])

(defn- class-view [{:keys [id x y w h sub nodes]} {:keys [root selected hovered added absorbing new-classes matches]}]
  (let [added-here (get added id #{})
        ref (notation/class-ref id)]
    [:g.eclass {:replicant/key id
                :data-id id
                :class (cond-> []
                         (= id root) (conj "root")
                         (= id selected) (conj "selected")
                         (= id hovered) (conj "hovered")
                         (contains? absorbing id) (conj "absorbing")
                         (contains? new-classes id) (conj "new-class")
                         (contains? matches id) (conj "match"))
                :on {:click [:tree/open id]
                     :mouseenter [:tree/hover id]
                     :mouseleave [:tree/hover nil]}}
     [:rect.box {:x x :y y :width w :height h :rx 8}]
     [:text.cid {:x (+ x graph/box-pad) :y (+ y 13)} ref]
     (when sub
       [:text.sub {:x (+ x graph/box-pad (* graph/char-w (+ 2 (count ref)))) :y (+ y 13)} sub])
     (for [{:keys [node label] nx :x ny :y nw :w nh :h} nodes]
       [:g.enode {:class (when (contains? added-here node) "added")}
        [:rect {:x (+ x nx) :y (+ y ny) :width nw :height nh :rx 5}]
        [:text {:x (+ x nx (quot nw 2)) :y (+ y ny 15) :text-anchor "middle"} label]])]))

(defn graph-view
  "The graph panel. layout is orrery.graph/layout's; root the input's
  class; selected and hovered the opened and the lit class; diff what
  the step changed; matches the classes a rule matches; zoom a factor
  over the natural size or nil to fit the width; filter? whether the
  picture is what the opened class reaches, filterable? whether a
  class is open to filter by."
  [{:keys [layout root selected hovered diff matches zoom filter? filterable?]}]
  (let [{:keys [width height classes edges]} layout
        lit (or hovered selected)
        marks {:root root :selected selected :hovered hovered :matches matches
               :added (:added diff) :absorbing (:absorbing diff) :new-classes (:new-classes diff)}]
    [:div.panel.graph {:id "graph"}
     [:div.graph-tools
      [:span.status {:id "graph-counts"}
       (str (count classes) " classes · " (reduce + 0 (map (comp count :nodes) classes)) " nodes drawn")]
      [:label.filter {:title "with a class opened, draw only the classes it reaches"}
       [:input {:type "checkbox" :id "graph-filter" :checked (boolean filter?) :disabled (not filterable?)
                :on {:change [:graph/filter]}}]
       " only what the opened class reaches"]
      [:span.spacer]
      [:button {:id "graph-zoom-out" :title "smaller" :on {:click [:graph/zoom :out]}} "−"]
      [:button {:id "graph-zoom-in" :title "larger" :on {:click [:graph/zoom :in]}} "+"]
      [:button {:id "graph-fit" :title "fit the width" :disabled (nil? zoom) :on {:click [:graph/zoom :fit]}} "fit"]]
     [:div.legend
      [:span [:span.swatch {:style {:background "var(--root)"}}] "the input's class"]
      [:span [:span.swatch {:style {:background "var(--selected)"}}] "opened or hovered"]
      [:span [:span.swatch {:style {:background "var(--added)"}}] "node added this step"]
      [:span [:span.swatch {:style {:border-color "var(--merged-ink)" :border-width "2px"}}] "class absorbed a merge"]
      [:span [:span.swatch {:style {:border-color "var(--added-ink)" :border-width "2px"}}] "new class"]
      [:span "a dashed edge closes a cycle; hover a box to light its row, click it to open the class"]]
     [:div.graph-scroll
      [:svg {:viewBox (str "0 0 " width " " height)
             :width (if zoom (* zoom width) width)
             :height (if zoom (* zoom height) height)
             :style (if zoom {} {:max-width "100%" :height "auto"})}
       [:defs (marker "arrow") (marker "arrow-lit")]
       (for [{:keys [from to back?] :as e} edges]
         (let [lit? (and lit (or (= lit from) (= lit to)))]
           [:path.edge {:class (cond-> [] back? (conj "back") lit? (conj "lit"))
                        :d (edge-path e)
                        :marker-end (if lit? "url(#arrow-lit)" "url(#arrow)")}]))
       (for [c classes] (class-view c marks))]]]))
