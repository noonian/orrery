(ns orrery.normal
  "The normal form shown beside each class of an e-graph that carries
  bendix's polynomial analysis. The normal form is one of:

    - the class's polynomial as a term, spelled over the atoms the
      polynomial mentions (a variable as itself, an opaque class as
      #id)
    - the fact that the class is its own atom
    - the fact that the class has given up (:too-big)
    - the fact that the class holds a contradiction (:conflict)

  No e-graph visualizer has this view (IDEA.md section 3)."
  (:require [bendix.analysis :as an]
            [bendix.poly :as poly]
            [bendix.term :as bt]
            [cromulent.core :as eg]
            [orrery.notation :as notation]))

(defn analysis?
  "Returns true when `g` carries the polynomial analysis."
  [g]
  (boolean (some #(= :poly (:name %)) (:analyses g))))

(defn- atom->term
  "Returns the term that `a`, an atom of a polynomial, prints as.

    - A variable prints as itself.
    - An opaque class prints as the term that `render` returns for
      its id. The class's best term is an example. The class prints
      as #id when there is no `render`, or when `render` returns
      nil.
    - A placeholder prints with each of its class references
      replaced in the same way."
  [render a]
  (let [class-term (fn [id] (or (when render (render id)) (symbol (notation/class-ref id))))]
    (cond (keyword? a) a
          (bt/placeholder? a) (bt/map-class-ids class-term a)
          :else (class-term a))))

(defn form
  "Returns the class with id `id` as the analysis sees it. The result
  is one of {:kind :polynomial :term t}, {:kind :atom :id id},
  {:kind :too-big}, {:kind :conflict}, or nil when `g` has no such
  data.

  `render`, when given, is (fn [id] term). It is called for the
  opaque classes that the polynomial mentions. Without `render`
  those classes print as #id."
  ([g id] (form g id nil))
  ([g id render]
   (when (analysis? g)
     (let [d (an/canonical g (eg/data g id :poly))]
       (cond (an/polynomial? d) {:kind :polynomial :term (poly/->term d #(atom->term render %))}
             (an/atom? d) {:kind :atom :id (:atom d)}
             (keyword? d) {:kind d}
             :else nil)))))
