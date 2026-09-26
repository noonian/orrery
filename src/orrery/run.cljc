(ns orrery.run
  "One shape for every lesson's execution: a timeline of e-graph
  values with a label each, plus the runner's stats when the engine
  ran. A script (lessons 1, 2, 3, 10) lists its steps; a saturation
  (lessons 4 to 7) is stepped one iteration at a time over
  cromulent's start/step/finish, so a page can repaint between
  iterations and stop between them, and the run is exactly the one
  `rw/embiggen` would make, bans and all. Pure: the browser drives
  `step` from a timer, the JVM and Jolt tests drive `run-all`."
  (:require [cromulent.core :as eg]
            [cromulent.rewrite :as rw]))

(def defaults
  "Runner options unless a lesson says otherwise."
  {:scheduler :simple :iter-limit 30 :node-limit 5000 :time-limit-ms 600000})

(defn script
  "A run from [label egraph] steps, the first being the input; root
  is the class to extract for, if any."
  ([steps] (script steps nil))
  ([steps root]
   {:kind :script :timeline (mapv second steps) :labels (mapv first steps) :root root
    :stats [] :iterations 0 :stop-reason :done :status :done :ms 0}))

(defn start
  "A stepped saturation of term under rules: the term is added to an
  empty e-graph, rebuilt, and becomes entry 0; :root is its class.
  opts as `rw/embiggen` takes them, over `defaults`."
  [term rules opts]
  (let [[g root] (eg/add (eg/egraph) term)
        opts (merge defaults opts)
        engine (rw/start g rules opts)]
    {:kind :embiggen :term term :rules rules :opts opts :root root
     :engine engine
     :timeline [(:egraph engine)] :labels ["the input"] :stats [] :iterations 0
     :stop-reason nil :status :running :ms 0}))

(defn step
  "One more iteration, when the run is still running."
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
                 (dissoc :engine))))))

(defn stop
  "The run, stopped where it is."
  [run]
  (if (= :running (:status run))
    (-> run (assoc :status :done :stop-reason :stopped)
        (assoc :ms (if-let [e (:engine run)] (:ms (rw/finish e)) (:ms run)))
        (dissoc :engine))
    run))

(defn run-all
  "Step to the end."
  [run]
  (loop [r run]
    (if (= :running (:status r)) (recur (step r)) r)))

(defn last-step [run] (dec (count (:timeline run))))

(defn egraph-at [run k] (nth (:timeline run) k))

(defn nodes-per-iteration [run] (mapv :nodes (:stats run)))
