(ns orrery.printed
  "Prints a value of the REPL as abridged text. The history shows
  this text for a value that has no view of its own.

  The REPL prints a value while it is still evaluating (see
  orrery.state). So when a lazy sequence throws as it is walked, the
  error belongs to its evaluation and not to the page's next render."
  (:require [cromulent.core :as eg]
            [orrery.diff :as diff]
            [orrery.workbench :as workbench]))

(def most
  "The maximum number of elements of a collection that a printed
  value shows."
  48)

(def deepest
  "The maximum depth of nested collections that a printed value
  shows."
  6)

(def longest
  "The maximum number of characters that a printed value shows of
  anything that is not a collection."
  240)

(def ^:private more (symbol "…"))

(defn egraph-summary
  "Returns a one-line summary of the e-graph `g`: its counts of
  classes and nodes."
  [g]
  (str (eg/class-count g) " classes, " (eg/node-count g) " nodes"
       (when (:dirty? g) ", dirty: rebuild pending")))

(defn- stop-text [reason]
  (case reason
    nil "not stopped"
    :saturated "saturated"
    :done "done"
    :stopped "stopped"
    (str "stopped at the " (subs (str reason) 1))))

(defn run-summary
  "Returns a one-line summary of a run: how many steps it has, how it
  ended, and the summary of its last e-graph."
  [{:keys [timeline iterations stop-reason status]}]
  (str (count timeline) (if (= 1 (count timeline)) " step, " " steps, ")
       iterations " iterations, "
       (if (= :running status) "not run yet" (stop-text stop-reason))
       "; " (egraph-summary (peek timeline))))

(defn- atom? [v]
  #?(:cljs (instance? Atom v)
     :default (instance? clojure.lang.Atom v)))

(defn- state?
  "Returns true when `v` looks like a state of the page. The check
  is only as strict as printing needs."
  [v]
  (and (map? v) (contains? v :run-id) (vector? (get-in v [:repl :history]))))

(defn abridge
  "Returns `v` as the history prints it. The result is a value to
  pass to `pr-str`.

    - An e-graph in `v` becomes its counts, since an e-graph printed
      whole is pages of vectors.
    - A run becomes its steps and how it ended.
    - In a state of the page, the REPL's history becomes its length.
      The reason is that a swap of `state` returns the state, and
      the history holds every value so far.
    - A collection of more than `most` elements is cut there. The
      cut is also what lets an endless sequence print.
    - A collection `deepest` levels down is left closed.
    - An atom becomes what it holds.
    - A function becomes a symbol that names its kind."
  ([v] (abridge v 0))
  ([v depth]
   (let [in (fn [x] (abridge x (inc depth)))
         some-of (fn [xs]
                   (let [shown (mapv in (take most xs))]
                     (if (seq (drop most xs)) (conj shown more) shown)))]
     (cond
       (diff/egraph? v) (symbol (str "#egraph[" (egraph-summary v) "]"))
       (workbench/run? v) (symbol (str "#run[" (run-summary v) "]"))
       (state? v) (in (-> (into {} v)
                          (dissoc :run-id)
                          (update-in [:repl :history] #(symbol (str "#history[" (count %) "]")))))
       (or (nil? v) (number? v) (keyword? v) (symbol? v) (boolean? v) (char? v)) v
       (string? v) (if (> (count v) longest) (str (subs v 0 longest) "…") v)
       (fn? v) (symbol "#function")
       (atom? v) (list 'atom (in @v))
       (and (coll? v) (>= depth deepest)) more
       (map? v) (let [n (count v)]
                  (cond-> (into {} (map (fn [[k x]] [(in k) (in x)])) (take most v))
                    (> n most) (assoc more (symbol (str (- n most) " more")))))
       (vector? v) (some-of v)
       (set? v) (set (some-of v))
       (coll? v) (apply list (some-of v))
       :else (let [s (pr-str v)]
               (if (> (count s) longest) (symbol (str (subs s 0 longest) "…")) v))))))

(defn printed
  "Returns the text of `v`, abridged."
  [v]
  (pr-str (abridge v)))
