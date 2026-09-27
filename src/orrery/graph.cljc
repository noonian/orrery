(ns orrery.graph
  "Lays out the e-graph as a picture. Every class is a box that
  holds its nodes. An edge runs from each child slot of a node to
  the class that the slot points at. The class list says the same
  in words.

  Boxes sit in layers by height, with leaves at the bottom. So every
  edge points down, except an edge that closes a cycle (x = x + 0),
  which is drawn back up.

  Within a layer, the boxes are ordered by the barycenter of their
  neighbours, over a few sweeps, with ties broken by class id. So
  the picture is a function of the e-graph value. The counts are the
  same on every runtime, and the positions follow the ids.

  The layout is pure, uses integer coordinates, and is the page's
  own code. The egraphs-good visualizer was weighed and not taken
  (IDEA.md section 9)."
  (:require [cromulent.core :as eg]
            [cromulent.term :as term]))

(def char-w "Width of one character of a label, monospace at 12px." 7)
(def node-h 22)
(def node-pad 12)
(def node-gap 6)
(def box-pad 8)
(def head-h "The line at the top of a box: the class id and its label." 16)
(def per-row "Nodes per row inside a box." 6)
(def col-gap 28)
(def layer-gap 48)
(def margin 12)

(defn reachable
  "Returns the canonical classes that the class of `id` reaches,
  including itself."
  [g id]
  (loop [todo [(eg/find g id)] seen #{}]
    (if-let [c (peek todo)]
      (let [todo (pop todo)]
        (if (contains? seen c)
          (recur todo seen)
          (recur (into todo (for [n (eg/nodes g c) :when (term/compound? n), ch (term/children n)] (eg/find g ch)))
                 (conj seen c))))
      seen)))

(defn- heights
  "Returns a map from class to height. The height is 0 for a class
  whose nodes are all leaves. Otherwise it is one more than the
  height of its highest drawn child. An edge that leads back into a
  class on the way down is not counted."
  [g classes]
  (let [drawn (set classes)
        state (volatile! {})
        walk (fn walk [c]
               (let [seen (get @state c)]
                 (cond (= :visiting seen) nil
                       (some? seen) seen
                       :else
                       (do (vswap! state assoc c :visiting)
                           (let [h (reduce (fn [h n]
                                             (if (term/compound? n)
                                               (reduce (fn [h ch]
                                                         (let [ch (eg/find g ch)]
                                                           (if-let [hc (when (contains? drawn ch) (walk ch))]
                                                             (max h (inc hc))
                                                             h)))
                                                       h (term/children n))
                                               h))
                                           0 (eg/nodes g c))]
                             (vswap! state assoc c h)
                             h)))))]
    (doseq [c classes] (walk c))
    @state))

(defn- text-w [s] (+ node-pad (* char-w (count s))))

(defn- box
  "Returns the box of class `c`: its size, and its nodes placed
  inside it in rows of `per-row`. Each row is centred. Positions
  are relative to the box."
  [c nodes labels sub]
  (let [ws (mapv text-w labels)
        rows (vec (partition-all per-row (range (count nodes))))
        row-w (fn [row] (+ (reduce + 0 (map ws row)) (* node-gap (dec (count row)))))
        head (str "#" c (when sub (str "  " sub)))
        w (+ (* 2 box-pad) (max (text-w head) (reduce max 0 (map row-w rows))))
        h (+ (* 2 box-pad) head-h (* (count rows) node-h) (* node-gap (max 0 (dec (count rows)))))
        placed (vec (apply concat
                           (map-indexed
                            (fn [ri row]
                              (let [x0 (quot (- w (row-w row)) 2)
                                    y (+ box-pad head-h (* ri (+ node-h node-gap)))]
                                (first (reduce (fn [[acc x] i]
                                                 [(conj acc {:node (nth nodes i) :label (nth labels i)
                                                             :x x :y y :w (nth ws i) :h node-h})
                                                  (+ x (nth ws i) node-gap)])
                                               [[] x0] row))))
                            rows)))]
    {:id c :w w :h h :sub sub :nodes placed}))

(defn- pack
  "Returns a map from class to the centre x of its box. Each layer
  is laid out left to right in its order, with `col-gap` between
  boxes, and is centred on the widest layer."
  [layers boxes]
  (let [width (fn [layer] (+ (reduce + 0 (map #(:w (boxes %)) layer)) (* col-gap (max 0 (dec (count layer))))))
        widest (reduce max 0 (map width layers))]
    (into {}
          (mapcat (fn [layer]
                    (let [x0 (+ margin (quot (- widest (width layer)) 2))]
                      (first (reduce (fn [[acc x] c]
                                       (let [w (:w (boxes c))]
                                         [(assoc acc c (+ x (quot w 2))) (+ x w col-gap)]))
                                     [{} x0] layer)))))
          layers)))

(defn- order
  "Returns the layers with their boxes sorted by the barycenter of
  the centres of their neighbours. A sweep down orders by parents,
  and a sweep up orders by children. There are two sweeps of each.
  A box with no neighbours keeps its place. Ties are broken by id."
  [layers boxes parents children]
  (loop [layers layers k 0]
    (if (= 4 k)
      layers
      (let [xs (pack layers boxes)
            near (if (even? k) parents children)
            bary (fn [c] (let [ns (near c)] (if (seq ns) (/ (reduce + 0 (map xs ns)) (count ns)) (xs c))))]
        (recur (mapv (fn [layer] (vec (sort-by (fn [c] [(bary c) c]) layer))) layers)
               (inc k))))))

(defn layout
  "Returns the picture of `g`:

    {:width w :height h
     :classes [{:id c :x :y :w :h :sub label :nodes [{:node n :label s :x :y :w :h}]}]
     :edges   [{:from c :to c' :node n :slot i :x1 :y1 :x2 :y2 :back? b}]
     :layers  n}

  Coordinates are integers. They are absolute, except that the
  position of a node inside a box is relative to the box. An edge
  leaves slot `i` of node `n` at the foot of the node. It enters
  the box of `c'` at the top, or at the foot when the edge closes a
  cycle.

  Options:

    :only         The set of classes to draw. Every class is drawn
                  otherwise.
    :node-label   `(fn [node] string)`.
    :class-label  `(fn [id] string or nil)`. Gives a second label
                  for the head of the box, such as the polynomial."
  [g {:keys [only node-label class-label] :or {node-label pr-str class-label (constantly nil)}}]
  (let [classes (if only (vec (sort only)) (eg/roots g))
        drawn (set classes)
        nodes-of (into {} (map (fn [c] [c (vec (sort-by pr-str (eg/nodes g c)))])) classes)
        children (into {} (map (fn [c] [c (vec (distinct (for [n (nodes-of c) :when (term/compound? n)
                                                                ch (term/children n)
                                                                :let [ch (eg/find g ch)] :when (contains? drawn ch)]
                                                            ch)))]))
                       classes)
        parents (reduce (fn [m [c chs]] (reduce (fn [m ch] (update m ch (fnil conj []) c)) m chs)) {} children)
        hs (heights g classes)
        top (reduce max 0 (vals hs))
        layer-of (fn [c] (- top (get hs c)))
        boxes (into {} (map (fn [c] [c (box c (nodes-of c) (mapv node-label (nodes-of c)) (class-label c))])) classes)
        layers (order (mapv (fn [i] (filterv #(= i (layer-of %)) classes)) (range (inc top))) boxes parents children)
        xs (pack layers boxes)
        layer-h (mapv (fn [layer] (reduce max 0 (map #(:h (boxes %)) layer))) layers)
        ys (vec (reductions (fn [y i] (+ y (nth layer-h i) layer-gap)) margin (range (count layers))))
        placed (mapv (fn [c] (let [b (boxes c)]
                               (assoc b :x (- (get xs c) (quot (:w b) 2)) :y (nth ys (layer-of c)))))
                     classes)
        by-id (into {} (map (fn [b] [(:id b) b])) placed)
        edges (vec (for [b placed, {:keys [node x y w h]} (:nodes b) :when (term/compound? node)
                         :let [chs (term/children node) n (count chs)]
                         [i ch] (map-indexed vector chs)
                         :let [ch (eg/find g ch)] :when (contains? drawn ch)]
                     (let [t (get by-id ch)
                           back? (<= (layer-of ch) (layer-of (:id b)))]
                       {:from (:id b) :to ch :node node :slot i
                        :x1 (+ (:x b) x (quot (* w (inc i)) (inc n))) :y1 (+ (:y b) y h)
                        :x2 (+ (:x t) (quot (:w t) 2)) :y2 (if back? (+ (:y t) (:h t)) (:y t))
                        :back? back?})))]
    {:width (+ margin (reduce max 0 (map #(+ (:x %) (:w %)) placed)))
     :height (+ margin (reduce max 0 (map #(+ (:y %) (:h %)) placed)))
     :classes placed
     :edges edges
     :layers (count layers)}))
