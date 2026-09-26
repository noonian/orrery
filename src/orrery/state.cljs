(ns orrery.state
  "One atom holds the page: the lesson, the learner's input, the run
  (a timeline of e-graph values), the scrub position, the cost in
  force, the REPL, and a few UI flags. Actions swap it; a watch in
  orrery.app re-renders. Derived values are plain functions of the
  state, memoized per run in a cache that a new run clears.

  A saturation is stepped one iteration per timer tick, so the page
  repaints between iterations, the counters climb, and a stop button
  works between iterations. Per-lesson limits bound the worst tick."
  (:require [clojure.string :as str]
            [cromulent.core :as eg]
            [cromulent.extract :as ex]
            [cromulent.pattern :as pat]
            [orrery.costs :as costs]
            [orrery.diff :as diff]
            [orrery.input :as input]
            [orrery.lessons :as lessons]
            [orrery.notation :as notation]
            [orrery.repl :as repl]
            [orrery.run :as run]
            [orrery.score :as score]))

(defonce app-state
  (atom {:lesson :blowup
         :input {:fields {} :values {} :error nil :alternative nil :opts {}}
         :run nil
         :run-id 0
         :step 0
         :follow? true
         :cost :ast-size
         :repl {:input "" :history []}
         :ui {:print :notation :playing nil :selected nil :hover nil :drawing? false}}))

(defonce ^:private cache (atom {}))

(defn- cached [k f]
  (if (contains? @cache k)
    (get @cache k)
    (let [v (f)] (swap! cache assoc k v) v)))

;; ---------------------------------------------------------------------------
;; derived

(defn lesson [s] (lessons/by-key (:lesson s)))

(defn egraph-at
  "The e-graph at step k of the run, clamped to the timeline."
  [s k]
  (when-let [run (:run s)]
    (run/egraph-at run (max 0 (min k (run/last-step run))))))

(defn current-egraph [s] (egraph-at s (:step s)))

(defn diff-at
  "What the current step changed, or nil at step 0."
  [s]
  (let [{:keys [run run-id step]} s]
    (when (and run (pos? step))
      (cached [run-id step :diff]
              #(diff/between (run/egraph-at run (dec step)) (run/egraph-at run step))))))

(defn best-at
  "An extractor over the current e-graph under the cost in force:
  (fn [id] {:cost :term}). One cost table per step and cost."
  [s]
  (let [{:keys [run run-id step cost]} s]
    (when run
      (cached [run-id step cost]
              #(let [g (run/egraph-at run step)]
                 (ex/extractor g (costs/cost-fn cost g)))))))

(defn root-at
  "The input term's class in the current e-graph, when the run has one."
  [s]
  (when-let [g (current-egraph s)]
    (when-let [root (:root (:run s))]
      (eg/find g root))))

(defn matches-at
  "For a lesson that shows matches: each pattern rule's matches in the
  current e-graph, [{:name :lhs :matches [...]}]."
  [s]
  (let [{:keys [run run-id step]} s
        l (lesson s)]
    (when (and run (= :embiggen (:kind run)) (contains? (:panels l) :matches))
      (cached [run-id step :matches]
              #(let [g (run/egraph-at run step)]
                 (vec (for [r (:rules run) :when (not (fn? (:lhs r)))]
                        {:name (:name r) :lhs (:lhs r) :matches (pat/ematch g (:lhs r))})))))))

(defn matched-classes [matches]
  (into #{} (mapcat (fn [m] (map :class (:matches m)))) matches))

(defn snapshot
  "Plain data about where the page is, for the end-to-end check."
  [s]
  (let [g (current-egraph s) run (:run s)]
    {:lesson (name (:lesson s))
     :step (:step s)
     :steps (when run (count (:timeline run)))
     :status (some-> run :status name)
     :iterations (:iterations run)
     :stopReason (some-> run :stop-reason name)
     :classes (when g (eg/class-count g))
     :nodes (when g (eg/node-count g))
     :dirty (when g (boolean (:dirty? g)))
     :bestCost (when-let [root (root-at s)] (:cost ((best-at s) root)))}))

;; ---------------------------------------------------------------------------
;; the run loop

(defonce ^:private tick-id (atom 0))

(defn- tick! [id]
  (let [{:keys [run]} @app-state]
    (when (and (= id @tick-id) run (= :running (:status run)))
      (let [run' (run/step run)]
        (swap! app-state (fn [s]
                           (cond-> (assoc s :run run')
                             (:follow? s) (assoc :step (run/last-step run')))))
        (when (= :running (:status run'))
          (js/setTimeout #(tick! id) 0))))))

(defn- install-run!
  "run becomes the run on show, in one swap, with the scrub position
  at the start of a running run and at the end of a finished one: the
  watch renders after every swap, so the step must never point past
  the timeline."
  [run]
  (swap! tick-id inc)
  (reset! cache {})
  (swap! app-state (fn [s]
                     (-> s
                         (assoc :run run)
                         (update :run-id inc)
                         (assoc :step (if (= :running (:status run)) 0 (run/last-step run)))
                         (assoc :follow? true)
                         (assoc-in [:ui :selected] nil)
                         (assoc-in [:ui :hover] nil))))
  run)

(defn start-run!
  "Start the lesson's run over the current input values."
  []
  (let [s @app-state
        l (lesson s)
        {:keys [values opts]} (:input s)
        run (install-run! (lessons/make-run l values (or opts {})))
        id @tick-id]
    (when (= :running (:status run))
      (js/setTimeout #(tick! id) 0))))

(defn stop! []
  (swap! tick-id inc)
  (swap! app-state update :run run/stop))

;; ---------------------------------------------------------------------------
;; input

(defn- field-text
  "What a field shows for a value, in the print mode in force."
  [mode type v]
  (if (= :notation mode)
    (if (= :rules type) (notation/rules->str v) (notation/term->str v))
    (case type
      :rules (str "[" (str/join "\n " (map pr-str v)) "]")
      (pr-str v))))

(defn- input-for [mode l values opts alternative]
  {:fields (into {} (for [{:keys [key type]} (:inputs l)] [key (field-text mode type (get values key))]))
   :values values :error nil :alternative alternative :opts opts})

(defn- print-mode [s] (get-in s [:ui :print]))

(defn load-lesson! [k]
  (when-let [l (lessons/by-key k)]
    (when (lessons/live? l)
      (swap! app-state assoc
             :lesson k
             :input (input-for (print-mode @app-state) l (:values l) {} nil)
             :cost (or (first (:costs l)) :ast-size))
      (start-run!))))

(defn choose-alternative! [alt]
  (let [l (lesson @app-state)
        values (merge (:values l) (:values alt))]
    (swap! app-state assoc :input (input-for (print-mode @app-state) l values (or (:opts alt) {}) (:label alt)))
    (start-run!)))

(defn set-field! [k text]
  (swap! app-state assoc-in [:input :fields k] text))

(defn submit-input!
  "Read every field; run on success, show the first problem otherwise."
  []
  (let [s @app-state
        l (lesson s)
        fields (get-in s [:input :fields])
        results (for [{:keys [key type] :as input} (:inputs l)]
                  (let [text (get fields key "")
                        label (lessons/input-label input (print-mode s))
                        r (case type
                            :term (input/read-term text)
                            :pattern (input/read-pattern text)
                            :rules (input/read-rules text))]
                    [key (or (:term r) (:pattern r) (:rules r)) (when (:error r) (str label ": " (:error r)))]))
        error (some (fn [[_ _ e]] e) results)
        ;; a value with no field (the fixed rule set of a bendix lesson) stays
        values (into (get-in s [:input :values]) (map (fn [[k v _]] [k v])) results)
        term (:term values)
        limit input/leaf-limit]
    (cond
      error (swap! app-state assoc-in [:input :error] error)
      (and term (= :embiggen (:kind l)) (> (input/leaf-count term) limit))
      (swap! app-state assoc-in [:input :error]
             (str "that has " (input/leaf-count term) " leaves; under these rules the page stops at " limit))
      :else (do (swap! app-state update :input assoc :values values :error nil :alternative nil :opts {} :drawn nil)
                (start-run!)))))

(defn surprise!
  "Draw a bank of candidates for the lesson over the rules and options
  in force, pick one weighted by score, and run it; the input area
  says what was picked and why. The bank is drawn on the next tick so
  the button can say it is drawing first."
  []
  (swap! app-state assoc-in [:ui :drawing?] true)
  (js/setTimeout
   (fn []
     (let [s @app-state
           l (lesson s)
           opts (or (get-in s [:input :opts]) {})
           c (score/surprise l (get-in s [:input :values]) opts (rand-int 1000000000))]
       (swap! app-state (fn [s]
                          (-> s
                              (assoc :input (assoc (input-for (print-mode s) l (:values c) opts nil)
                                                   :drawn (dissoc c :run :values)))
                              (assoc-in [:ui :drawing?] false))))
       (start-run!)))
   0))

;; ---------------------------------------------------------------------------
;; scrubbing and the rest of the UI

(defn set-step! [k]
  (swap! app-state (fn [s]
                     (let [n (run/last-step (:run s))
                           k (max 0 (min n k))]
                       (assoc s :step k :follow? (= k n))))))

(defn pause! []
  (when-let [id (get-in @app-state [:ui :playing])]
    (js/clearInterval id))
  (swap! app-state assoc-in [:ui :playing] nil))

(defn play! []
  (pause!)
  (let [id (js/setInterval
            (fn []
              (let [{:keys [step run]} @app-state]
                (if (< step (run/last-step run))
                  (set-step! (inc step))
                  (pause!))))
            500)]
    (swap! app-state assoc-in [:ui :playing] id)))

(defn set-cost! [k] (swap! app-state assoc :cost k))

(defn set-print!
  "Switch the print mode; a field the learner has not edited follows it."
  [mode]
  (swap! app-state
         (fn [s]
           (let [old (print-mode s)
                 values (get-in s [:input :values])
                 refill (fn [fields]
                          (reduce (fn [fields {:keys [key type]}]
                                    (let [v (get values key)]
                                      (cond-> fields
                                        (= (get fields key) (field-text old type v))
                                        (assoc key (field-text mode type v)))))
                                  fields
                                  (:inputs (lesson s))))]
             (-> s (assoc-in [:ui :print] mode) (update-in [:input :fields] refill))))))

(defn select-class! [id] (swap! app-state assoc-in [:ui :selected] id))

(defn hover! [id] (swap! app-state assoc-in [:ui :hover] id))

;; ---------------------------------------------------------------------------
;; the REPL

(defn set-repl-input! [text] (swap! app-state assoc-in [:repl :input] text))

(defn clear-repl! [] (swap! app-state assoc :repl {:input "" :history []}))

(defn eval-repl!
  "Evaluate the prompt with g bound to the e-graph on show."
  []
  (let [s @app-state
        text (get-in s [:repl :input])]
    (when (seq (str/trim text))
      (repl/bind! (current-egraph s) (:timeline (:run s)))
      (let [r (repl/eval-string text)]
        (swap! app-state (fn [s]
                           (-> s
                               (update-in [:repl :history] conj (assoc r :in text))
                               (assoc-in [:repl :input] ""))))))))

(defn adopt!
  "Make a REPL value the run on show: an e-graph becomes a one-entry
  timeline, a runner result its timeline (or its final e-graph)."
  [v]
  (let [steps (cond
                (diff/egraph? v) [["from the REPL" v]]
                (:timeline v) (map-indexed (fn [i g] [(if (zero? i) "the input" (str "iteration " i)) g]) (:timeline v))
                :else [["from the REPL" (:egraph v)]])
        run (assoc (run/script steps) :stats (or (:stats v) []) :iterations (or (:iterations v) 0)
                   :stop-reason (or (:stop-reason v) :done))]
    (install-run! run)))
