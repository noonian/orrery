(ns orrery.state
  "One atom holds the page: the lesson, the learner's input, the run
  (a timeline of e-graph values), the scrub position, the cost in
  force, the REPL, and a few UI flags. The actions here swap it, from
  orrery.dispatch, and a watch in orrery.app re-renders; what the
  page shows is derived from the value by orrery.derived.

  A saturation is stepped one iteration per timer tick, so the page
  repaints between iterations, the counters climb, and a stop button
  works between iterations. Per-lesson limits bound the worst tick."
  (:require [clojure.string :as str]
            [orrery.derived :as derived]
            [orrery.diff :as diff]
            [orrery.eclass :as eclass]
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

(def lesson derived/lesson)

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
  (derived/clear-cache!)
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

(defn choose-alternative-by-label!
  "The lesson's alternative with this label, from a link in the prose."
  [label]
  (when-let [alt (some (fn [a] (when (= label (:label a)) a)) (:alternatives (lesson @app-state)))]
    (choose-alternative! alt)))

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

(defn select-class!
  "Open the class of id in the class list; opening it again closes it."
  [id]
  (swap! app-state update-in [:ui :selected] #(if (= % id) nil id)))

(defn deselect! [] (swap! app-state assoc-in [:ui :selected] nil))

(defn select-term!
  "Open the class holding t, from a link in the prose, scrubbing to
  step k first when one is given; nothing happens when the e-graph on
  show does not hold t."
  [t k]
  (when k (set-step! k))
  (let [s @app-state]
    (when-let [id (eclass/class-of (derived/current-egraph s) t)]
      (swap! app-state assoc-in [:ui :selected] id))))

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
      (repl/bind! (derived/current-egraph s) (:timeline (:run s)))
      (let [r (repl/eval-string text)]
        (swap! app-state (fn [s]
                           (-> s
                               (update-in [:repl :history] conj (assoc r :in text))
                               (assoc-in [:repl :input] ""))))))))

(defn adopt!
  "Make a REPL value the run on show: an e-graph becomes a one-entry
  timeline, a [g id] pair its e-graph, a runner result its timeline
  (or its final e-graph)."
  [v]
  (let [v (if (and (vector? v) (diff/egraph? (first v))) (first v) v)
        steps (cond
                (diff/egraph? v) [["from the REPL" v]]
                (:timeline v) (map-indexed (fn [i g] [(if (zero? i) "the input" (str "iteration " i)) g]) (:timeline v))
                :else [["from the REPL" (:egraph v)]])
        run (assoc (run/script steps) :stats (or (:stats v) []) :iterations (or (:iterations v) 0)
                   :stop-reason (or (:stop-reason v) :done))]
    (install-run! run)))

(defn adopt-entry!
  "Make the value of REPL history entry i the run on show."
  [i]
  (when-let [v (:ok (get-in @app-state [:repl :history i]))]
    (adopt! v)))
