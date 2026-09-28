(ns orrery.state
  "Holds the page in one atom. The atom's value is a state of the
  page, as orrery.workbench defines it: the lesson, the learner's
  input, the run (a timeline of e-graph values), the scrub position,
  the cost in force, the REPL, and a few UI flags.

  orrery.dispatch calls the actions here. Each action swaps the atom,
  using the steps of orrery.workbench. A watch in orrery.app renders
  the page again, and orrery.derived derives what the page shows
  from the atom's value. The REPL holds the same atom as `state`.
  The atom refuses a value that is not a state of the page (see
  `workbench/problem`).

  A saturation is stepped one iteration per timer tick. So the page
  repaints between iterations, the counters climb, and a stop button
  works between iterations. Per-lesson limits bound the worst tick.

  An action that changes what the REPL sees (the e-graph on show,
  the run, the class that is open) is traced in the REPL's history
  with the code that does the same (`workbench/traced`).

  The stepping follows the value, not the action that put it there.
  `changed!` starts the stepping whenever a different run is on show
  and that run is still running, whoever swapped it in."
  (:require [clojure.string :as str]
            [orrery.derived :as derived]
            [orrery.diff :as diff]
            [orrery.eclass :as eclass]
            [orrery.forms :as forms]
            [orrery.lessons :as lessons]
            [orrery.printed :as printed]
            [orrery.repl :as repl]
            [orrery.run :as run]
            [orrery.workbench :as workbench]))

(defn- state?
  "Validates a value for the atom. Returns true when `s` is a state
  of the page. Otherwise throws an error that says what is wrong."
  [s]
  (if-let [p (workbench/problem s)]
    (throw (ex-info (str "not a state of the page: " p) {:problem p}))
    true))

(defonce app-state (atom (workbench/initial) :validator state?))

(def lesson workbench/lesson)

(defn- act!
  "Takes the step `v` of orrery.workbench with the arguments `args`,
  and traces it in the REPL's history under `label`."
  [label v & args]
  (swap! app-state #(apply workbench/traced % label v args)))

;; ---------------------------------------------------------------------------
;; the run loop

(defn- tick!
  "Runs one more iteration of the run with run id `id`, and schedules
  the next tick while that run is still running. Does nothing when
  that run is no longer the run on show, or is no longer running."
  [id]
  (let [{:keys [run run-id]} @app-state]
    (when (and (= id run-id) run (= :running (:status run)))
      (let [run' (run/step run)]
        (swap! app-state workbench/advance run')
        (when (= :running (:status run'))
          (js/setTimeout #(tick! id) 0))))))

(defn changed!
  "Does what a change of the state asks of the browser. `old` is the
  state before the change, and `new` is the state after it.

  When a different run is on show, drops the values derived from the
  previous run, and starts stepping the new run if it is still
  running. The page's watch calls this before it renders."
  [old new]
  (when (not= (:run-id old) (:run-id new))
    (derived/clear-cache!)
    (when (= :running (:status (:run new)))
      (let [id (:run-id new)]
        (js/setTimeout #(tick! id) 0)))))

(defn start-run!
  "Starts the lesson's run over the current input values."
  []
  (swap! app-state workbench/start))

(defn stop! []
  (swap! app-state update :run run/stop))

;; ---------------------------------------------------------------------------
;; input

(defn load-lesson!
  "Opens the page of key `k` and starts its run. The first page the
  browser opens is not traced, since nothing is in the history yet."
  [k]
  (if (nil? (:run @app-state))
    (swap! app-state workbench/visit k)
    (act! (str "opened " (lessons/heading (lessons/by-key k))) #'workbench/visit k)))

(defn choose-alternative-by-label!
  "Chooses the lesson's alternative that has the label `label`. Links
  in the prose use this."
  [label]
  (act! (str "tried " label) #'workbench/alternative label))

(defn set-field! [k text]
  (swap! app-state assoc-in [:input :fields k] text))

(defn submit-input!
  "Reads every field. Starts the run when every field can be read,
  and otherwise shows the first problem."
  []
  (act! "ran the input" #'workbench/submit))

(defn surprise!
  "Draws a bank of candidates for the lesson over the rules and
  options in force, picks one weighted by score, and runs it. The
  input area says what was picked and why.

  The bank is drawn on the next tick, so that the button can first
  say that it is drawing."
  []
  (swap! app-state assoc-in [:ui :drawing?] true)
  (js/setTimeout #(act! "drew a surprise" #'workbench/surprise (rand-int 1000000000)) 0))

;; ---------------------------------------------------------------------------
;; scrubbing and the rest of the UI

(defn set-step!
  "Scrubs to step `k`. `how` says how in the trace: scrubbed or
  played."
  ([k] (set-step! k "scrubbed"))
  ([k how]
   (let [n (some-> (:run @app-state) run/last-step)]
     (act! (str how " to step " (if n (max 0 (min n k)) k)) #'workbench/scrub k))))

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
                  (set-step! (inc step) "played")
                  (pause!))))
            500)]
    (swap! app-state assoc-in [:ui :playing] id)))

(defn set-cost! [k] (swap! app-state assoc :cost k))

(defn set-print!
  "Switches the print mode to `mode`. A field the learner has not
  edited is printed again in the new mode."
  [mode]
  (swap! app-state
         (fn [s]
           (let [old (workbench/print-mode s)
                 values (get-in s [:input :values])
                 refill (fn [fields]
                          (reduce (fn [fields {:keys [key type]}]
                                    (let [v (get values key)]
                                      (cond-> fields
                                        (= (get fields key) (workbench/field-text old type v))
                                        (assoc key (workbench/field-text mode type v)))))
                                  fields
                                  (:inputs (lesson s))))]
             (-> s (assoc-in [:ui :print] mode) (update-in [:input :fields] refill))))))

(defn select-class!
  "Opens the class with id `id` in the class list. Opening a class
  that is already open closes it."
  [id]
  (if (= id (get-in @app-state [:ui :selected]))
    (act! "closed the class" #'workbench/select nil)
    (act! (str "opened class " id) #'workbench/select id)))

(defn deselect! [] (act! "closed the class" #'workbench/select nil))

(defn select-term!
  "Opens the class that holds the term `t`. Links in the prose use
  this. Scrubs to step `k` first when `k` is given. Opens nothing
  when the e-graph on show does not hold `t`."
  [t k]
  (when k (set-step! k))
  (let [s @app-state]
    (when-let [id (eclass/class-of (derived/current-egraph s) t)]
      (act! (str "opened class " id) #'workbench/select id))))

(defn hover! [id] (swap! app-state assoc-in [:ui :hover] id))

;; ---------------------------------------------------------------------------
;; the graph picture and the export

(defn toggle-graph!
  "Draws the graph picture, or hides it. From then on the learner's
  choice applies on every lesson, and the lesson no longer decides."
  []
  (swap! app-state (fn [s] (assoc-in s [:ui :graph?] (not (derived/graph? s))))))

(defn toggle-graph-filter! [] (swap! app-state update-in [:ui :graph-filter?] not))

(def zoom-levels [0.25 0.35 0.5 0.75 1 1.5 2 3])

(defn zoom-graph!
  "Sets the graph's zoom. `dir` is :fit, :in or :out.

  :fit sets the zoom to nil, which fits the graph to the width. :in
  and :out step through `zoom-levels`. From the fit, :in goes to the
  natural size and :out goes to three quarters of it."
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

(defn set-repl-buffer! [text] (swap! app-state assoc-in [:repl :buffer] text))

(defn to-buffer!
  "Adds the code of history entry `i` to the end of the buffer."
  [i]
  (swap! app-state update :repl workbench/to-buffer i))

(defn add-code!
  "Adds `code` to the end of the buffer and opens the dock."
  [code]
  (swap! app-state #(-> %
                        (update :repl workbench/add-code code)
                        (assoc-in [:ui :repl-open?] true))))

(defn toggle-dock! [] (swap! app-state update-in [:ui :repl-open?] not))

(defn open-dock! [] (swap! app-state assoc-in [:ui :repl-open?] true))

(defn close-dock! [] (swap! app-state assoc-in [:ui :repl-open?] false))

(defn toggle-repl-help!
  "Shows the REPL's keys and names, or hides them. Hides the
  snippets, which stand in the same place."
  []
  (swap! app-state update :ui #(assoc % :repl-help? (not (:repl-help? %)) :repl-snippets? false)))

(defn toggle-snippets!
  "Shows the snippets, or hides them. Hides the keys and names, which
  stand in the same place."
  []
  (swap! app-state update :ui #(assoc % :repl-snippets? (not (:repl-snippets? %)) :repl-help? false)))

(defn set-dock-height! [px] (swap! app-state assoc-in [:ui :dock-height] px))

(defn toggle-line-numbers! [] (swap! app-state update-in [:ui :line-numbers?] not))

(defn clear-repl!
  "Empties the history. The line and the buffer keep their text."
  []
  (swap! app-state update :repl assoc :history [] :recall nil))

(defn clear-buffer!
  "Empties the buffer. The line and the history keep their text."
  []
  (set-repl-buffer! ""))

(defn- evaluate!
  "Evaluates `text` and returns its history entry. Before it
  evaluates, binds `g` to the e-graph on show and `timeline` to the
  run's timeline.

  The code it evaluates may swap the state itself. So the caller
  that appends the entry must read the state again."
  [text]
  (let [s @app-state]
    (repl/bind! (derived/current-egraph s) (:timeline (:run s)) (get-in s [:ui :selected]))
    (assoc (repl/eval-string text printed/printed) :in text)))

(defn- evaluate-forms!
  "Evaluates the forms `fs` of `text` in order, each into its own
  history entry. Stops after the first form that throws. Returns the
  forms it evaluated."
  [text fs]
  (loop [[f & more] fs done []]
    (if-not f
      done
      (let [entry (evaluate! (forms/text-of text f))]
        (swap! app-state update-in [:repl :history] conj entry)
        (if (contains? entry :error)
          (conj done f)
          (recur more (conj done f)))))))

(defn eval-line!
  "Evaluates every form on the line, each into its own history entry,
  and empties the line, as a terminal does."
  []
  (let [text (get-in @app-state [:repl :input])]
    (when (seq (str/trim text))
      (evaluate-forms! text (forms/top-level text))
      (swap! app-state update :repl assoc :input "" :recall nil :draft nil))))

(defn eval-buffer!
  "Evaluates the buffer as `how` says. :form evaluates the top-level
  form at the position `pos`. :all evaluates every form in order, and
  stops at the first error. The buffer keeps its text. Returns the
  forms it evaluated, each with its :start and :end."
  [how pos]
  (let [text (get-in @app-state [:repl :buffer])]
    (case how
      :form (when-let [f (forms/form-at text (or pos (count text)))]
              (evaluate-forms! text [f]))
      :all (evaluate-forms! text (forms/top-level text)))))

(defn run-repl!
  "Evaluates `code` from a link in the prose, as if it had been
  typed. The editor keeps its text, and the dock stays as it is. The
  closed dock shows the result in its line."
  [code]
  (let [entry (evaluate! code)]
    (swap! app-state update-in [:repl :history] conj entry)))

(defn recall-repl!
  "Puts an earlier input into the editor when `dir` is :back, or a
  later one when `dir` is :forward."
  [dir]
  (swap! app-state update :repl workbench/recall dir))

(defn show!
  "Puts `v` on show. `v` is an e-graph, a [g id] pair, a runner's
  result or a run."
  [v]
  (when-not (workbench/run-of v)
    (throw (ex-info "show! takes an e-graph, a [g id] pair, a runner's result or a run" {:value v})))
  (swap! app-state workbench/put v)
  nil)

(defn push!
  "Makes the e-graph `g` the next step of the run on show, under
  `label`."
  ([g] (push! g "from the REPL"))
  ([g label]
   (when-not (diff/egraph? g)
     (throw (ex-info "push! takes an e-graph" {:value g})))
   (swap! app-state workbench/push g label)
   nil))

(defn adopt-entry!
  "Makes the value of REPL history entry `i` the run on show."
  [i]
  (when-let [v (:ok (get-in @app-state [:repl :history i]))]
    (when (workbench/run-of v)
      (swap! app-state #(workbench/trace % (workbench/put % v)
                                         {:trace (str "put the value of entry " i " on show") :kind "show"})))))

;; The REPL holds the atom as `state`, and `show!` and `push!` under
;; their own names. This runs on every load, so that a reload hands
;; over the functions as they are now.
(repl/bind-page! app-state [#'show! #'push!])
