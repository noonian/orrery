(ns orrery.workbench
  "The page's state as a value, and the steps over it that need no
  browser. One map holds the page: the lesson, the learner's input,
  the run on show (a timeline of e-graph values), the scrub position,
  the cost in force, the REPL, and a few UI flags.

  The steps:

    - `initial` returns the page before it has opened anything.
    - `open` opens a lesson over its curated values.
    - `start` starts the lesson's run.
    - `show` puts any run on show.
    - `page` returns the page of a lesson: `initial`, then `open`,
      then `start`, or `show` when it is given a run.

  orrery.state holds the atom and the timers, and swaps these steps
  in. The REPL swaps them into the same atom, under the alias `wb`.
  The suites build their pages from them on the JVM and Jolt.

  `problem` says what is wrong with a value that is not a state. The
  atom's validator asks this of every value the atom is given, so a
  line at the REPL cannot leave the page with nothing to draw."
  (:require [clojure.string :as str]
            [orrery.costs :as costs]
            [orrery.diff :as diff]
            [orrery.lessons :as lessons]
            [orrery.notation :as notation]
            [orrery.run :as run]))

(def ui
  "The flags a page starts with."
  {:print :notation :playing nil :selected nil :hover nil :drawing? false
   ;; :graph? is nil until the switch is touched. While it is nil the
   ;; lesson decides (see derived/graph?).
   :graph? nil :graph-filter? false :graph-zoom nil :export-status nil
   :line-numbers? false
   ;; The REPL's dock along the bottom of the page: open or closed,
   ;; its height in pixels, nil for the height it opens at, and
   ;; whether it shows its keys and the names in scope.
   :repl-open? false :dock-height nil :repl-help? false})

(defn initial
  "Returns the page before it has opened anything."
  []
  {:lesson lessons/start
   :input {:fields {} :values {} :error nil :alternative nil :opts {}}
   :run nil
   :run-id 0
   :step 0
   :follow? true
   :cost :ast-size
   :repl {:input "" :buffer "" :history [] :recall nil}
   :ui ui})

(defn lesson [s] (lessons/by-key (:lesson s)))

(defn print-mode [s] (get-in s [:ui :print]))

;; ---------------------------------------------------------------------------
;; the input

(defn field-text
  "Returns the text a field shows for the value `v`, in the print
  mode `mode`. `type` is the type of the field's input."
  [mode type v]
  (if (= :notation mode)
    (if (= :rules type) (notation/rules->str v) (notation/term->str v))
    (case type
      :rules (str "[" (str/join "\n " (map pr-str v)) "]")
      (pr-str v))))

(defn input-for
  "Returns the input area of lesson `l` over `values`. The area has
  one field per input, printed in the print mode `mode`."
  [mode l values opts alternative]
  {:fields (into {} (for [{:keys [key type]} (:inputs l)] [key (field-text mode type (get values key))]))
   :values values :error nil :alternative alternative :opts opts})

;; ---------------------------------------------------------------------------
;; a run on show

(defn show
  "Returns `s` with `run` on show. The scrub position is the start of
  a run that is still running, and the end of one that has finished.
  The page follows the run, and no class is open.

  Increments the run id. The derived values are cached under it, and
  a new id is how the page knows to step a run that is still
  running."
  [s run]
  (-> s
      (assoc :run run
             :step (if (= :running (:status run)) 0 (run/last-step run))
             :follow? true)
      (update :run-id inc)
      (update :ui assoc :selected nil :hover nil :export-status nil)))

(defn advance
  "Returns `s` with `run'` as its run. `run'` is the run of `s`, one
  step further. The scrub position moves to the last step when the
  page was following the run."
  [s run']
  (cond-> (assoc s :run run')
    (:follow? s) (assoc :step (run/last-step run'))))

(defn scrub
  "Returns `s` at step `k` of its run. `k` is clamped to the
  timeline. At the last step the page follows the run again."
  [s k]
  (let [n (run/last-step (:run s))
        k (max 0 (min n k))]
    (assoc s :step k :follow? (= k n))))

(defn open
  "Returns `s` with lesson `l` opened over its curated values. The
  fields are printed in the print mode in force, and the lesson's
  first cost is in force. A lesson whose `:dock` is :open opens the
  REPL's dock. Leaves the run alone: `start` makes the lesson's run."
  [s l]
  (cond-> (assoc s
                 :lesson (:key l)
                 :input (input-for (print-mode s) l (:values l) {} nil)
                 :cost (or (first (:costs l)) :ast-size))
    (= :open (:dock l)) (assoc-in [:ui :repl-open?] true)))

(defn start
  "Returns `s` with the lesson's run on show. The run is made over
  the input values in force. A saturation is not yet stepped."
  [s]
  (let [{:keys [values opts]} (:input s)]
    (show s (lessons/make-run (lesson s) values (or opts {})))))

(defn page
  "Returns the page of lesson `l`, with the lesson opened. Starts the
  lesson's run, or puts `run` on show when `run` is given. Scrubs to
  step `k` when `k` is given too."
  ([l] (-> (initial) (open l) start))
  ([l run] (-> (initial) (open l) (show run)))
  ([l run k] (scrub (page l run) k)))

;; ---------------------------------------------------------------------------
;; the values of the REPL

(defn run?
  "Returns true when `v` is a run, as orrery.run makes them."
  [v]
  (boolean
   (and (map? v)
        (vector? (:timeline v))
        (seq (:timeline v))
        (every? diff/egraph? (:timeline v))
        (vector? (:labels v))
        (= (count (:labels v)) (count (:timeline v)))
        (contains? #{:running :done} (:status v)))))

(defn pair?
  "Returns true when `v` is a [g id] pair, which is what `eg/add` and
  `eg/union` return."
  [v]
  (and (vector? v) (= 2 (count v)) (diff/egraph? (first v)) (integer? (second v))))

(defn runner-result?
  "Returns true when `v` is what `rw/saturate` returns."
  [v]
  (and (map? v) (diff/egraph? (:egraph v)) (vector? (:stats v))))

(defn run-of
  "Returns the REPL value `v` as a run, or nil when `v` is none of
  these:

    - A run is returned as it is.
    - An e-graph becomes a timeline of one entry.
    - A [g id] pair becomes a timeline of its e-graph, and id is the
      class to extract for.
    - A runner's result becomes its timeline when it kept one, and
      otherwise its last e-graph.

  Every run it returns is marked `:from :repl`."
  [v]
  (cond
    (run? v) (assoc v :from :repl)
    (diff/egraph? v) (assoc (run/script [["from the REPL" v]]) :from :repl)
    (pair? v) (assoc (run/script [["from the REPL" (first v)]] (second v)) :from :repl)
    (runner-result? v)
    (let [steps (if (seq (:timeline v))
                  (map-indexed (fn [i g] [(if (zero? i) "the input" (str "iteration " i)) g]) (:timeline v))
                  [["from the REPL" (:egraph v)]])]
      (assoc (run/script steps (:root v))
             :stats (:stats v) :iterations (or (:iterations v) 0)
             :stop-reason (or (:stop-reason v) :done) :ms (or (:ms v) 0) :from :repl))
    :else nil))

(defn follows?
  "Returns true when `g1` may come after `g0` in a timeline. Ids only
  grow along a run, and the diff and a class's history rely on that."
  [g0 g1]
  (<= (:next-id g0) (:next-id g1)))

(defn put
  "Returns `s` with the REPL value `v` on show. Returns `s` unchanged
  when `v` is nothing the page can show."
  [s v]
  (if-let [run (run-of v)] (show s run) s))

(defn push
  "Returns `s` with the e-graph `g` as the next step of the run on
  show, under `label`, and with the scrub position on that step. So
  a run can be made by hand and scrubbed.

  A run that is still running is stopped first. `g` is put on show
  alone when it cannot follow the last e-graph of the run, or when
  the page has no run yet."
  ([s g] (push s g "from the REPL"))
  ([s g label]
   (let [run (some-> (:run s) run/stop)]
     (cond
       (not (diff/egraph? g)) s
       (and run (follows? (peek (:timeline run)) g))
       (let [run (-> run
                     (update :timeline conj g)
                     (update :labels conj (str label))
                     (assoc :from :repl))]
         (scrub (assoc s :run run) (run/last-step run)))
       :else (put s g)))))

(defn recall
  "Returns the REPL `r` with an earlier input on the line when `dir`
  is :back, or a later one when `dir` is :forward. The arrow keys of
  a terminal do the same. `r` is a map that holds the line's text
  under :input, and the :history.

  :recall is the index of the history entry that is on the line. It
  is nil while the line holds what is being typed. Going forward past
  the last entry brings back what was being typed."
  [{:keys [input history recall draft] :as r} dir]
  (let [n (count history)
        i (case dir
            :back (if recall (max 0 (dec recall)) (dec n))
            :forward (when recall (inc recall)))]
    (cond
      (or (zero? n) (nil? i)) r
      (>= i n) (assoc r :input (or draft "") :recall nil :draft nil)
      :else (assoc r :input (:in (nth history i)) :recall i :draft (if recall draft input)))))

(defn to-buffer
  "Returns the REPL `r` with the code of history entry `i` added to
  the end of the buffer. A blank line separates it from what the
  buffer already holds."
  [{:keys [buffer history] :as r} i]
  (if-let [code (:in (get history i))]
    (let [kept (str/trimr buffer)]
      (assoc r :buffer (if (seq kept) (str kept "\n\n" code) code)))
    r))

;; ---------------------------------------------------------------------------
;; what a state is

(defn problem
  "Returns nil when `s` is a state of the page. Otherwise returns a
  string that says what is wrong with it."
  [s]
  (let [run (:run s)]
    (cond
      (not (map? s)) "the state of the page is a map"
      (nil? (lesson s)) (str ":lesson names no page: " (pr-str (:lesson s)))
      (not (integer? (:run-id s))) ":run-id is a number, and a new one for every run put on show"
      (not (or (nil? run) (run? run)))
      ":run is a run: a :timeline of e-graphs, a label for each in :labels, and a :status, :running or :done"
      (and run (not (every? true? (map follows? (:timeline run) (rest (:timeline run))))))
      "the e-graphs of a timeline follow one another: ids only grow along a run"
      (not (and (integer? (:step s)) (<= 0 (:step s) (if run (run/last-step run) 0))))
      (str ":step is a step of the run, 0 to " (if run (run/last-step run) 0) ": " (pr-str (:step s)))
      (nil? (costs/label (:cost s))) (str ":cost names no cost: " (pr-str (:cost s)))
      (not (map? (:input s))) ":input is a map"
      (not (map? (get-in s [:input :fields]))) "[:input :fields] is a map, the text of each field"
      (not (map? (get-in s [:input :values]))) "[:input :values] is a map, the value of each input"
      (not (map? (:ui s))) ":ui is a map"
      (not (contains? #{:notation :native} (print-mode s))) "[:ui :print] is :notation or :native"
      (not (let [id (get-in s [:ui :selected])] (or (nil? id) (and (integer? id) (<= 0 id)))))
      "[:ui :selected] is a class id, or nil"
      (not (string? (get-in s [:repl :input]))) "[:repl :input] is the line's text"
      (not (string? (get-in s [:repl :buffer]))) "[:repl :buffer] is the buffer's text"
      (not (vector? (get-in s [:repl :history]))) "[:repl :history] is a vector"
      :else nil)))
