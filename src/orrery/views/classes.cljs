(ns orrery.views.classes
  "The class list: every root of the e-graph, its nodes, its parents,
  and the best term of the class under the cost in force. Diff
  highlighting marks what the current step added or merged."
  (:require [cromulent.core :as eg]
            [orrery.lay :as lay]
            [orrery.views.common :as common]))

(defn- ref-chip [id on-select]
  [:span.ref {:on {:click #(on-select id)}} (lay/class-ref id)])

(defn class-list
  [{:keys [g diff best mode root selected on-select]}]
  (let [added (:added diff)
        absorbing (:absorbing diff)
        new-classes (:new-classes diff)
        dirty? (:dirty? g)]
    [:div
     [:div.legend
      [:span [:span.swatch {:style {:background "var(--added)"}}] "node added this step"]
      [:span [:span.swatch {:style {:background "var(--merged-ink)"}}] "class absorbed a merge"]
      [:span [:span.swatch {:style {:background "var(--added-ink)"}}] "new class"]
      [:span [:span.swatch {:style {:background "var(--root)"}}] "the input's class"]]
     [:table.classes
      [:thead [:tr [:th "class"] [:th "nodes"] [:th "best"] [:th "parents"]]]
      (into [:tbody]
            (for [id (eg/roots g)
                  :let [c (eg/eclass g id)
                        added-here (get added id #{})]]
              [:tr {:replicant/key id
                    :class (cond-> []
                             (= id root) (conj "root")
                             (= id selected) (conj "selected")
                             (contains? absorbing id) (conj "absorbing")
                             (contains? new-classes id) (conj "new-class")
                             dirty? (conj "dirty"))}
               [:td [:span.class-id {:on {:click #(on-select id)}} (lay/class-ref id)]]
               (into [:td]
                     (for [node (sort-by pr-str (:nodes c))]
                       [:span.node {:class (when (contains? added-here node) "added")}
                        (common/enode-view node mode on-select)]))
               [:td (when best (common/term-view (:term (best id)) mode))]
               [:td (str (count (:parents c)))]]))]]))
