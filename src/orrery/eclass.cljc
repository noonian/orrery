(ns orrery.eclass
  "Computes what the page says about one class when it is selected:

    - the class of a term
    - each node of the class, with its cost under the cost in force
    - the classes its nodes point at, and the nodes that point at it
    - how many terms the class stands for, and the cheapest few of
      them
    - where the class has been along a run

  The functions are pure. Counts and costs are the same on every
  runtime. Ids are not."
  (:refer-clojure :exclude [parents])
  (:require [cromulent.core :as eg]
            [cromulent.term :as term]))

(defn class-of
  "Returns the canonical class that holds `t` in `g`, or nil when
  `g` does not hold `t`. Only looks up, and never adds to `g`."
  [g t]
  (if (term/compound? t)
    (let [ids (mapv #(class-of g %) (term/children t))]
      (when (every? some? ids)
        (eg/lookup g (term/make (term/operator t) ids))))
    (eg/lookup g t)))

(defn children
  "Returns the classes that the nodes of the class of `id` point at,
  in ascending order."
  [g id]
  (->> (eg/nodes g id)
       (filter term/compound?)
       (mapcat term/children)
       (map #(eg/find g %))
       distinct
       sort
       vec))

(defn parents
  "Returns the nodes that hold the class of `id` as a child, each
  with the class it is in, as `[{:node n :class c}]`. Each node is
  canonical.

  Between a union and the rebuild, two parents may read the same and
  sit in two classes. That breaks the invariant that congruence
  restores."
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
  "The cap on a count of terms. Past this many terms, the count says
  only that there are more."
  1000000)

(def few
  "The number of a class's cheapest terms that the page lists."
  6)

(defn term-count
  "Returns how many terms the class of `id` stands for. The result
  is a number, where `count-cap` means at least that many, or
  `:infinite` when the class reaches itself. For example, x = x + 0
  puts x, x + 0, x + 0 + 0 and so on in one class."
  [g id]
  (let [state (volatile! {})            ; by class id: count, or :visiting
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
  "Compares two costed terms by ascending cost, and breaks ties by
  cromulent's order on terms. A list of the cheapest terms is then
  a function of the e-graph value alone."
  [a b]
  (let [c (compare (:cost a) (:cost b))]
    (if (zero? c) (term/compare-nodes (:term a) (:term b)) c)))

(defn- cartesian
  "Returns every way of choosing one element from each list."
  [lists]
  (reduce (fn [acc xs] (for [a acc x xs] (conj a x))) [[]] lists))

(def ^:private combo-budget 2000)

(defn- node-terms
  "Returns the `k` cheapest terms that `node` heads, given the
  cheapest terms of each child class so far. Returns nil while a
  child has none. A node with many children is combined over only a
  few of the terms of each child, so that the work stays bounded."
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
  "Returns a vector indexed by class id. For every root, the entry
  holds the `k` cheapest terms of the class under `cost-fn`, as
  `[{:cost c :term t}]` in ascending order. Every other entry is
  nil.

  This is the bottom-up fixpoint of `cromulent.extract/best-costs`,
  except that it keeps `k` terms per class instead of one. A term
  that comes back to its own class costs more than the term it came
  from, so a cycle adds nothing past the `k`-th term."
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
  "Returns each node of the class of `id` with the cost of the
  cheapest term it heads, as `[{:node n :cost c :best? b}]`. The
  nodes are in ascending order of cost, and `:best?` marks the
  cheapest. A node whose child has no finite term is left out."
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
  "Returns true when `g`, an earlier e-graph of the same run,
  already held `node`. Ids only grow along a run, so a node with a
  child that `g` does not have yet is new."
  [g node]
  (and (or (not (term/compound? node))
           (every? #(< % (:next-id g)) (term/children node)))
       (some? (eg/lookup g node))))

(defn history
  "Returns where the class of `id`, as it is at step `k` of
  `timeline`, has been:

    {:born j :grew [j ...] :merged [j ...]}

  `:born` is the first step with a class that ends up in it.
  `:grew` holds the steps at which it gained a node. `:merged`
  holds the steps at which two or more classes that make it up
  became one.

  Before `k`, a class is traced by which roots the union-find of
  step `k` joins into it. After `k`, it is traced by `find`, because
  ids only grow along a run."
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
