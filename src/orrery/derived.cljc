(ns orrery.derived
  "What the page shows, computed from the state value: the lesson,
  the e-graph at a step, what a step changed, the extractor and the
  cheapest terms under the cost in force, the opened class, a rule's
  matches, and the snapshot the browser suite reads. Pure functions
  of the state map, memoized per run in a cache that a new run
  clears (orrery.state), so the views build on the JVM and Jolt as
  well as in the browser."
  (:require [cromulent.core :as eg]
            [cromulent.extract :as ex]
            [cromulent.pattern :as pat]
            [orrery.costs :as costs]
            [orrery.diff :as diff]
            [orrery.eclass :as eclass]
            [orrery.lessons :as lessons]
            [orrery.run :as run]))

(defonce ^:private cache (atom {}))

(defn clear-cache! [] (reset! cache {}))

(defn- cached [k f]
  (if (contains? @cache k)
    (get @cache k)
    (let [v (f)] (swap! cache assoc k v) v)))

(defn lesson [s] (lessons/by-key (:lesson s)))

(defn egraph-at
  "The e-graph at step k of the run, clamped to the timeline."
  [s k]
  (when-let [run (:run s)]
    (run/egraph-at run (max 0 (min k (run/last-step run))))))

(defn current-egraph [s] (egraph-at s (:step s)))

(defn diff-for
  "What step k of the run changed, or nil at step 0."
  [s k]
  (let [{:keys [run run-id]} s]
    (when (and run (pos? k))
      (cached [run-id k :diff]
              #(diff/between (run/egraph-at run (dec k)) (run/egraph-at run k))))))

(defn diff-at
  "What the current step changed, or nil at step 0."
  [s]
  (diff-for s (:step s)))

(defn best-at
  "An extractor over the current e-graph under the cost in force:
  (fn [id] {:cost :term}). One cost table per step and cost."
  [s]
  (let [{:keys [run run-id step cost]} s]
    (when run
      (cached [run-id step cost]
              #(let [g (run/egraph-at run step)]
                 (ex/extractor g (costs/cost-fn cost g)))))))

(defn cheapest-at
  "The few cheapest terms of every class of the e-graph at step k,
  under the cost in force (orrery.eclass/cheapest). One table per
  step and cost, computed when a class is first opened there."
  [s k]
  (let [{:keys [run run-id cost]} s]
    (when run
      (cached [run-id k cost :cheapest]
              #(let [g (run/egraph-at run k)]
                 (eclass/cheapest g (costs/cost-fn cost g) eclass/few))))))

(defn detail-at
  "What the class panel says about the selected class in the e-graph
  at step k, or nil when nothing is selected or that e-graph does not
  have the class yet: its root there, its nodes with their costs, its
  cheapest terms and how many it stands for, the classes it points at
  and the nodes that point at it, and its history along the run."
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
                                 (eclass/history (:timeline run) k root))})))))))

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
