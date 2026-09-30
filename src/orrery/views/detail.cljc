(ns orrery.views.detail
  "Renders the opened class: what the engine knows about the selected
  class in the e-graph on show (`orrery.derived/detail-at`). The
  view shows:

  - each node with its cost, and the cheapest node marked;
  - the terms that the class stands for;
  - the classes that it points at and the nodes that point at it;
  - where the class has been along the run;
  - how the class got its polynomial, when the graph carries the
    polynomial analysis of bendix.

  The view renders in the replay bar, which is stuck to the top of
  the viewport with the transport. So the opened class stays in view
  while the class list scrolls under it."
  (:require [clojure.string :as str]
            [orrery.costs :as costs]
            [orrery.eclass :as eclass]
            [orrery.notation :as notation]
            [orrery.snippets :as snippets]
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

(def ^:private why-words
  {:unknowns "fewer unknowns"
   :monomials "fewer monomials"
   :degree "a lower degree"
   :numbers "smaller numbers"
   :order "the fixed order of bendix.poly"})

(defn- form-text
  "Renders a form that `orrery.normal/workings` returns."
  [{:keys [kind term id]} mode]
  (case kind
    :polynomial (common/term-view term mode)
    :atom (list "an unknown of its own, " (ref-chip id))
    :too-big [:span.status "too big"]
    :conflict [:span.status "a contradiction"]
    nil))

(defn- reads-view
  "Renders a node read over its children's polynomials. A child that
  is not a single number, variable or unknown is put in parentheses,
  so the reader sees each child's polynomial whole."
  [[op & children :as reads] mode]
  (let [plain? (fn [t] (or (keyword? t) (symbol? t) (and (number? t) (not (neg? t)))))
        child (fn [t] (if (plain? t) (common/term-view t mode) (list "(" (common/term-view t mode) ")")))
        joined (fn [sep] (interpose sep (map child children)))]
    (case op
      :+ (joined " + ")
      :* (joined "·")
      :- (if (= 1 (count children)) (list "−" (child (first children))) (joined " − "))
      :neg (list "−" (child (first children)))
      :/ (joined " / ")
      (common/term-view reads mode))))

(defn- node-working
  "Renders one node of the class and what it makes."
  [{:keys [node kind reads form]} kept? mode]
  [:div.working
   [:span.dnode (common/enode-view node mode)]
   (when kept? [:span.kept "kept"])
   (case kind
     :number (list "is a number: " (form-text form mode))
     :variable (list "is a variable: " (form-text form mode))
     :unknown "is not ring arithmetic, so the ring cannot see inside it"
     :operation (if reads
                  (list "reads " (reads-view reads mode) " = " (form-text form mode))
                  (list "makes " (form-text form mode))))])

(defn- workings-view
  "Renders how the class got its polynomial."
  [{:keys [form nodes verdict why unknowns]} mode]
  [:div.detail-row.workings
   [:div [:span.label "polynomial"] (form-text form mode)]
   (for [n nodes]
     (node-working n (and (= :disagree verdict) (= form (:form n))) mode))
   (when (seq unknowns)
     [:div [:span.label "unknowns"]
      (interpose ", " (for [{:keys [id term]} unknowns]
                        (list (when term (list (common/term-view term mode) " ")) (ref-chip id))))])
   [:div.verdict
    (case verdict
      :one "The class has one node, so the class is worth what that node makes."
      :agree "Every node makes the same polynomial, so the nodes agree."
      :disagree (str "The nodes make different polynomials. That is an equation the ring cannot prove. "
                     "The class keeps the one marked kept, because it has "
                     (str/join " and " (distinct (map why-words why))) ".")
      :atom "No node is ring arithmetic, so the class is an unknown to every class that uses it."
      :too-big "The polynomial passed the limit of 200 monomials, and the analysis gave up on this class."
      :conflict "Two forms differ by a number, as x and x + 1 would. The e-graph asserts a contradiction."
      nil)]])

(defn detail-view
  "Renders the opened class.

  `mode` is the print mode. `cost-label` is the name of the cost in
  force. `labels` are the step labels of the run. `root-id` is the
  class of the input term in the e-graph on show."
  [{:keys [id root nodes cheapest count children parents history workings]}
   {:keys [mode cost-label labels root-id]}]
  [:div.class-detail
   [:div.detail-title
    [:span.class-id (notation/class-ref root)]
    (when (not= id root) [:span.status (str (notation/class-ref id) " is part of it now")])
    (when (= root root-id) [:span.status "the input's class"])
    [:span.spacer]
    (common/snippet-button "class-code" (:class snippets/panels))
    [:button.close {:title "close" :on {:click [:deselect]}} "×"]]
   [:div.detail-row.nodes
    [:span.label "nodes"]
    (for [{:keys [node cost best?]} nodes]
      [:span.dnode {:class (when best? "best") :title (when best? (str "the cheapest under " cost-label))}
       (common/enode-view node mode)
       [:span.cost (costs/cost-str cost)]])
    [:span.note {:id "detail-cost-note"} (str "costs under " cost-label ", cheapest outlined")]]
   (when workings (workings-view workings mode))
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
