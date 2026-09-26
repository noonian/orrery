(ns orrery.views.classes
  "The class list: every root of the e-graph, its nodes, its parents,
  and the best term of the class under the cost in force, and, when
  the graph carries bendix's polynomial analysis, the normal form
  of each class. Diff highlighting marks what the current step added
  or merged."
  (:require [cromulent.core :as eg]
            [orrery.notation :as notation]
            [orrery.normal :as normal]
            [orrery.views.common :as common]))

(defn- form-view [g id mode best]
  (let [{:keys [kind term] atom-id :id} (normal/form g id (when best #(:term (best %))))]
    (case kind
      :polynomial (common/term-view term mode)
      :atom [:span.status (str "its own atom, " (notation/class-ref atom-id))]
      :too-big [:span.status "too big: the analysis gave up"]
      :conflict [:span.status "a contradiction"]
      nil)))

(defn- ref-chip [id on-select]
  [:span.ref {:on {:click #(on-select id)}} (notation/class-ref id)])

(defn class-list
  [{:keys [g diff best mode root selected hovered matches on-select]}]
  (let [added (:added diff)
        absorbing (:absorbing diff)
        new-classes (:new-classes diff)
        dirty? (:dirty? g)
        normal? (normal/analysis? g)]
    [:div
     [:div.legend
      [:span [:span.swatch {:style {:background "var(--added)"}}] "node added this step"]
      [:span [:span.swatch {:style {:background "var(--merged-ink)"}}] "class absorbed a merge"]
      [:span [:span.swatch {:style {:background "var(--added-ink)"}}] "new class"]
      [:span [:span.swatch {:style {:background "var(--root)"}}] "the input's class"]
      (when (seq matches) [:span [:span.swatch {:style {:background "var(--match)"}}] "a rule matches here"])]
     [:table.classes
      [:thead [:tr [:th "class"] [:th "nodes"] (when normal? [:th "polynomial"]) [:th "best"] [:th "parents"]]]
      (into [:tbody]
            (for [id (eg/roots g)
                  :let [c (eg/eclass g id)
                        added-here (get added id #{})]]
              [:tr {:replicant/key id
                    :class (cond-> []
                             (= id root) (conj "root")
                             (= id selected) (conj "selected")
                             (= id hovered) (conj "hovered")
                             (contains? matches id) (conj "match")
                             (contains? absorbing id) (conj "absorbing")
                             (contains? new-classes id) (conj "new-class")
                             dirty? (conj "dirty"))}
               [:td [:span.class-id {:on {:click #(on-select id)}} (notation/class-ref id)]]
               (into [:td]
                     (for [node (sort-by pr-str (:nodes c))]
                       [:span.node {:class (when (contains? added-here node) "added")}
                        (common/enode-view node mode on-select)]))
               (when normal? [:td.poly (form-view g id mode best)])
               [:td (when best (common/term-view (:term (best id)) mode))]
               [:td (str (count (:parents c)))]]))]]))
