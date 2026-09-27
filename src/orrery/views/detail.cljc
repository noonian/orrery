(ns orrery.views.detail
  "Renders the opened class: what the engine knows about the selected
  class in the e-graph on show (`orrery.derived/detail-at`). The
  view shows:

  - each node with its cost, and the cheapest node marked;
  - the terms that the class stands for;
  - the classes that it points at and the nodes that point at it;
  - where the class has been along the run.

  The view renders in the replay bar, which is stuck to the top of
  the viewport with the transport. So the opened class stays in view
  while the class list scrolls under it."
  (:require [orrery.costs :as costs]
            [orrery.eclass :as eclass]
            [orrery.notation :as notation]
            [orrery.views.common :as common]))

(defn- ref-chip [id]
  [:span.ref {:on {:click [:select id]}} (notation/class-ref id)])

(defn- count-text [n]
  (cond (= :infinite n) "infinitely many terms, since the class reaches itself"
        (>= n eclass/count-cap) "more than a million terms"
        (= 1 n) "one term"
        :else (str n " terms")))

(defn- step-links [labels ks]
  (interpose ", " (for [k ks] [:a.step {:title (nth labels k "") :on {:click [:step k]}} (str "step " k)])))

(defn detail-view
  "Renders the opened class.

  `mode` is the print mode. `cost-label` is the name of the cost in
  force. `labels` are the step labels of the run. `root-id` is the
  class of the input term in the e-graph on show."
  [{:keys [id root nodes cheapest count children parents history]}
   {:keys [mode cost-label labels root-id]}]
  [:div.class-detail
   [:div.detail-title
    [:span.class-id (notation/class-ref root)]
    (when (not= id root) [:span.status (str (notation/class-ref id) " is part of it now")])
    (when (= root root-id) [:span.status "the input's class"])
    [:span.spacer]
    [:button.close {:title "close" :on {:click [:deselect]}} "×"]]
   [:div.detail-row.nodes
    [:span.label "nodes"]
    (for [{:keys [node cost best?]} nodes]
      [:span.dnode {:class (when best? "best") :title (when best? (str "the cheapest under " cost-label))}
       (common/enode-view node mode)
       [:span.cost (costs/cost-str cost)]])]
   [:div.detail-row.terms
    [:span.label "stands for"]
    (count-text count)
    (when (seq cheapest)
      (list (if (and (number? count) (<= count (clojure.core/count cheapest)))
              (str ", under " cost-label ": ")
              (str "; the cheapest under " cost-label ": "))
            (interpose ", " (for [{:keys [term cost]} cheapest]
                              [:span.costed (common/term-view term mode) [:span.cost (costs/cost-str cost)]]))))]
   [:div.detail-row.children
    [:span.label "points at"]
    (if (seq children)
      (interpose ", " (for [c children] (ref-chip c)))
      "nothing: a leaf")]
   [:div.detail-row.parents
    [:span.label "pointed at by"]
    (if (seq parents)
      (for [{:keys [node class]} parents]
        [:span.dnode (common/enode-view node mode) [:span.cost "in " (ref-chip class)]])
      "nothing")]
   (when history
     (let [{:keys [born grew merged]} history]
       [:div.detail-row.history
        [:span.label "along the run"]
        "first at " (step-links labels [born])
        (when (seq grew) (list "; gained nodes at " (step-links labels grew)))
        (when (seq merged) (list "; classes became one at " (step-links labels merged)))]))])
