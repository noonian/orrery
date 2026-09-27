(ns orrery.views.classes
  "Renders the class list. The list has a row for every root of the
  e-graph. A row shows:

  - the nodes of the class;
  - its parents, as a count;
  - the best term of the class under the cost in force;
  - the normal form of the class, when the graph carries the
    polynomial analysis of bendix.

  Diff highlighting marks what the current step added or merged.
  When a class is open (`orrery.views.detail`, in the replay bar),
  its row is marked. The rows of the classes that it points at, and
  of the classes that point at it, say so."
  (:require [cromulent.core :as eg]
            [orrery.normal :as normal]
            [orrery.notation :as notation]
            [orrery.views.common :as common]))

(defn- form-view [g id mode best]
  (let [{:keys [kind term] atom-id :id} (normal/form g id (when best #(:term (best %))))]
    (case kind
      :polynomial (common/term-view term mode)
      :atom [:span.status (str "its own atom, " (notation/class-ref atom-id))]
      :too-big [:span.status "too big: the analysis gave up"]
      :conflict [:span.status "a contradiction"]
      nil)))

(defn class-list
  [{:keys [g diff best mode root selected hovered matches detail]}]
  (let [added (:added diff)
        absorbing (:absorbing diff)
        new-classes (:new-classes diff)
        dirty? (:dirty? g)
        normal? (normal/analysis? g)
        child-set (set (:children detail))
        parent-set (set (map :class (:parents detail)))]
    [:div
     [:div.legend
      [:span [:span.swatch {:style {:background "var(--added)"}}] "node added this step"]
      [:span [:span.swatch {:style {:background "var(--merged-ink)"}}] "class absorbed a merge"]
      [:span [:span.swatch {:style {:background "var(--added-ink)"}}] "new class"]
      [:span [:span.swatch {:style {:background "var(--root)"}}] "the input's class"]
      (when (seq matches) [:span [:span.swatch {:style {:background "var(--match)"}}] "a rule matches here"])
      [:span "a coloured id inside a node names a class, not a number"]
      [:span "click a class, or a #id, to open it in the bar above"]]
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
               [:td [:span.class-id {:on {:click [:select id]}} (notation/class-ref id)]
                (when (and detail (not= id (:root detail)))
                  (cond (contains? child-set id) [:span.rel "child"]
                        (contains? parent-set id) [:span.rel "parent"]))]
               (into [:td]
                     (for [node (sort-by pr-str (:nodes c))]
                       [:span.node {:class (when (contains? added-here node) "added")}
                        (common/enode-view node mode)]))
               (when normal? [:td.poly (form-view g id mode best)])
               [:td.best (when best (common/term-view (:term (best id)) mode))]
               [:td (str (count (:parents c)))]]))]]))
