(ns orrery.views.tree
  "A term as a tree, each node annotated with its class in the
  e-graph; hovering a node lights every node of the same class, in
  the tree and in the class list, which is how sharing shows without
  drawing an edge."
  (:require [cromulent.core :as eg]
            [orrery.lay :as lay]))

(defn annotate
  "The term with the class id of every subterm. `eg/add` on a term the
  graph holds adds nothing and returns its class."
  [g t]
  (let [[_ id] (eg/add g t)]
    {:term t :id id
     :children (when (vector? t) (mapv #(annotate g %) (rest t)))}))

(defn- node-view [{:keys [term id children]} hovered on-hover]
  [:div.tnode {:class (when (= id hovered) "hl")
               :on {:mouseover (fn [e] (.stopPropagation e) (on-hover id))
                    :mouseout (fn [e] (.stopPropagation e) (on-hover nil))}}
   [:div.tlabel
    [:span.top (if (vector? term) (name (first term)) (lay/leaf-str term))]
    [:span.tid (lay/class-ref id)]]
   (when (seq children)
     (into [:div.tchildren] (map #(node-view % hovered on-hover) children)))])

(defn tree-view [{:keys [g term hovered on-hover]}]
  (let [n (count (tree-seq vector? rest term))]
    [:div
     [:div.legend {:id "tree-counts"}
      [:span (str "tree nodes " n)] [:span (str "graph nodes " (eg/node-count g))]
      [:span "hover a node to see its class"]]
     [:div.tree (node-view (annotate g term) hovered on-hover)]]))
