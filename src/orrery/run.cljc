(ns orrery.run
  "One shape for every lesson's execution: a timeline of e-graph
  values with a label each, plus the runner's stats when the engine
  ran. A script (lessons 1, 2, 3, 10) lists its steps; a saturation
  (lessons 4 to 7) is stepped one iteration at a time, so a page can
  repaint between iterations and stop between them. Pure: the browser
  drives `step` from a timer, the JVM and Jolt tests drive `run-all`."
  (:require [cromulent.core :as eg]
            [cromulent.rewrite :as rw]))

(def defaults
  "Runner options unless a lesson says otherwise."
  {:scheduler :simple :iter-limit 30 :node-limit 5000})

(defn script
  "A run from [label egraph] steps, the first being the input."
  [steps]
  {:kind :script :timeline (mapv second steps) :labels (mapv first steps)
   :stats [] :iterations 0 :stop-reason :done :status :done :ms 0})

(defn start
  "A stepped saturation of term under rules: the term is added to an
  empty e-graph, rebuilt, and becomes entry 0; :root is its class.
  opts as `rw/embiggen` takes them, over `defaults`."
  [term rules opts]
  (let [[g root] (eg/add (eg/egraph) term)
        g (eg/rebuild g)]
    {:kind :embiggen :term term :rules rules :opts (merge defaults opts) :root root
     :timeline [g] :labels ["the input"] :stats [] :iterations 0
     :stop-reason nil :status :running :ms 0}))

(defn step
  "One more iteration, when the run is still running. Under the simple
  scheduler a run stepped this way is the run one `rw/embiggen` call
  makes: the scheduler keeps no state and a rebuilt graph rebuilds to
  itself. The backoff scheduler keeps bans across iterations that this
  does not yet carry (IDEA.md, the runner as start/step/finish)."
  [{:keys [timeline rules opts iterations status] :as run}]
  (if (not= :running status)
    run
    (let [g (peek timeline)
          res (rw/embiggen g rules (assoc opts :iter-limit 1 :timeline? false :time-limit-ms 600000))
          iter (inc iterations)
          reason (:stop-reason res)
          stop (cond (not= reason :iter-limit) reason
                     (>= iter (:iter-limit opts)) :iter-limit)
          stat (assoc (first (:stats res)) :iter iter)]
      (cond-> (-> run
                  (update :timeline conj (:egraph res))
                  (update :labels conj (str "iteration " iter))
                  (update :stats conj stat)
                  (assoc :iterations iter)
                  (update :ms + (:ms res)))
        stop (assoc :stop-reason stop :status :done)))))

(defn stop
  "The run, stopped where it is."
  [run]
  (if (= :running (:status run))
    (assoc run :status :done :stop-reason :stopped)
    run))

(defn run-all
  "Step to the end."
  [run]
  (loop [r run]
    (if (= :running (:status r)) (recur (step r)) r)))

(defn last-step [run] (dec (count (:timeline run))))

(defn egraph-at [run k] (nth (:timeline run) k))

(defn nodes-per-iteration [run] (mapv :nodes (:stats run)))
