(ns orrery.score
  "Scores how interesting a run is. The score is our own measure and
  nothing deeper (IDEA.md section 4). It has three parts:

    - Features are what the engine reports about a finished run.
      Each feature has a raw value and a score in [0, 1].
    - The wants of a lesson are weights on the features the lesson
      teaches.
    - The bank is the candidates drawn for a lesson. Each candidate
      is run and scored. One candidate is kept per shape of result,
      and one is picked at random, weighted by score.

  Counts, iterations, stop reasons and costs are the same on every
  runtime. The terms a tie falls to are not. So a feature that reads
  a best term (`:disagreement`, `:derivative-free`) can score a
  candidate differently per runtime. That only moves a pick."
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
  "Maps `raw` from [0, ∞) to [0, 1). Returns 0.5 when `raw` = `k`."
  [raw k]
  (/ (* 1.0 raw) (+ raw k)))

(defn- visible
  "Returns 1 − 1/r for a ratio `r` ≥ 1. That is 0 at 1, 0.5 at 2,
  and more than 0.9 past 10."
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
  "Returns every feature of a finished run of `lesson` over
  `values`, as `{feature {:raw x :score s}}`.

  The last e-graph the engine made is entry `:iterations` of the
  timeline. The materialization of a bendix run comes after that
  entry. The best terms are read from the last entry of the
  timeline, after the materialization."
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
  "Returns the weighted mean of the scores of the wanted features,
  in [0, 1]."
  [wants features]
  (if (empty? wants)
    0.0
    (/ (reduce + 0.0 (map (fn [[k w]] (* w (get-in features [k :score] 0.0))) wants))
       (reduce + 0.0 (vals wants)))))

;; ---------------------------------------------------------------------------
;; the bank

(defn candidate
  "Returns one candidate for `lesson`. The candidate holds:

    - the values drawn, merged over the values in force
    - its run to the end
    - its features
    - its score"
  [lesson values opts s]
  (let [drawn (generate/draw lesson s values)
        values (merge values drawn)
        run (run/run-all (lessons/make-run lesson values opts))
        fs (features lesson values run)]
    {:values values :drawn drawn :run run :features fs
     :score (score (get-in lesson [:surprise :wants]) fs)}))

(defn shape
  "Returns the shape of the result of a run. Two candidates must
  differ in shape for both to be worth offering."
  [run]
  (let [g (peek (:timeline run))]
    [(eg/class-count g) (eg/node-count g) (:iterations run) (:stop-reason run)]))

(defn bank
  "Draws `n` candidates for `lesson` from the stream `s`, over the
  values and opts in force. Returns the first candidate of each
  shape."
  [lesson values opts s n]
  (loop [i 0 seen #{} out []]
    (if (= i n)
      out
      (let [c (candidate lesson values opts s)
            k (shape (:run c))]
        (recur (inc i) (conj seen k) (if (seen k) out (conj out c)))))))

(defn pick
  "Returns one candidate of `bank` at random, weighted by the cube
  of its score. The strong candidates then share most of the draw,
  and a weak candidate keeps a small chance."
  [bank s]
  (let [ws (mapv #(max 0.01 (* (:score %) (:score %) (:score %))) bank)
        x (* (reduce + 0.0 ws) (/ (s 1000) 1000.0))]
    (loop [i 0 acc 0.0]
      (let [acc (+ acc (nth ws i))]
        (if (or (< x acc) (= i (dec (count bank))))
          (nth bank i)
          (recur (inc i) acc))))))

(defn surprise
  "Returns the pick from a bank drawn from `seed` for `lesson`, over
  the values and opts in force. The pick is a candidate with two
  more keys. `:of` is the size of the bank before deduplication, and
  `:shapes` is its size after. The bank draws `n` candidates, where
  `n` is `:n` under the lesson's `:surprise`, or twelve by default."
  [lesson values opts seed]
  (let [s (generate/stream seed)
        n (get-in lesson [:surprise :n] 12)
        b (bank lesson values opts s n)]
    (assoc (pick b s) :of n :shapes (count b))))

;; ---------------------------------------------------------------------------
;; in words

(defn- decimals
  "Returns `x` as a string with `places` decimals. The string is the
  same on every runtime."
  [x places]
  (let [scale (reduce * 1 (repeat places 10))
        k (long (+ 0.5 (* scale x)))
        whole (quot k scale)
        frac (str (mod k scale))]
    (str whole "." (apply str (repeat (- places (count frac)) "0")) frac)))

(def ^:private ratios #{:sharing :merges-per-node :shrink :growth})

(defn- raw-str
  "Returns a raw value in words. A ratio is written to one decimal.
  The feature `k` decides what is a ratio, and not the number,
  because 1.0 is an integer in JavaScript."
  [k x]
  (cond (keyword? x) (name x)
        (true? x) "yes"
        (false? x) "no"
        (ratios k) (decimals x 1)
        (integer? x) (str x)
        (number? x) (decimals x 1)
        :else (pr-str x)))

(defn explain
  "Returns a string that says why this candidate was picked: the
  bank it came from, its score, and the value and score of each
  wanted feature, with the heaviest want first."
  [{:keys [of shapes score features]} wants]
  (str "drawn from " of " candidates, " shapes " shapes of result; score " (decimals score 2) ": "
       (str/join " · "
                 (for [[k _] (sort-by (fn [[k w]] [(- w) (name k)]) wants)
                       :let [f (get features k)]]
                   (str (labels k) " " (raw-str k (:raw f)) " (" (decimals (:score f) 1) ")")))))
