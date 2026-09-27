(ns orrery.workbench
  "The page's state as a value, and the steps over it that need no
  browser. One map holds the page: the lesson, the learner's input,
  the run on show (a timeline of e-graph values), the scrub position,
  the cost in force, the REPL, and a few UI flags. `initial` is the
  page before it has opened anything, `open` a lesson over its
  curated values, `start` its run, `show` any run put on show, and
  `page` the three at once. orrery.state holds the atom and the
  timers and swaps these in; the REPL swaps them into the same atom,
  as `wb`; the suites build their pages from them on the JVM and
  Jolt. `problem` says what is wrong with a value that is not a
  state, which the atom's validator asks of every value it is given,
  so a line at the REPL cannot leave the page with nothing to draw."
  (:require [clojure.string :as str]
            [orrery.costs :as costs]
            [orrery.diff :as diff]
            [orrery.lessons :as lessons]
            [orrery.notation :as notation]
            [orrery.run :as run]))

(def ui
  "The flags a page starts with."
  {:print :notation :playing nil :selected nil :hover nil :drawing? false
   ;; :graph? is nil until the switch is touched: the lesson decides (derived/graph?)
   :graph? nil :graph-filter? false :graph-zoom nil :export-status nil})

(defn initial
  "The page before it has opened anything."
  []
  {:lesson lessons/start
   :input {:fields {} :values {} :error nil :alternative nil :opts {}}
   :run nil
   :run-id 0
   :step 0
   :follow? true
   :cost :ast-size
   :repl {:input "" :history [] :recall nil}
   :ui ui})

(defn lesson [s] (lessons/by-key (:lesson s)))

(defn print-mode [s] (get-in s [:ui :print]))

;; ---------------------------------------------------------------------------
;; the input

(defn field-text
  "What a field shows for a value, in the print mode in force."
  [mode type v]
  (if (= :notation mode)
    (if (= :rules type) (notation/rules->str v) (notation/term->str v))
    (case type
      :rules (str "[" (str/join "\n " (map pr-str v)) "]")
      (pr-str v))))

(defn input-for
  "The input area of lesson l over values: a field per input, in the
  print mode."
  [mode l values opts alternative]
  {:fields (into {} (for [{:keys [key type]} (:inputs l)] [key (field-text mode type (get values key))]))
   :values values :error nil :alternative alternative :opts opts})

;; ---------------------------------------------------------------------------
;; a run on show

(defn show
  "s with run on show: the scrub position at the start of a run still
  running and at the end of a finished one, following, nothing
  opened. The run id is new, which is what the derived values are
  cached under and how the page knows to step a run that is still
  running."
  [s run]
  (-> s
      (assoc :run run
             :step (if (= :running (:status run)) 0 (run/last-step run))
             :follow? true)
      (update :run-id inc)
      (update :ui assoc :selected nil :hover nil :export-status nil)))

(defn advance
  "s with its run a step further, run', the scrub position following
  when it was."
  [s run']
  (cond-> (assoc s :run run')
    (:follow? s) (assoc :step (run/last-step run'))))

(defn scrub
  "s at step k of its run, clamped to the timeline; at the last step
  it follows the run again."
  [s k]
  (let [n (run/last-step (:run s))
        k (max 0 (min n k))]
    (assoc s :step k :follow? (= k n))))

(defn open
  "s with lesson l opened over its curated values, its fields in the
  print mode in force and its first cost in force. The run is
  `start`'s."
  [s l]
  (assoc s
         :lesson (:key l)
         :input (input-for (print-mode s) l (:values l) {} nil)
         :cost (or (first (:costs l)) :ast-size)))

(defn start
  "s with the lesson's run over the input values in force on show,
  not yet stepped when it is a saturation."
  [s]
  (let [{:keys [values opts]} (:input s)]
    (show s (lessons/make-run (lesson s) values (or opts {})))))

(defn page
  "The page of lesson l: opened, and its run started, or run on show
  when one is given, at step k when that is given too."
  ([l] (-> (initial) (open l) start))
  ([l run] (-> (initial) (open l) (show run)))
  ([l run k] (scrub (page l run) k)))

;; ---------------------------------------------------------------------------
;; the values of the REPL

(defn run?
  "Is v a run, as orrery.run makes them?"
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
  "Is v what `eg/add` and `eg/union` return, [g id]?"
  [v]
  (and (vector? v) (= 2 (count v)) (diff/egraph? (first v)) (integer? (second v))))

(defn runner-result?
  "Is v what `rw/saturate` returns?"
  [v]
  (and (map? v) (diff/egraph? (:egraph v)) (vector? (:stats v))))

(defn run-of
  "A value of the REPL as a run, or nil when it is none of these: a
  run as it is; an e-graph as a timeline of one entry; a [g id] pair
  as its e-graph, id the class to extract for; a runner's result as
  its timeline, when it kept one, or its last e-graph."
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
  "May g1 come after g0 in a timeline? Ids only grow along a run,
  which the diff and a class's history rely on."
  [g0 g1]
  (<= (:next-id g0) (:next-id g1)))

(defn put
  "s with the REPL value v on show, or s when v is nothing the page
  can show."
  [s v]
  (if-let [run (run-of v)] (show s run) s))

(defn push
  "s with the e-graph g as the next step of the run on show, under
  label, and the scrub position on it, so a run can be made by hand
  and scrubbed. A run still running is stopped first. An e-graph
  that cannot follow the last one, or the first on a page with no
  run, is put on show alone."
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
  "The REPL r, a map of the editor's text, :input, and the :history,
  with an earlier input in the editor (dir :back) or a later one
  (:forward), as the arrow keys of a terminal do it: :recall is the
  entry in the editor, nil while it holds what is being typed, and
  going forward past the last entry brings that back."
  [{:keys [input history recall draft] :as r} dir]
  (let [n (count history)
        i (case dir
            :back (if recall (max 0 (dec recall)) (dec n))
            :forward (when recall (inc recall)))]
    (cond
      (or (zero? n) (nil? i)) r
      (>= i n) (assoc r :input (or draft "") :recall nil :draft nil)
      :else (assoc r :input (:in (nth history i)) :recall i :draft (if recall draft input)))))

;; ---------------------------------------------------------------------------
;; what a state is

(defn problem
  "nil when s is a state of the page, or what is wrong with it, in
  words."
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
      (not (string? (get-in s [:repl :input]))) "[:repl :input] is the editor's text"
      (not (vector? (get-in s [:repl :history]))) "[:repl :history] is a vector"
      :else nil)))
