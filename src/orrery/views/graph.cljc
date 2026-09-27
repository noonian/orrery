(ns orrery.views.graph
  "Renders the picture of the e-graph on show (`orrery.graph`) as
  SVG. The picture has a box for each class, with the nodes of the
  class inside. It has an edge from each child slot of a node to the
  class that the slot points at. It carries the marks that the class
  list uses:

  - the input's class;
  - the opened class and the hovered class;
  - what the step added or merged;
  - where a rule matches.

  Hovering a box lights its row in the class list, and clicking a
  box opens the class, as in the tree. The controls zoom in and out,
  fit the width, and restrict the picture to what the opened class
  reaches.

  The picture is off by default, because it takes up room. The
  exception is the introduction, where the e-graph is small and the
  prose points at the picture. The tools row above the picture turns
  it on and off. Every handler is data: an `[action & args]` vector
  over the actions in `orrery.actions`."
  (:require [orrery.graph :as graph]
            [orrery.notation :as notation]))

(defn- edge-path
  "Returns the SVG path of an edge: a cubic curve from the slot of
  the node to the top of the class. An edge that closes a cycle dips
  under both ends and comes back up to the foot of the class."
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
  "Renders the graph panel. Takes a map with these keys:

  - `layout` is the layout that `orrery.graph/layout` returns.
  - `root` is the input's class.
  - `selected` is the opened class and `hovered` is the lit class.
  - `diff` is what the step changed.
  - `matches` holds the classes that a rule matches.
  - `zoom` is a factor over the natural size, or nil to fit the
    width.
  - `filter?` says whether the picture is restricted to what the
    opened class reaches.
  - `filterable?` says whether a class is open to filter by."
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
