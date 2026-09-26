(ns orrery.score
  "Interestingness, our score and nothing deeper (IDEA.md section 4):
  features the engine reports about a finished run, each a raw value
  and a score in [0, 1]; a lesson's wants, weights on the features it
  teaches; and the bank, candidates drawn for a lesson, run, scored,
  one kept per shape of result, one picked at random weighted by
  score. Counts, iterations, stop reasons and costs are the same on
  every runtime; the terms a tie falls to are not, so a feature that
  reads a best term (:disagreement, :derivative-free) can score a
  candidate differently per runtime, which only moves a pick."
  (:require [clojure.string :as str]
            [cromulent.core :as eg]
            [cromulent.extract :as ex]
            [orrery.costs :as costs]
            [orrery.generate :as generate]
            [orrery.input :as input]
            [orrery.lessons :as lessons]
            [orrery.run :as run]))

;; ---------------------------------------------------------------------------
;; features

(defn- ratio [a b] (/ (* 1.0 a) (max 1 b)))

(defn- squash
  "raw in [0, ∞) to [0, 1): half a point at raw = k."
  [raw k]
  (/ (* 1.0 raw) (+ raw k)))

(defn- visible
  "1 − 1/r for a ratio r ≥ 1: nothing at 1, half at 2, most past 10."
  [r]
  (max 0.0 (- 1.0 (/ 1.0 r))))

(defn- distinct-subterms [t] (eg/node-count (first (eg/add (eg/egraph) t))))

(defn- has-op? [t op] (boolean (some #(and (vector? %) (= op (first %))) (tree-seq vector? rest t))))

(defn- sum-of-counts [maps] (reduce + 0 (mapcat vals maps)))

(def labels
  {:size "tree nodes"
   :sharing "tree nodes per graph node"
   :congruence "classes merged by congruence"
   :merges-per-node "merges per node"
   :shrink "shrink"
   :growth "node growth"
   :analysis-merges "forms the analysis already had"
   :rule-fired "normal-form rule applications"
   :iterations "iterations"
   :saturated "stop reason"
   :node-limit "stop reason"
   :disagreement "distinct answers under the costs"
   :derivative-free "a derivative-free answer"})

(defn features
  "Every feature of a finished run of lesson over values, {feature
  {:raw x :score s}}. The last e-graph the engine made is entry
  :iterations of the timeline; a bendix run's materialization comes
  after it, and the best terms are read after it."
  [lesson values run]
  (let [{:keys [timeline stats iterations stop-reason]} run
        g0 (first timeline)
        g-engine (nth timeline iterations)
        g (peek timeline)
        applied (sum-of-counts (map :applied stats))
        matched (sum-of-counts (map :matches stats))
        t ((or (:build lesson) :term) values)
        size (if t (input/size t) 0)
        root (when-let [r (:root run)] (eg/find g r))
        cost-keys (or (seq (:costs lesson)) [:ast-size])
        bests (when root (mapv (fn [c] (:term (ex/extract g root (costs/cost-fn c g)))) cost-keys))
        best (first bests)
        classes (mapv eg/class-count timeline)
        congruence (if (>= (count timeline) 3) (max 0 (- (nth classes 1) (peek classes))) 0)
        sharing (ratio size (eg/node-count g0))
        merges-per-node (ratio applied (eg/node-count g-engine))
        shrink (if best (ratio size (input/size best)) 1.0)
        growth (ratio (eg/node-count g-engine) (eg/node-count g0))
        already (max 0 (+ (if t (- (distinct-subterms t) (eg/class-count g0)) 0) (- matched applied)))
        fired (reduce + 0 (for [st stats, r (:rules run) :when (fn? (:lhs r))] (get (:applied st) (:name r) 0)))
        answers (count (distinct bests))
        d-free? (boolean (and best (not (has-op? best :D))))]
    {:size {:raw size
            :score (let [d (cond (< size 5) (- 5 size) (> size 11) (- size 11) :else 0)]
                     (max 0.0 (- 1.0 (/ d 6.0))))}
     :sharing {:raw sharing :score (visible sharing)}
     :congruence {:raw congruence :score (squash congruence 1)}
     :merges-per-node {:raw merges-per-node :score (min 1.0 (* 2.0 merges-per-node))}
     :shrink {:raw shrink :score (visible shrink)}
     :growth {:raw growth :score (visible growth)}
     :analysis-merges {:raw already :score (squash already 4)}
     :rule-fired {:raw fired :score (squash fired 2)}
     :iterations {:raw iterations
                  :score (cond (zero? iterations) 0.0 (= 1 iterations) 0.2 (= 2 iterations) 0.6
                               (<= iterations 8) 1.0 (<= iterations 12) 0.7 :else 0.4)}
     :saturated {:raw stop-reason :score (if (= :saturated stop-reason) 1.0 0.0)}
     :node-limit {:raw stop-reason :score (if (= :node-limit stop-reason) 1.0 0.0)}
     :disagreement {:raw answers
                    :score (if (> (count cost-keys) 1) (ratio (dec answers) (dec (count cost-keys))) 0.0)}
     :derivative-free {:raw d-free? :score (if d-free? 1.0 0.0)}}))

(defn score
  "The weighted mean of the wanted features' scores, in [0, 1]."
  [wants features]
  (if (empty? wants)
    0.0
    (/ (reduce + 0.0 (map (fn [[k w]] (* w (get-in features [k :score] 0.0))) wants))
       (reduce + 0.0 (vals wants)))))

;; ---------------------------------------------------------------------------
;; the bank

(defn candidate
  "One candidate for lesson: the values drawn over those in force,
  its run to the end, its features and its score."
  [lesson values opts s]
  (let [drawn (generate/draw lesson s values)
        values (merge values drawn)
        run (run/run-all (lessons/make-run lesson values opts))
        fs (features lesson values run)]
    {:values values :drawn drawn :run run :features fs
     :score (score (get-in lesson [:surprise :wants]) fs)}))

(defn shape
  "The shape of a run's result: what two candidates must differ in to
  both be worth offering."
  [run]
  (let [g (peek (:timeline run))]
    [(eg/class-count g) (eg/node-count g) (:iterations run) (:stop-reason run)]))

(defn bank
  "n candidates for lesson over the values and opts in force, drawn
  from the stream, the first of each shape kept."
  [lesson values opts s n]
  (loop [i 0 seen #{} out []]
    (if (= i n)
      out
      (let [c (candidate lesson values opts s)
            k (shape (:run c))]
        (recur (inc i) (conj seen k) (if (seen k) out (conj out c)))))))

(defn pick
  "One of the bank at random, weighted by the cube of its score, so
  the strong candidates share most of the draw and a weak one keeps
  a small chance."
  [bank s]
  (let [ws (mapv #(max 0.01 (* (:score %) (:score %) (:score %))) bank)
        x (* (reduce + 0.0 ws) (/ (s 1000) 1000.0))]
    (loop [i 0 acc 0.0]
      (let [acc (+ acc (nth ws i))]
        (if (or (< x acc) (= i (dec (count bank))))
          (nth bank i)
          (recur (inc i) acc))))))

(defn surprise
  "The pick from a bank drawn from seed for lesson over the values
  and opts in force: a candidate, with :of the bank's size before and
  :shapes after deduplication. n is the lesson's :surprise :n, twelve
  unless said."
  [lesson values opts seed]
  (let [s (generate/stream seed)
        n (get-in lesson [:surprise :n] 12)
        b (bank lesson values opts s n)]
    (assoc (pick b s) :of n :shapes (count b))))

;; ---------------------------------------------------------------------------
;; in words

(defn- decimals
  "x to places decimals, as a string, on every runtime."
  [x places]
  (let [scale (reduce * 1 (repeat places 10))
        k (long (+ 0.5 (* scale x)))
        whole (quot k scale)
        frac (str (mod k scale))]
    (str whole "." (apply str (repeat (- places (count frac)) "0")) frac)))

(def ^:private ratios #{:sharing :merges-per-node :shrink :growth})

(defn- raw-str
  "A raw value in words: a ratio to one decimal (1.0 is an integer
  in JavaScript, so the feature decides, not the number)."
  [k x]
  (cond (keyword? x) (name x)
        (true? x) "yes"
        (false? x) "no"
        (ratios k) (decimals x 1)
        (integer? x) (str x)
        (number? x) (decimals x 1)
        :else (pr-str x)))

(defn explain
  "Why this candidate: the bank it came from, its score, and each
  wanted feature's value and score, the heaviest want first."
  [{:keys [of shapes score features]} wants]
  (str "drawn from " of " candidates, " shapes " shapes of result; score " (decimals score 2) ": "
       (str/join " · "
                 (for [[k _] (sort-by (fn [[k w]] [(- w) (name k)]) wants)
                       :let [f (get features k)]]
                   (str (labels k) " " (raw-str k (:raw f)) " (" (decimals (:score f) 1) ")")))))
