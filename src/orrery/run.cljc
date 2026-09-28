(ns orrery.run
  "A run: one shape for every lesson's execution. A run is a timeline
  of e-graph values with a label for each, plus the runner's stats
  when the engine ran.

  A script (lessons 1, 2, 3, 11) lists its steps. A saturation
  (lessons 4 to 7) is stepped one iteration at a time, over
  cromulent's start/step/finish. So a page can repaint between
  iterations and stop between them. The run is exactly the one
  `rw/embiggen` would make, including its bans.

  The functions are pure. The browser drives `step` from a timer,
  and the JVM and Jolt tests drive `run-all`."
  (:require [cromulent.core :as eg]
            [cromulent.rewrite :as rw]))

(def defaults
  "The runner options a run uses unless a lesson says otherwise."
  {:scheduler :simple :iter-limit 30 :node-limit 5000 :time-limit-ms 600000})

(defn script
  "Returns a run made from `steps`, a sequence of [label egraph]
  pairs. The first step is the input. `root` is the class to extract
  for, if there is one."
  ([steps] (script steps nil))
  ([steps root]
   {:kind :script :timeline (mapv second steps) :labels (mapv first steps) :root root
    :stats [] :iterations 0 :stop-reason :done :status :done :ms 0}))

(defn- after-steps
  "Returns the run with its :after steps applied. Each step is a
  pair [label f]. It calls `f` on the last e-graph and adds the
  result as one more entry, under `label`."
  [run]
  (reduce (fn [run [label f]]
            (-> run
                (update :timeline conj (f (peek (:timeline run))))
                (update :labels conj label)))
          run
          (:after run)))

(defn start
  "Returns a stepped saturation of `term` under `rules`. The term is
  added to an empty e-graph. That e-graph is rebuilt and becomes
  entry 0 of the timeline. :root is the term's class.

  `opts` are the options that `rw/embiggen` takes, merged over
  `defaults`, plus two more:

    :egraph  The empty e-graph to start from. It can be one that
             carries an analysis, for example.
    :after   Steps [label f] that are appended once the engine
             stops. `f` takes the last e-graph and returns the
             next. A materialization is such a step."
  [term rules opts]
  (let [{:keys [egraph after]} opts
        [g root] (eg/add (or egraph (eg/egraph)) term)
        opts (merge defaults (dissoc opts :egraph :after))
        engine (rw/start g rules opts)]
    {:kind :embiggen :term term :rules rules :opts opts :root root :after (vec after)
     :engine engine
     :timeline [(:egraph engine)] :labels ["the input"] :stats [] :iterations 0
     :stop-reason nil :status :running :ms 0}))

(defn step
  "Returns the run after one more iteration. Returns the run
  unchanged when it is no longer running."
  [{:keys [engine status] :as run}]
  (if (not= :running status)
    run
    (let [engine' (rw/step engine)
          advanced? (> (count (:stats engine')) (count (:stats engine)))
          iter (count (:stats engine'))
          stop (:stop-reason engine')]
      (cond-> (assoc run :engine engine')
        advanced? (-> (update :timeline conj (:egraph engine'))
                      (update :labels conj (str "iteration " iter))
                      (update :stats conj (peek (:stats engine')))
                      (assoc :iterations iter))
        stop (-> (assoc :stop-reason stop :status :done)
                 (assoc :ms (:ms (rw/finish engine')))
                 (dissoc :engine)
                 after-steps)))))

(defn stop
  "Returns the run, stopped where it is."
  [run]
  (if (= :running (:status run))
    (-> run (assoc :status :done :stop-reason :stopped)
        (assoc :ms (if-let [e (:engine run)] (:ms (rw/finish e)) (:ms run)))
        (dissoc :engine)
        after-steps)
    run))

(defn run-all
  "Steps the run to its end and returns it."
  [run]
  (loop [r run]
    (if (= :running (:status r)) (recur (step r)) r)))

(defn last-step [run] (dec (count (:timeline run))))

(defn egraph-at [run k] (nth (:timeline run) k))

(defn nodes-per-iteration [run] (mapv :nodes (:stats run)))
