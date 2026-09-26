(ns orrery.eclass
  "What the page says about one class when it is selected: the class
  of a term, each node of the class with its cost under the cost in
  force, the classes its nodes point at and the nodes that point at
  it, how many terms the class stands for and the cheapest few of
  them, and where the class has been along a run. Pure; counts and
  costs are the same on every runtime, ids are not."
  (:refer-clojure :exclude [parents])
  (:require [cromulent.core :as eg]
            [cromulent.term :as term]))

(defn class-of
  "The canonical class holding t in g, or nil when g does not hold it.
  Looks up, never adds."
  [g t]
  (if (term/compound? t)
    (let [ids (mapv #(class-of g %) (term/children t))]
      (when (every? some? ids)
        (eg/lookup g (term/make (term/operator t) ids))))
    (eg/lookup g t)))

(defn children
  "The classes the nodes of the class of id point at, ascending."
  [g id]
  (->> (eg/nodes g id)
       (filter term/compound?)
       (mapcat term/children)
       (map #(eg/find g %))
       distinct
       sort
       vec))

(defn parents
  "The nodes that hold the class of id as a child, each with the class
  it is in: [{:node n :class c}], the node canonical. Between a union
  and the rebuild two parents may read the same and sit in two
  classes, which is the invariant congruence restores."
  [g id]
  (->> (:parents (eg/eclass g id))
       (map (fn [[node c]] {:node (eg/canonicalize g node) :class (eg/find g c)}))
       distinct
       (sort (fn [a b]
               (let [c (compare (:class a) (:class b))]
                 (if (zero? c) (term/compare-nodes (:node a) (:node b)) c))))
       vec))

;; ---------------------------------------------------------------------------
;; the terms a class stands for

(def count-cap
  "Past this many terms the count says only that there are more."
  1000000)

(def few
  "How many of a class's cheapest terms the page lists."
  6)

(defn term-count
  "How many terms the class of id stands for: a number, `count-cap`
  meaning at least that many, or :infinite when the class reaches
  itself (x = x + 0 puts x, x + 0, x + 0 + 0, … in one class)."
  [g id]
  (let [state (volatile! {})            ; class id -> count, or :visiting
        infinite (volatile! false)
        walk (fn walk [id]
               (let [id (eg/find g id)
                     seen (get @state id)]
                 (cond
                   (= :visiting seen) (do (vreset! infinite true) 0)
                   (some? seen) seen
                   :else
                   (do (vswap! state assoc id :visiting)
                       (let [n (reduce (fn [n node]
                                         (min count-cap
                                              (+ n (if (term/compound? node)
                                                     (reduce (fn [p c] (min count-cap (* p (walk c))))
                                                             1 (term/children node))
                                                     1))))
                                       0 (eg/nodes g id))]
                         (vswap! state assoc id n)
                         n)))))
        n (walk id)]
    (if @infinite :infinite n)))

(defn- compare-costed
  "Ascending cost, ties in cromulent's order on terms, so a list of
  the cheapest is a function of the e-graph value alone."
  [a b]
  (let [c (compare (:cost a) (:cost b))]
    (if (zero? c) (term/compare-nodes (:term a) (:term b)) c)))

(defn- cartesian
  "Every way of choosing one element from each list."
  [lists]
  (reduce (fn [acc xs] (for [a acc x xs] (conj a x))) [[]] lists))

(def ^:private combo-budget 2000)

(defn- node-terms
  "The k cheapest terms a node heads, given each child class's
  cheapest terms so far; nil while a child has none. A node of many
  children is combined over a few of each child's terms so the work
  stays bounded."
  [cost-fn best k node]
  (if (term/compound? node)
    (let [lists (mapv #(nth best %) (term/children node))]
      (when (every? seq lists)
        (let [n (count lists)
              m (loop [m k] (if (or (<= m 1) (<= (reduce * (repeat n m)) combo-budget)) m (recur (dec m))))
              lists (mapv #(vec (take m %)) lists)]
          (->> (cartesian lists)
               (map (fn [choice]
                      {:cost (cost-fn node (mapv :cost choice))
                       :term (term/make (term/operator node) (mapv :term choice))}))
               (sort compare-costed)
               (take k)
               vec))))
    [{:cost (cost-fn node []) :term node}]))

(defn cheapest
  "Vector, class id -> the k cheapest terms of the class under cost-fn,
  [{:cost c :term t}] ascending, for every root (nil elsewhere): the
  bottom-up fixpoint of cromulent.extract/best-costs, keeping k terms
  per class instead of one. A term that comes back to its own class
  costs more than the one it came from, so a cycle adds nothing past
  the k-th."
  [g cost-fn k]
  (let [n (:next-id g)
        classes (:classes g)
        pass (fn [best]
               (reduce (fn [best i]
                         (if-let [cls (nth classes i)]
                           (let [terms (->> (:nodes cls)
                                            (map #(eg/canonicalize g %))
                                            distinct
                                            (mapcat #(node-terms cost-fn best k %))
                                            (sort compare-costed)
                                            (take k)
                                            vec)]
                             (if (= terms (nth best i)) best (assoc best i terms)))
                           best))
                       best
                       (range n)))]
    (loop [best (vec (repeat n nil))]
      (let [best' (pass best)]
        (if (= best' best) best (recur best'))))))

(defn node-costs
  "Each node of the class of id with the cost of the cheapest term it
  heads, ascending, the cheapest marked: [{:node n :cost c :best? b}].
  A node whose child has no finite term is left out."
  [g cheapest-table cost-fn id]
  (let [costed (keep (fn [node]
                       (when-let [c (if (term/compound? node)
                                      (let [cs (map #(:cost (first (nth cheapest-table (eg/find g %)))) (term/children node))]
                                        (when (every? some? cs) (cost-fn node (vec cs))))
                                      (cost-fn node []))]
                         {:node node :cost c}))
                     (eg/nodes g id))
        sorted (vec (sort (fn [a b]
                            (let [c (compare (:cost a) (:cost b))]
                              (if (zero? c) (term/compare-nodes (:node a) (:node b)) c)))
                          costed))]
    (if (seq sorted)
      (assoc-in sorted [0 :best?] true)
      sorted)))

;; ---------------------------------------------------------------------------
;; along the run

(defn- old?
  "Did g, an earlier e-graph of the same run, already hold node? Ids
  only grow along a run, so a node whose child g does not have yet
  is new."
  [g node]
  (and (or (not (term/compound? node))
           (every? #(< % (:next-id g)) (term/children node)))
       (some? (eg/lookup g node))))

(defn history
  "Where the class of id, as it is at step k of a timeline, has been:
  {:born j :grew [j …] :merged [j …]}, the first step with a class
  that ends up in it, the steps at which it gained a node, and the
  steps at which two or more classes that make it up became one.
  Before k a class is traced by which roots the union-find of step k
  joins into it; after k, by find, since ids only grow along a run."
  [timeline k id]
  (let [n (count timeline)
        gk (nth timeline k)
        c (eg/find gk id)
        reps (fn [j]
               (let [gj (nth timeline j)]
                 (if (<= j k)
                   (into #{} (filter #(= c (eg/find gk %))) (eg/roots gj))
                   #{(eg/find gj c)})))
        rep-sets (mapv reps (range n))
        grew (for [j (range 1 n)
                   :let [gj (nth timeline j) gp (nth timeline (dec j))]
                   :when (some (fn [r] (some #(not (old? gp %)) (eg/nodes gj r))) (nth rep-sets j))]
               j)
        merged (for [j (range 1 n)
                     :let [gj (nth timeline j) gp (nth timeline (dec j)) d (nth rep-sets j)]
                     :when (and (seq d)
                                (> (count (filter #(contains? d (eg/find gj %)) (eg/roots gp))) (count d)))]
                 j)]
    {:born (first (keep-indexed (fn [j s] (when (seq s) j)) rep-sets))
     :grew (vec grew)
     :merged (vec merged)}))
