(ns orrery.generate
  "Draws random terms over a small signature, the way the property
  tests of bendix and cromulent draw them (`term-gen`,
  `trig-term-gen`, cromulent.gen).

  This namespace does not use test.check, for two reasons. A page
  has no use for shrinking. And one seed must draw the same term on
  the JVM, on Jolt and in the browser. So the stream is the
  Park-Miller generator, whose products stay under 2^53 and are
  exact on every runtime. Every choice indexes a vector, and never
  a set or a map.

  A signature is leaves and shapes. A shape is an operator followed
  by slots:

    :t        draws a subterm
    :same     repeats the subterm that the slot before it drew
    :flip     repeats that subterm with its children reversed
    a vector  chooses one of its constants

  `:leaf-pct` is how often a slot above the bottom is a leaf anyway.
  `:reuse-pct` is how often a slot is a subterm already drawn for
  this term, which is how a term comes to share subterms.

  Each lesson names its draw under `:surprise` (see orrery.lessons).
  A draw is a function that takes the stream and the values in
  force, and returns the values it replaces."
  (:require [clojure.walk :as walk]
            [orrery.input :as input]))

;; ---------------------------------------------------------------------------
;; the stream

(def ^:private modulus 2147483647)
(def ^:private multiplier 48271)

(defn stream
  "Returns a stateful stream of draws from `seed`. For a stream `s`,
  `(s n)` is the next integer in [0, n). Any integer can be the
  seed. One seed gives the same stream on every runtime."
  [seed]
  (let [seed (if (neg? seed) (- seed) seed)
        state (atom (inc (mod seed (dec modulus))))]
    (fn [n]
      (let [x (mod (* @state multiplier) modulus)]
        (reset! state x)
        (mod x n)))))

(defn pick "Draws one element of `v`." [s v] (nth v (s (count v))))

(defn chance? "Returns true `pct` times in a hundred." [s pct] (< (s 100) pct))

;; ---------------------------------------------------------------------------
;; terms

(defn term
  "Draws a term over `sig` to the depth `depth`. At depth 0 the
  term is a leaf. Above depth 0 the term is:

    - a subterm already drawn, `:reuse-pct` times in a hundred
    - a leaf, `:leaf-pct` times in a hundred (33 by default)
    - otherwise a shape with its slots filled in"
  ([s sig depth] (term s sig depth (atom [])))
  ([s {:keys [leaves shapes leaf-pct reuse-pct] :or {leaf-pct 33 reuse-pct 0} :as sig} depth pool]
   (cond
     (zero? depth) (pick s leaves)
     (and (seq @pool) (chance? s reuse-pct)) (pick s @pool)
     (chance? s leaf-pct) (pick s leaves)
     :else
     (let [[op & slots] (pick s shapes)
           t (loop [slots slots args [] prev nil]
               (if (empty? slots)
                 (into [op] args)
                 (let [slot (first slots)
                       a (cond (= :t slot) (term s sig (dec depth) pool)
                               (= :same slot) prev
                               (= :flip slot) (if (vector? prev) (into [(first prev)] (reverse (rest prev))) prev)
                               :else (pick s slot))]
                   (recur (rest slots) (conj args a) a))))]
       (swap! pool conj t)
       t))))

(defn leaves [t] (if (vector? t) (mapcat leaves (rest t)) [t]))

(defn variables "Returns the variables of `t`, in order of first appearance." [t]
  (vec (distinct (filter keyword? (leaves t)))))

(defn- leaf-paths
  "Returns the path of every leaf of `t`, for use with `assoc-in`."
  ([t] (leaf-paths t []))
  ([t path]
   (if (vector? t)
     (mapcat (fn [i c] (leaf-paths c (conj path i))) (range 1 (count t)) (rest t))
     [path])))

(defn- shuffled [s v]
  (loop [v v i (dec (count v))]
    (if (pos? i)
      (let [j (s (inc i))]
        (recur (assoc v i (v j) j (v i)) (dec i)))
      v)))

(defn- bracketed
  "Returns a random binary bracketing of the sum of `v`. Keeps the
  elements in the order `v` has them."
  [s v]
  (if (= 1 (count v))
    (first v)
    (let [k (inc (s (dec (count v))))]
      [:+ (bracketed s (subvec v 0 k)) (bracketed s (subvec v k))])))

(defn arrangement
  "Returns the sum of `atoms` in a random order and a random
  bracketing."
  [s atoms]
  (bracketed s (shuffled s (vec atoms))))

(defn- until
  "Returns the first value that `f` draws and that passes `ok?`.
  Gives up after twenty-one draws and returns the last one."
  [ok? f]
  (loop [i 0]
    (let [v (f)]
      (if (or (ok? v) (>= i 20)) v (recur (inc i))))))

(defn- at-most [n] (fn [v] (<= (input/leaf-count (:term v)) n)))

(defn- planted
  "Returns `t` with `a` at one of its leaves and `b` at another."
  [s t a b]
  (let [paths (vec (leaf-paths t))
        i (s (count paths))
        j (let [j (s (dec (count paths)))] (if (< j i) j (inc j)))]
    (-> t
        (assoc-in (nth paths i) a)
        (assoc-in (nth paths j) b))))

;; ---------------------------------------------------------------------------
;; the draws, one per lesson: (fn [stream values-in-force] values-drawn)

(def textbook
  "The signature of what a learner writes: sums, products, powers
  and sines."
  {:leaves [:x :y :x :y 1 2 3]
   :shapes [[:+ :t :t] [:* :t :t] [:- :t :t] [:* [2 3] :t] [:expt :t [2 3]]
            [:sin :t] [:cos :t] [:/ :t [2 :y]]]})

(defn a-term "Draws a term for lesson 1." [s _] {:term (term s textbook 3)})

(defn a-shared-term "Draws a term for lesson 2. Half the subterms are ones already drawn." [s _]
  {:term (term s (assoc textbook :reuse-pct 50) 3)})

(def sides {:leaves [:a :b 1 2] :leaf-pct 0
            :shapes [[:* :t [2 3]] [:+ :t :t] [:<< :t [1 2]] [:* :t :t]]})

(def wrappers {:leaves ['?x '?x :c 1 2] :leaf-pct 0
               :shapes [[:+ :t :t] [:* :t :t] [:/ :t [2 3]] [:sin :t] [:* :t :same]]})

(defn an-equation
  "Draws an equation for lesson 3: two different sides, and a
  wrapper that mentions `?x`."
  [s _]
  (let [lhs (term s sides 1)
        rhs (until #(not= lhs %) #(term s sides 1))
        wrapper (until #(some #{'?x} (leaves %)) #(term s wrappers 2))]
    {:lhs lhs :rhs rhs :wrapper wrapper}))

(def arithmetic
  "The signature of sums and products over a few atoms and the
  constants the rules mention."
  {:leaves [:a :b :c :a :b 0 1 2]
   :shapes [[:+ :t :t] [:* :t :t] [:* :t [2]] [:+ :t :t :t]]})

(defn an-arithmetic-term "Draws a term for lesson 4, under the rules in force." [s _]
  {:term (term s arithmetic 2)})

(defn a-small-arithmetic-term
  "Draws a term for lesson 5, under the rules in force. The term has
  six leaves at most, because a sum of six atoms under commutativity
  and associativity is already six hundred nodes."
  [s _]
  (until (at-most 6) #(an-arithmetic-term s nil)))

(def doublings
  "The signature of terms with a subterm added to itself, which the
  rules of the taste lesson can spell three ways."
  {:leaves [:a :b :c :a :b 2] :leaf-pct 20 :reuse-pct 30
   :shapes [[:+ :t :same] [:* :t [2]] [:<< :t [1]] [:+ :t :t]]})

(defn a-doubled-term "Draws a term for lesson 6." [s _] {:term (term s doublings 2)})

(defn a-sum
  "Draws a sum for lesson 7: three, four or five atoms in a random
  order and a random bracketing."
  [s _]
  (let [n (+ 3 (s 3))]
    {:term (arrangement s (mapv #(keyword (str "a" %)) (range n)))}))

(def ring
  "A signature that follows bendix's `term-gen`, leaning to sums. It
  has the ring operators over atoms and small integers. Now and then
  it reuses a subterm. Now and then it puts a subterm beside its own
  reversal, which the polynomial sees as the same thing and a
  pattern rule does not."
  {:leaves [:a :b :c :a :b 1 2 3] :reuse-pct 30
   :shapes [[:+ :t :t] [:+ :t :t :t] [:+ :t :flip] [:- :t :flip] [:* :t :t] [:- :t :t]
            [:* :t :same] [:expt :t [2 3]] [:* [2 3] :t] [:neg :t] [:/ :t [2 4]]]})

(defn a-ring-term "Draws a term for lesson 8, with eight leaves at most." [s _]
  (until (at-most 8) #(hash-map :term (term s ring 3))))

(def contexts
  "The signature of sums, differences and products with room for two
  squares."
  {:leaves [:a :b :c 1 2] :leaf-pct 0
   :shapes [[:+ :t :t] [:+ :t :t :t] [:* :t :t] [:- :t :t]]})

(def arguments [:x :y [:+ :x 1] [:* 2 :x]])
(def cofactors [nil nil :y 2 :a])

(defn a-pythagorean-term
  "Draws a term for lesson 9. Plants sin²u and cos²u at two leaves
  of a random context. Both squares have the same argument u, and
  the same cofactor if they have one."
  [s _]
  (let [u (pick s arguments)
        k (pick s cofactors)
        square (fn [f] (let [sq [:expt [f u] 2]] (if k [:* k sq] sq)))]
    {:term (planted s (term s contexts 2) (square :sin) (square :cos))}))

(def probes
  "The small terms of `?v` that the what-if lesson chooses from. The
  lesson plants one of them twice: once as a term of the variable,
  and once as a term of what the variable might equal."
  '[[:sin ?v] [:* ?v ?v] [:* 2 ?v] [:expt ?v 2] [:+ ?v 1]])

(defn a-what-if
  "Draws a term and a what-if for lesson 10. The term is over x and
  y. It has some small term of a variable at one leaf. It has the
  same small term of a number, or of the other variable, at another
  leaf. The what-if drawn is the one that makes those two terms one
  node."
  [s _]
  (let [lhs (pick s [:x :y])
        rhs (pick s [0 1 2 (if (= :x lhs) :y :x)])
        probe (pick s probes)
        of (fn [v] (walk/postwalk-replace {'?v v} probe))
        t (term s (assoc contexts :leaves [:x :y 1 2]) 2)]
    {:term (planted s t (of lhs) (of rhs)) :lhs lhs :rhs rhs}))

(defn a-function
  "Draws a term for lesson 11: a function of the variable in force,
  with six leaves at most."
  [s {:keys [var]}]
  (let [v (or var :x)
        sig {:leaves [v v :y 1 2]
             :shapes [[:+ :t :t] [:* :t :t] [:sin :t] [:cos :t] [:expt :t [2 3]] [:* [2 3] :t] [:exp :t]]}]
    (until (fn [{:keys [term]}] (and (some #{v} (leaves term)) (<= (input/leaf-count term) 6)))
           #(hash-map :term (term s sig 3)))))

(defn draw
  "Returns the values a candidate replaces for `lesson`. Calls the
  lesson's `:surprise` draw on the stream `s` and the values in
  force. Draws again while the term drawn has more leaves than the
  page takes."
  [lesson s values]
  (let [f (get-in lesson [:surprise :draw])]
    (until (fn [v] (or (nil? (:term v)) (<= (input/leaf-count (:term v)) input/leaf-limit)))
           #(f s values))))
