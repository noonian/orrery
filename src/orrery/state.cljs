(ns orrery.state
  "One atom holds the page: the lesson, the learner's input, the run
  (a timeline of e-graph values), the scrub position, the cost in
  force, and a few UI flags. Actions swap it; a watch in orrery.app
  re-renders. Derived values are plain functions of the state,
  memoized per run in a cache that `run!` clears.

  A saturation is stepped one iteration per timer tick, so the page
  repaints between iterations, the counters climb, and a stop button
  works between iterations. Per-lesson limits bound the worst tick."
  (:require [cromulent.core :as eg]
            [cromulent.extract :as ex]
            [orrery.costs :as costs]
            [orrery.diff :as diff]
            [orrery.input :as input]
            [orrery.lessons :as lessons]
            [orrery.run :as run]))

(defonce app-state
  (atom {:lesson :blowup
         :input {:text "" :term nil :error nil :alternative nil :opts {}}
         :run nil
         :run-id 0
         :step 0
         :follow? true
         :cost :ast-size
         :ui {:print :lay :playing nil :selected nil}}))

(defonce ^:private cache (atom {}))

(defn- cached [k f]
  (if (contains? @cache k)
    (get @cache k)
    (let [v (f)] (swap! cache assoc k v) v)))

;; ---------------------------------------------------------------------------
;; derived

(defn lesson [s] (lessons/by-key (:lesson s)))

(defn egraph-at [s k] (some-> (:run s) (run/egraph-at k)))

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
              #(ex/extractor (run/egraph-at run step) (costs/cost-fn cost))))))

(defn root-at
  "The input term's class in the current e-graph."
  [s]
  (when-let [g (current-egraph s)]
    (eg/find g (:root (:run s)))))

(defn snapshot
  "Plain data about where the page is, for the end-to-end check."
  [s]
  (let [g (current-egraph s) run (:run s)]
    {:lesson (name (:lesson s))
     :step (:step s)
     :status (some-> run :status name)
     :iterations (:iterations run)
     :stopReason (some-> run :stop-reason name)
     :classes (when g (eg/class-count g))
     :nodes (when g (eg/node-count g))
     :bestCost (when g (:cost ((best-at s) (root-at s))))}))

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

(defn start-run!
  "Start the lesson's run over the current input."
  []
  (let [s @app-state
        l (lesson s)
        {:keys [term opts]} (:input s)
        term (or term (:term l))
        id (swap! tick-id inc)]
    (reset! cache {})
    (swap! app-state (fn [s]
                       (-> s
                           (assoc :run (lessons/make-run l term (or opts {})))
                           (update :run-id inc)
                           (assoc :step 0 :follow? true)
                           (assoc-in [:ui :selected] nil))))
    (js/setTimeout #(tick! id) 0)))

(defn stop! []
  (swap! tick-id inc)
  (swap! app-state update :run run/stop))

;; ---------------------------------------------------------------------------
;; actions

(defn load-lesson! [k]
  (when-let [l (lessons/by-key k)]
    (when (lessons/live? l)
      (swap! app-state assoc
             :lesson k
             :input {:text (pr-str (:term l)) :term (:term l) :error nil :alternative nil :opts {}}
             :cost (or (first (:costs l)) :ast-size))
      (start-run!))))

(defn choose-alternative! [alt]
  (swap! app-state assoc :input {:text (pr-str (:term alt)) :term (:term alt) :error nil
                                 :alternative (:label alt) :opts (or (:opts alt) {})})
  (start-run!))

(defn set-input-text! [text]
  (swap! app-state assoc-in [:input :text] text))

(defn submit-input!
  "Read the text area; run on success, show the problem otherwise."
  []
  (let [s @app-state
        {:keys [term error]} (input/read-term (get-in s [:input :text]))
        limit 10]
    (cond
      error (swap! app-state assoc-in [:input :error] error)
      (> (input/leaf-count term) limit)
      (swap! app-state assoc-in [:input :error]
             (str "that has " (input/leaf-count term) " leaves; under these rules the page stops at " limit))
      :else (do (swap! app-state update :input assoc :term term :error nil :alternative nil :opts {})
                (start-run!)))))

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

(defn set-print! [mode] (swap! app-state assoc-in [:ui :print] mode))

(defn select-class! [id] (swap! app-state assoc-in [:ui :selected] id))
