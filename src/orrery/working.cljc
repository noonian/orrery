(ns orrery.working
  "Works one operation of bendix's ring through, monomial by
  monomial, so that a lesson can show the arithmetic that the
  polynomial analysis does. The prose widget `[:working op & terms]`
  renders what `work` returns. The operations are:

    :product     [:working :product a b] multiplies the polynomial
                 of a by the polynomial of b. Each monomial of a
                 meets each monomial of b, and like terms are
                 collected.
    :reduction   [:working :reduction t base q] reduces the
                 polynomial of t modulo base² = q, as the pythagoras
                 rule does. Each power of base past the first
                 becomes a power of q.
    :derivative  [:working :derivative t x] differentiates the
                 polynomial of t by the variable x, one monomial at
                 a time.

  Every term is read by bendix's polynomial analysis, in one e-graph,
  so an opaque subterm such as sin x is the same atom wherever it
  appears. The atom prints as the subterm it came from."
  (:require [bendix.analysis :as an]
            [bendix.core :as bx]
            [bendix.poly :as poly]
            [bendix.term :as bt]
            [cromulent.core :as eg]
            [orrery.normal :as normal]))

(def limit
  "The most monomials a worked product shows along each side. Past
  it, the widget shows only the result."
  5)

(defn- read-terms
  "Adds `terms` to one e-graph that carries the polynomial analysis.
  Returns the polynomial of each term, or nil for a term that is not
  worth a polynomial, and a function that prints an atom as the
  subterm it came from."
  [terms]
  (let [g (reduce (fn [g t] (first (eg/add g t))) (bx/egraph) terms)
        g (eg/rebuild g)
        class-of (fn [t] (eg/find g (second (eg/add g t))))
        subterms (into {} (for [t (reverse (mapcat #(tree-seq vector? rest %) terms))]
                            [(class-of t) t]))
        polys (for [t terms]
                (let [d (an/canonical g (eg/data g (class-of t) :poly))]
                  (cond (an/polynomial? d) d
                        (an/atom? d) (poly/variable (:atom d))
                        :else nil)))]
    {:polys (vec polys)
     :class-of class-of
     :render (fn [a] (if (bt/class-id? a) (get subterms a a) a))}))

(defn- monomials
  "Returns the monomials of `p` in canonical order, each as a
  polynomial of one term."
  [p]
  (for [[m c] (poly/sorted-terms p)] {m c}))

(defn- product [[a b]]
  (let [{[p q] :polys render :render} (read-terms [a b])]
    (when (and p q)
      (let [->t #(normal/spell (poly/->term % render))
            rows (monomials p)
            cols (monomials q)
            result (poly/mul p q)
            cells (for [r rows] (for [c cols] (poly/mul r c)))
            ;; the monomials that more than one cell makes, and what they sum to
            by-monomial (group-by #(ffirst (keys %)) (apply concat cells))
            like (into {} (for [[m ps] by-monomial :when (< 1 (count ps))] [m (poly/sum ps)]))
            mark (fn [cell] (let [m (ffirst (keys cell))]
                              (cond (not (contains? like m)) nil
                                    (= poly/zero (get like m)) :cancels
                                    :else :combines)))]
        {:op :product
         :a (->t p) :b (->t q)
         :grid? (and (<= (count rows) limit) (<= (count cols) limit))
         :rows (mapv ->t rows)
         :cols (mapv ->t cols)
         :cells (mapv (fn [row] (mapv (fn [cell] {:term (->t cell) :mark (mark cell)}) row)) cells)
         :like (vec (for [m (distinct (map #(ffirst (keys %)) (apply concat cells)))
                          :when (contains? like m)]
                      {:parts (mapv ->t (get by-monomial m))
                       :sum (->t (get like m))
                       :cancels? (= poly/zero (get like m))}))
         :result (->t result)}))))

(defn- reduction [[t base q]]
  (let [{[p b q'] :polys render :render class-of :class-of} (read-terms [t base q])
        a (class-of base)]
    (when (and p b q' (= b (poly/variable a)))
      (let [->t #(normal/spell (poly/->term % render))]
        {:op :reduction
         :base (->t b) :q (->t q')
         :term (->t p)
         :steps (vec (for [m (monomials p)
                           :when (<= 2 (get (ffirst m) a 0))]
                       {:before (->t m) :after (->t (poly/reduce-square m a q'))}))
         ;; each monomial replaced, before like terms are collected
         :substituted (let [ts (for [m (monomials p)]
                                 (->t (if (<= 2 (get (ffirst m) a 0)) (poly/reduce-square m a q') m)))]
                        (if (= 1 (count ts)) (first ts) (into [:+] ts)))
         :result (->t (poly/reduce-square p a q'))}))))

(defn- derivative [[t x]]
  (let [{[p] :polys render :render} (read-terms [t])]
    (when (and p (bt/variable? x))
      (let [->t #(normal/spell (poly/->term % render))]
        {:op :derivative
         :term (->t p) :x x
         :steps (vec (for [m (monomials p)] {:before (->t m) :after (->t (poly/derivative m x))}))
         :result (->t (poly/derivative p x))}))))

(defn- work*
  [op args]
  (case op
    :product (product args)
    :reduction (reduction args)
    :derivative (derivative args)
    nil))

(def work
  "Returns the working of `op` over `args`, or nil when a term is not
  worth a polynomial (or, for :reduction, when base is not an atom of
  its own). See the namespace docstring for the operations. A
  working depends only on its arguments, so it is kept once made."
  (memoize work*))
