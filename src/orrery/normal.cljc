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
            [bendix.num :as num]
            [bendix.poly :as poly]
            [bendix.term :as bt]
            [cromulent.core :as eg]
            [cromulent.term :as term]
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

(defn- negative
  "Returns the monomial term `t` with its sign turned when its number
  is negative, and nil otherwise."
  [t]
  (let [neg-num? (fn [x] (and (num/rational? x) (neg? (num/cmp x 0))))]
    (cond (neg-num? t) (num/neg t)
          (and (vector? t) (= :* (first t)) (neg-num? (second t)))
          (let [c (num/neg (second t)), fs (drop 2 t)]
            (cond (not= 1 c) (into [:* c] fs)
                  (= 1 (count fs)) (first fs)
                  :else (into [:*] fs)))
          :else nil)))

(defn spell
  "Returns the term `t` of a polynomial, as bendix.poly/->term writes
  it, spelled for a reader: a monomial with a negative number is
  subtracted, and a positive monomial comes first. So x² + −1 is
  spelled x² − 1, and −1·cos²x + 1 is spelled 1 − cos²x. A sum with
  no negative monomial is left as it is. The value is
  the same; only the spelling changes."
  [t]
  (if (and (vector? t) (= :+ (first t)) (some negative (rest t)))
    (let [ts (rest t)
          i (or (first (keep-indexed (fn [i t] (when-not (negative t) i)) ts)) 0)
          [lead & more] (cons (nth ts i) (concat (take i ts) (drop (inc i) ts)))]
      ;; a monomial is never a sum, so a sum in acc is one made here, and it grows n-ary
      (reduce (fn [acc t] (cond (negative t) [:- acc (negative t)]
                                (and (vector? acc) (= :+ (first acc))) (conj acc t)
                                :else [:+ acc t]))
              (if-let [p (negative lead)] [:neg p] lead)
              more))
    (if-let [p (and (vector? t) (negative t))] [:neg p] t)))

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
       (cond (an/polynomial? d) {:kind :polynomial :term (spell (poly/->term d #(atom->term render %)))}
             (an/atom? d) {:kind :atom :id (:atom d)}
             (keyword? d) {:kind d}
             :else nil)))))

;; ---------------------------------------------------------------------------
;; how a class got its polynomial

(defn- the-make [g]
  (some #(when (= :poly (:name %)) (:make %)) (:analyses g)))

(defn- opaque-count [p]
  (count (filter bt/class-id? (poly/atoms p))))

(defn- coefficient-size
  "Returns the sum over the numbers of `p` of |numerator| + denominator,
  the size that bendix.poly compares."
  [p]
  (reduce num/add 0 (map #(num/add (num/abs (num/numerator %)) (num/denominator %)) (vals p))))

(defn why-kept
  "Returns a word for why the analysis keeps the polynomial `kept`
  over `other`, following the order of bendix.analysis: fewer
  unknowns first, then bendix.poly's order, which puts fewer
  monomials first, then lower degree, then smaller numbers. Returns
  :order when all of those tie and the fixed order decides."
  [kept other]
  (some (fn [[k f]] (when (< (f kept) (f other)) k))
        [[:unknowns opaque-count]
         [:monomials poly/term-count]
         [:degree poly/degree]
         [:numbers #(num/->double (coefficient-size %))]
         [:order (constantly 0)]]))

(defn- node-kind [node]
  (cond (bt/constant? node) :number
        (bt/variable? node) :variable
        :else :operation))

(defn workings
  "Returns how the class with id `id` got its polynomial, or nil when
  `g` does not carry the polynomial analysis. The result holds:

    :form     the form the class keeps, as `form` returns it
    :nodes    for each node of the class, a map of
                :node   the node
                :kind   :number, :variable, :operation (a ring
                        operation), or :unknown (the ring cannot see
                        inside it, so its class is an atom)
                :reads  the node with each child replaced by the
                        child's polynomial as a term, for a ring
                        operation whose children all have one
                :form   what the node alone makes, as `form` returns
                        it
    :verdict  :one (a single node), :agree (every node makes the
              kept form), :disagree (the nodes make different
              polynomials, and the class keeps one), or the kind of
              the kept form when it is not a polynomial
    :why      for :disagree, why the kept form beat each other one,
              as `why-kept` returns it
    :unknowns the opaque classes that the kept polynomial mentions,
              each as {:id id :term t}, t spelled by `render` (nil
              without one)

  `render` spells the opaque atoms, as it does for `form`."
  ([g id] (workings g id nil))
  ([g id render]
   (when (analysis? g)
     (let [root (eg/find g id)
           make (the-make g)
           kept (an/canonical g (eg/data g root :poly))
           as-form (fn [d] (cond (an/polynomial? d) {:kind :polynomial :term (spell (poly/->term d #(atom->term render %)))}
                                 (an/atom? d) {:kind :atom :id (:atom d)}
                                 (keyword? d) {:kind d}))
           child-term (fn [c] (let [f (form g c render)]
                                (case (:kind f)
                                  :polynomial (:term f)
                                  :atom (atom->term render (:id f))
                                  nil)))
           nodes (vec (for [n (eg/nodes g root)
                            :let [d (an/canonical g (make g (eg/canonicalize g n) root))
                                  kind (if (and (= :operation (node-kind n)) (an/atom? d)) :unknown (node-kind n))
                                  reads (when (= :operation kind)
                                          (let [ts (map child-term (term/children n))]
                                            (when (every? some? ts) (into [(term/operator n)] ts))))]]
                        {:node n :kind kind :reads reads :d d :form (as-form d)}))
           polys (distinct (filter an/polynomial? (map :d nodes)))
           verdict (cond (not (an/polynomial? kept)) (:kind (as-form kept))
                         (= 1 (count nodes)) :one
                         (every? #(= kept %) polys) :agree
                         :else :disagree)]
       {:form (as-form kept)
        :nodes (mapv #(dissoc % :d) nodes)
        :verdict verdict
        :why (when (= :disagree verdict)
               (vec (for [p polys :when (not= p kept)] (why-kept kept p))))
        :unknowns (when (an/polynomial? kept)
                    (vec (for [a (sort (filter bt/class-id? (poly/atoms kept)))]
                           {:id a :term (when render (render a))})))}))))
