(ns orrery.derived
  "Computes what the page shows from the state value:

    - the lesson
    - the e-graph at a step
    - what a step changed
    - the extractor and the cheapest terms under the cost in force
    - the opened class
    - a rule's matches
    - the snapshot the browser suite reads

  These are pure functions of the state map, so the views build on
  the JVM and on Jolt as well as in the browser. Results are
  memoized per run in a cache, which a new run clears (see
  orrery.state).

  The cache trusts the run id. `workbench/show` gives every run it
  puts on show a new id. A run that is put in the state another way,
  such as `(swap! state assoc :run r)` at the REPL, keeps the old id,
  and the page shows values derived from the run before it."
  (:require [cromulent.core :as eg]
            [cromulent.export :as export]
            [cromulent.extract :as ex]
            [cromulent.pattern :as pat]
            [orrery.costs :as costs]
            [orrery.diff :as diff]
            [orrery.eclass :as eclass]
            [orrery.graph :as graph]
            [orrery.lessons :as lessons]
            [orrery.normal :as normal]
            [orrery.notation :as notation]
            [orrery.run :as run]))

(defonce ^:private cache (atom {}))

(defn clear-cache! [] (reset! cache {}))

(defn- cached [k f]
  (if (contains? @cache k)
    (get @cache k)
    (let [v (f)] (swap! cache assoc k v) v)))

(defn lesson [s] (lessons/by-key (:lesson s)))

(defn egraph-at
  "Returns the e-graph at step `k` of the run. Clamps `k` to the
  steps the timeline has."
  [s k]
  (when-let [run (:run s)]
    (run/egraph-at run (max 0 (min k (run/last-step run))))))

(defn current-egraph [s] (egraph-at s (:step s)))

(defn diff-for
  "Returns what step `k` of the run changed, or nil at step 0."
  [s k]
  (let [{:keys [run run-id]} s]
    (when (and run (pos? k))
      (cached [run-id k :diff]
              #(diff/between (run/egraph-at run (dec k)) (run/egraph-at run k))))))

(defn diff-at
  "Returns what the current step changed, or nil at step 0."
  [s]
  (diff-for s (:step s)))

(defn best-at
  "Returns an extractor for the e-graph at the current step, under
  the cost in force. The extractor is `(fn [id] {:cost :term})`.
  Caches one cost table per step and cost."
  [s]
  (let [{:keys [run run-id step cost]} s]
    (when run
      (cached [run-id step cost]
              #(let [g (run/egraph-at run step)]
                 (ex/extractor g (costs/cost-fn cost g)))))))

(defn cheapest-at
  "Returns the few cheapest terms of every class of the e-graph at
  step `k`, under the cost in force. See `orrery.eclass/cheapest`.
  Caches one table per step and cost, and computes the table when
  a class is first opened at that step."
  [s k]
  (let [{:keys [run run-id cost]} s]
    (when run
      (cached [run-id k cost :cheapest]
              #(let [g (run/egraph-at run k)]
                 (eclass/cheapest g (costs/cost-fn cost g) eclass/few))))))

(defn detail-at
  "Returns what the class panel says about the selected class in the
  e-graph at step `k`. Returns nil when nothing is selected, or when
  that e-graph does not have the class yet.

  The result holds:

    - the root of the class at step `k`
    - its nodes with their costs
    - its cheapest terms, and how many terms it stands for
    - the classes it points at, and the nodes that point at it
    - its history along the run
    - how it got its polynomial, when the graph carries the
      polynomial analysis (`orrery.normal/workings`)"
  [s k]
  (let [{:keys [run run-id cost]} s
        id (get-in s [:ui :selected])]
    (when (and run id)
      (let [g (run/egraph-at run k)]
        (when (< id (:next-id g))
          (cached [run-id k cost id (run/last-step run) :detail]
                  #(let [root (eg/find g id)
                         cf (costs/cost-fn cost g)
                         table (cheapest-at s k)]
                     {:id id
                      :root root
                      :nodes (eclass/node-costs g table cf root)
                      :cheapest (nth table root)
                      :count (eclass/term-count g root)
                      :children (eclass/children g root)
                      :parents (eclass/parents g root)
                      :history (when (> (count (:timeline run)) 1)
                                 (eclass/history (:timeline run) k root))
                      :workings (normal/workings g root (fn [c] (:term (first (nth table (eg/find g c))))))})))))))

(defn root-at
  "Returns the class of the input term in the current e-graph, or
  nil when the run has no `:root`."
  [s]
  (when-let [g (current-egraph s)]
    (when-let [root (:root (:run s))]
      (eg/find g root))))

(defn matches-at
  "Returns the matches of each pattern rule in the current e-graph,
  as `[{:name :lhs :matches [...]}]`. Returns nil unless the lesson
  shows matches."
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

(defn- form-label
  "Returns the label that the head of a box shows for the normal
  form of a class, written in the print mode. For a polynomial, the
  label is the polynomial with its opaque atoms rendered by their
  best terms, cut short past 24 characters. For any other kind, the
  label is what the analysis reports instead."
  [g id mode render]
  (when-let [{:keys [kind term]} (normal/form g id render)]
    (case kind
      :polynomial (let [s (if (= :native mode) (pr-str term) (notation/term->str term))]
                    (if (> (count s) 24) (str (subs s 0 23) "…") s))
      :atom "its own atom"
      :too-big "too big"
      :conflict "a contradiction"
      nil)))

(defn graph?
  "Returns true when the picture is drawn. Once the learner has
  touched the switch, the learner's choice decides. Until then the
  lesson decides: the picture is off unless the lesson's panels ask
  for `:graph`, as the introduction's panels do."
  [s]
  (let [choice (get-in s [:ui :graph?])]
    (if (some? choice)
      choice
      (contains? (:panels (lesson s)) :graph))))

(defn graph-at
  "Returns the picture of the e-graph at the current step. See
  `orrery.graph/layout`.

  The picture shows every class. With the filter on and a class
  opened, it shows only what that class reaches. Labels are written
  in the print mode. The opaque atoms of a polynomial are spelled by
  their best terms under the cost in force.

  Caches one layout per step, filter, mode and cost."
  [s]
  (let [{:keys [run run-id step cost]} s
        mode (get-in s [:ui :print])
        selected (get-in s [:ui :selected])]
    (when run
      (let [g (run/egraph-at run step)
            only (when (and (get-in s [:ui :graph-filter?]) selected (< selected (:next-id g)))
                   (graph/reachable g selected))]
        (cached [run-id step mode only cost :graph]
                #(let [best (best-at s)]
                   (graph/layout g {:only only
                                    :node-label (fn [n] (if (= :native mode) (pr-str n) (notation/enode->str n)))
                                    :class-label (fn [id] (form-label g id mode (fn [c] (:term (best c)))))})))))))

(defn export-json
  "Returns the e-graph on show as egraph-serialize JSON. See
  cromulent.export.

  The JSON holds every node with its cost under the cost in force,
  and the class of the input as the root. When the graph carries the
  polynomial analysis, the JSON also holds the kind of each class as
  its type, and the normal form of each polynomial class written in
  the notation, with opaque atoms spelled by their best terms."
  [s]
  (when-let [g (current-egraph s)]
    (let [root (root-at s)
          best (best-at s)
          render (fn [c] (:term (best c)))]
      (export/json g {:cost (costs/cost-fn (:cost s) g)
                      :roots (when root [root])
                      :class-data (when (normal/analysis? g)
                                    (fn [g id]
                                      (when-let [{:keys [kind term]} (normal/form g id render)]
                                        (if (= :polynomial kind)
                                          {"type" "polynomial" "poly" (notation/term->str term)}
                                          {"type" (name kind)}))))}))))

(defn snapshot
  "Returns plain data about where the page is, for the end-to-end
  check."
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
