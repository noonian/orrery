(ns orrery.normal
  "The normal form beside each class of an e-graph that carries
  bendix's polynomial analysis: the class's polynomial as a term,
  spelled over the atoms it mentions (a variable as itself, an opaque
  class as #id), or the fact that the class is its own atom, has
  given up, or holds a contradiction. The view no e-graph visualizer
  has (IDEA.md section 3)."
  (:require [bendix.analysis :as an]
            [bendix.poly :as poly]
            [bendix.term :as bt]
            [cromulent.core :as eg]
            [orrery.lay :as lay]))

(defn analysis?
  "Does g carry the polynomial analysis?"
  [g]
  (boolean (some #(= :poly (:name %)) (:analyses g))))

(defn- atom->term
  "How an atom of a polynomial prints: a variable as itself, an opaque
  class by render (a term for its id, the class's best term say) or
  as #id, a placeholder likewise over its class references."
  [render a]
  (let [class-term (fn [id] (or (when render (render id)) (symbol (lay/class-ref id))))]
    (cond (keyword? a) a
          (bt/placeholder? a) (bt/map-class-ids class-term a)
          :else (class-term a))))

(defn form
  "The class of id as its analysis sees it: {:kind :polynomial :term t},
  {:kind :atom :id id}, {:kind :too-big}, {:kind :conflict}, or nil
  when g has no such data. render, when given, is (fn [id] term) for
  the opaque classes the polynomial mentions; without it they print
  as #id."
  ([g id] (form g id nil))
  ([g id render]
   (when (analysis? g)
     (let [d (an/canonical g (eg/data g id :poly))]
       (cond (an/polynomial? d) {:kind :polynomial :term (poly/->term d #(atom->term render %))}
             (an/atom? d) {:kind :atom :id (:atom d)}
             (keyword? d) {:kind d}
             :else nil)))))
