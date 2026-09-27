(ns orrery.state
  "One atom holds the page, a value of orrery.workbench: the lesson,
  the learner's input, the run (a timeline of e-graph values), the
  scrub position, the cost in force, the REPL, and a few UI flags.
  The actions here swap it, from orrery.dispatch, with the steps of
  orrery.workbench, and a watch in orrery.app re-renders; what the
  page shows is derived from the value by orrery.derived. The REPL
  holds the same atom as `state`, and the atom refuses a value that
  is not a state of the page (`workbench/problem`).

  A saturation is stepped one iteration per timer tick, so the page
  repaints between iterations, the counters climb, and a stop button
  works between iterations. Per-lesson limits bound the worst tick.
  The stepping follows the value, not the action that put it there:
  `changed!` starts it whenever the run on show is another and still
  running, whoever swapped it in."
  (:require [clojure.string :as str]
            [orrery.derived :as derived]
            [orrery.diff :as diff]
            [orrery.eclass :as eclass]
            [orrery.input :as input]
            [orrery.lessons :as lessons]
            [orrery.printed :as printed]
            [orrery.repl :as repl]
            [orrery.run :as run]
            [orrery.score :as score]
            [orrery.workbench :as workbench]))

(defn- state?
  "The atom's validator: true, or an error that says what is wrong."
  [s]
  (if-let [p (workbench/problem s)]
    (throw (ex-info (str "not a state of the page: " p) {:problem p}))
    true))

(defonce app-state (atom (workbench/initial) :validator state?))

(def lesson workbench/lesson)

;; ---------------------------------------------------------------------------
;; the run loop

(defn- tick!
  "One more iteration of run id, while it is the run on show and
  still running."
  [id]
  (let [{:keys [run run-id]} @app-state]
    (when (and (= id run-id) run (= :running (:status run)))
      (let [run' (run/step run)]
        (swap! app-state workbench/advance run')
        (when (= :running (:status run'))
          (js/setTimeout #(tick! id) 0))))))

(defn changed!
  "What a change of the state asks of the browser, from the state
  before and the state after: when the run on show is another, the
  values derived from the last one are dropped, and a run still
  running is stepped. The page's watch calls it before it renders."
  [old new]
  (when (not= (:run-id old) (:run-id new))
    (derived/clear-cache!)
    (when (= :running (:status (:run new)))
      (let [id (:run-id new)]
        (js/setTimeout #(tick! id) 0)))))

(defn start-run!
  "Start the lesson's run over the current input values."
  []
  (swap! app-state workbench/start))

(defn stop! []
  (swap! app-state update :run run/stop))

;; ---------------------------------------------------------------------------
;; input

(def ^:private field-text workbench/field-text)

(def ^:private input-for workbench/input-for)

(def ^:private print-mode workbench/print-mode)

(defn load-lesson! [k]
  (when-let [l (lessons/by-key k)]
    (when (lessons/live? l)
      (swap! app-state #(-> % (workbench/open l) workbench/start)))))

(defn choose-alternative! [alt]
  (swap! app-state
         (fn [s]
           (let [l (lesson s)
                 values (merge (:values l) (:values alt))]
             (-> s
                 (assoc :input (input-for (print-mode s) l values (or (:opts alt) {}) (:label alt)))
                 workbench/start)))))

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
                        label (lessons/input-label input)
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

(defn set-step! [k] (swap! app-state workbench/scrub k))

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
;; the graph picture and the export

(defn toggle-graph!
  "Draw the picture or hide it; from here on the choice is the
  learner's, on every lesson."
  []
  (swap! app-state (fn [s] (assoc-in s [:ui :graph?] (not (derived/graph? s))))))

(defn toggle-graph-filter! [] (swap! app-state update-in [:ui :graph-filter?] not))

(def zoom-levels [0.25 0.35 0.5 0.75 1 1.5 2 3])

(defn zoom-graph!
  "The graph's zoom: nil fits the width; :in and :out step through
  `zoom-levels`, from the fit to the natural size going in and to
  three quarters going out."
  [dir]
  (swap! app-state update-in [:ui :graph-zoom]
         (fn [z]
           (case dir
             :fit nil
             :in (if (nil? z) 1 (or (first (filter #(> % z) zoom-levels)) z))
             :out (if (nil? z) 0.75 (or (last (filter #(< % z) zoom-levels)) z))))))

(defn set-export-status! [text] (swap! app-state assoc-in [:ui :export-status] text))

;; ---------------------------------------------------------------------------
;; the REPL

(defn set-repl-input! [text] (swap! app-state update :repl assoc :input text :recall nil))

(defn clear-repl! [] (swap! app-state assoc :repl (:repl (workbench/initial))))

(defn- evaluate!
  "The entry for text, evaluated with g bound to the e-graph on show
  and timeline to the run's. What it evaluates may swap the state
  itself, so the state is read again by whoever appends the entry."
  [text]
  (let [s @app-state]
    (repl/bind! (derived/current-egraph s) (:timeline (:run s)))
    (assoc (repl/eval-string text printed/printed) :in text)))

(defn eval-repl!
  "Evaluate what is in the editor."
  []
  (let [text (get-in @app-state [:repl :input])]
    (when (seq (str/trim text))
      (let [entry (evaluate! text)]
        (swap! app-state update :repl
               (fn [r] (-> r (update :history conj entry) (assoc :input "" :recall nil))))))))

(defn run-repl!
  "Evaluate code from a link in the prose, as if it had been typed;
  the editor keeps what is in it."
  [code]
  (let [entry (evaluate! code)]
    (swap! app-state update-in [:repl :history] conj entry)))

(defn recall-repl!
  "An earlier input into the editor (:back), or a later one (:forward)."
  [dir]
  (swap! app-state update :repl workbench/recall dir))

(defn show!
  "Put v on show: an e-graph, a [g id] pair, a runner's result or a run."
  [v]
  (when-not (workbench/run-of v)
    (throw (ex-info "show! takes an e-graph, a [g id] pair, a runner's result or a run" {:value v})))
  (swap! app-state workbench/put v)
  nil)

(defn push!
  "Make the e-graph g the next step of the run on show, under label."
  ([g] (push! g "from the REPL"))
  ([g label]
   (when-not (diff/egraph? g)
     (throw (ex-info "push! takes an e-graph" {:value g})))
   (swap! app-state workbench/push g label)
   nil))

(defn adopt-entry!
  "Make the value of REPL history entry i the run on show."
  [i]
  (when-let [v (:ok (get-in @app-state [:repl :history i]))]
    (show! v)))

;; the REPL holds the atom as `state`, and show! and push! as its own;
;; on every load, so that a reload hands over the functions as they are now
(repl/bind-page! app-state [#'show! #'push!])
