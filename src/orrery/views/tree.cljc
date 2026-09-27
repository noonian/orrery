(ns orrery.views.tree
  "Renders a term as a tree. Each node is annotated with its class in
  the e-graph. Hovering a node lights every node of the same class,
  in the tree and in the class list. That is how sharing shows
  without drawing an edge. Clicking a node opens its class. The tree
  handlers stop propagation, because the nodes nest."
  (:require [cromulent.core :as eg]
            [orrery.notation :as notation]))

(defn annotate
  "Returns the term `t` with the class id of every subterm. Calling
  `eg/add` on a term that the graph holds adds nothing and returns
  the class of the term."
  [g t]
  (let [[_ id] (eg/add g t)]
    {:term t :id id
     :children (when (vector? t) (mapv #(annotate g %) (rest t)))}))

(defn- node-view [{:keys [term id children]} hovered]
  [:div.tnode {:class (when (= id hovered) "hl")
               :on {:mouseover [:tree/hover id]
                    :mouseout [:tree/hover nil]
                    :click [:tree/open id]}}
   [:div.tlabel
    [:span.top (if (vector? term) (name (first term)) (notation/leaf-str term))]
    [:span.tid (notation/class-ref id)]]
   (when (seq children)
     (into [:div.tchildren] (map #(node-view % hovered) children)))])

(defn tree-view [{:keys [g term hovered]}]
  (let [n (count (tree-seq vector? rest term))]
    [:div
     [:div.legend {:id "tree-counts"}
      [:span (str "tree nodes " n)] [:span (str "graph nodes " (eg/node-count g))]
      [:span "hover a node to see its class; click it to open the class"]]
     [:div.tree (node-view (annotate g term) hovered)]]))
