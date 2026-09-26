(ns orrery.generate
  "Random terms over a small signature, the way bendix's and
  cromulent's property tests draw them (term-gen, trig-term-gen,
  cromulent.gen), without test.check: a page has no use for
  shrinking, and one seed must draw the same term on the JVM, on
  Jolt and in the browser. So the stream is Park–Miller's generator,
  whose products stay under 2^53 and are exact on every runtime, and
  every choice indexes a vector, never a set or a map.

  A signature is leaves and shapes. A shape is an operator followed
  by slots: :t draws a subterm, :same repeats the subterm the slot
  before it drew, :flip repeats it with its children reversed, and a
  vector chooses one of its constants.
  :leaf-pct is how often a slot above the bottom is a leaf anyway,
  :reuse-pct how often it is a subterm already drawn for this term,
  which is how a term comes to share. Each lesson names its draw
  (orrery.lessons, :surprise), a function of the stream and the
  values in force to the values it replaces."
  (:require [clojure.walk :as walk]
            [orrery.input :as input]))

;; ---------------------------------------------------------------------------
;; the stream

(def ^:private modulus 2147483647)
(def ^:private multiplier 48271)

(defn stream
  "A stateful stream of draws from seed: (s n) is the next integer in
  [0, n). Any integer seeds it; one seed gives one stream on every
  runtime."
  [seed]
  (let [seed (if (neg? seed) (- seed) seed)
        state (atom (inc (mod seed (dec modulus))))]
    (fn [n]
      (let [x (mod (* @state multiplier) modulus)]
        (reset! state x)
        (mod x n)))))

(defn pick "One of v." [s v] (nth v (s (count v))))

(defn chance? "True pct times in a hundred." [s pct] (< (s 100) pct))

;; ---------------------------------------------------------------------------
;; terms

(defn term
  "A term over sig to depth: a leaf at depth 0, and above it a
  subterm already drawn :reuse-pct times in a hundred, a leaf
  :leaf-pct times (33 unless said), otherwise a shape filled in."
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

(defn variables "The variables of t, in order of first appearance." [t]
  (vec (distinct (filter keyword? (leaves t)))))

(defn- leaf-paths
  "The path of every leaf of t, for assoc-in."
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
  "A random binary bracketing of the sum of v, in this order."
  [s v]
  (if (= 1 (count v))
    (first v)
    (let [k (inc (s (dec (count v))))]
      [:+ (bracketed s (subvec v 0 k)) (bracketed s (subvec v k))])))

(defn arrangement
  "The sum of atoms in a random order and a random bracketing."
  [s atoms]
  (bracketed s (shuffled s (vec atoms))))

(defn- until
  "The first values f draws that pass ok?, or the twentieth draw."
  [ok? f]
  (loop [i 0]
    (let [v (f)]
      (if (or (ok? v) (>= i 20)) v (recur (inc i))))))

(defn- at-most [n] (fn [v] (<= (input/leaf-count (:term v)) n)))

(defn- planted
  "t with a at one of its leaves and b at another."
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
  "What a learner writes: sums, products, powers, sines."
  {:leaves [:x :y :x :y 1 2 3]
   :shapes [[:+ :t :t] [:* :t :t] [:- :t :t] [:* [2 3] :t] [:expt :t [2 3]]
            [:sin :t] [:cos :t] [:/ :t [2 :y]]]})

(defn a-term "Lesson 1." [s _] {:term (term s textbook 3)})

(defn a-shared-term "Lesson 2: half the subterms are ones already drawn." [s _]
  {:term (term s (assoc textbook :reuse-pct 50) 3)})

(def sides {:leaves [:a :b 1 2] :leaf-pct 0
            :shapes [[:* :t [2 3]] [:+ :t :t] [:<< :t [1 2]] [:* :t :t]]})

(def wrappers {:leaves ['?x '?x :c 1 2] :leaf-pct 0
               :shapes [[:+ :t :t] [:* :t :t] [:/ :t [2 3]] [:sin :t] [:* :t :same]]})

(defn an-equation
  "Lesson 3: two different sides and a wrapper that mentions ?x."
  [s _]
  (let [lhs (term s sides 1)
        rhs (until #(not= lhs %) #(term s sides 1))
        wrapper (until #(some #{'?x} (leaves %)) #(term s wrappers 2))]
    {:lhs lhs :rhs rhs :wrapper wrapper}))

(def arithmetic
  "Sums and products over a few atoms and the constants the rules mention."
  {:leaves [:a :b :c :a :b 0 1 2]
   :shapes [[:+ :t :t] [:* :t :t] [:* :t [2]] [:+ :t :t :t]]})

(defn an-arithmetic-term "Lesson 4, under the rules in force." [s _]
  {:term (term s arithmetic 2)})

(defn a-small-arithmetic-term
  "Lesson 5, under the rules in force: six leaves at most, since a
  sum of six atoms under commutativity and associativity is already
  six hundred nodes."
  [s _]
  (until (at-most 6) #(an-arithmetic-term s nil)))

(def doublings
  "Terms with a subterm added to itself, which the taste lesson's
  rules can spell three ways."
  {:leaves [:a :b :c :a :b 2] :leaf-pct 20 :reuse-pct 30
   :shapes [[:+ :t :same] [:* :t [2]] [:<< :t [1]] [:+ :t :t]]})

(defn a-doubled-term "Lesson 6." [s _] {:term (term s doublings 2)})

(defn a-sum
  "Lesson 7: three, four or five atoms in a random order and bracketing."
  [s _]
  (let [n (+ 3 (s 3))]
    {:term (arrangement s (mapv #(keyword (str "a" %)) (range n)))}))

(def ring
  "bendix's term-gen, leaning to sums: the ring operators over atoms
  and small integers, a subterm reused now and then, and now and
  then a subterm beside its own reversal, which the polynomial sees
  as the same thing and a pattern rule does not."
  {:leaves [:a :b :c :a :b 1 2 3] :reuse-pct 30
   :shapes [[:+ :t :t] [:+ :t :t :t] [:+ :t :flip] [:- :t :flip] [:* :t :t] [:- :t :t]
            [:* :t :same] [:expt :t [2 3]] [:* [2 3] :t] [:neg :t] [:/ :t [2 4]]]})

(defn a-ring-term "Lesson 8: eight leaves at most." [s _]
  (until (at-most 8) #(hash-map :term (term s ring 3))))

(def contexts
  "Sums, differences and products with room for two squares."
  {:leaves [:a :b :c 1 2] :leaf-pct 0
   :shapes [[:+ :t :t] [:+ :t :t :t] [:* :t :t] [:- :t :t]]})

(def arguments [:x :y [:+ :x 1] [:* 2 :x]])
(def cofactors [nil nil :y 2 :a])

(defn a-pythagorean-term
  "Lesson 9: sin²u and cos²u, of one argument u and under one
  cofactor if any, planted at two leaves of a random context."
  [s _]
  (let [u (pick s arguments)
        k (pick s cofactors)
        square (fn [f] (let [sq [:expt [f u] 2]] (if k [:* k sq] sq)))]
    {:term (planted s (term s contexts 2) (square :sin) (square :cos))}))

(def probes
  "Small terms of ?v, one of which the what-if lesson plants twice:
  of the variable, and of what it might equal."
  '[[:sin ?v] [:* ?v ?v] [:* 2 ?v] [:expt ?v 2] [:+ ?v 1]])

(defn a-what-if
  "Lesson 10: a term over x and y with some small term of a variable
  at one leaf and the same term of a number, or of the other
  variable, at another; the what-if that makes them one node."
  [s _]
  (let [lhs (pick s [:x :y])
        rhs (pick s [0 1 2 (if (= :x lhs) :y :x)])
        probe (pick s probes)
        of (fn [v] (walk/postwalk-replace {'?v v} probe))
        t (term s (assoc contexts :leaves [:x :y 1 2]) 2)]
    {:term (planted s t (of lhs) (of rhs)) :lhs lhs :rhs rhs}))

(defn a-function
  "Lesson 11: a function of the variable in force, six leaves at most."
  [s {:keys [var]}]
  (let [v (or var :x)
        sig {:leaves [v v :y 1 2]
             :shapes [[:+ :t :t] [:* :t :t] [:sin :t] [:cos :t] [:expt :t [2 3]] [:* [2 3] :t] [:exp :t]]}]
    (until (fn [{:keys [term]}] (and (some #{v} (leaves term)) (<= (input/leaf-count term) 6)))
           #(hash-map :term (term s sig 3)))))

(defn draw
  "The values a candidate replaces for lesson: its :surprise draw
  over the stream and the values in force, redrawn while its term
  has more leaves than the page takes."
  [lesson s values]
  (let [f (get-in lesson [:surprise :draw])]
    (until (fn [v] (or (nil? (:term v)) (<= (input/leaf-count (:term v)) input/leaf-limit)))
           #(f s values))))
