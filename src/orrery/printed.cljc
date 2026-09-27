(ns orrery.printed
  "A value of the REPL as text, abridged: what the history shows for
  a value with no view of its own. The REPL prints a value while it
  is still evaluating, so a lazy sequence that throws when it is
  walked is an error of its evaluation and not of the page's next
  render (orrery.state)."
  (:require [cromulent.core :as eg]
            [orrery.diff :as diff]
            [orrery.workbench :as workbench]))

(def most
  "How many elements of a collection a printed value shows."
  48)

(def deepest
  "How many collections deep a printed value goes."
  6)

(def longest
  "How many characters of anything that is not a collection."
  240)

(def ^:private more (symbol "…"))

(defn egraph-summary
  "An e-graph in a line: its counts."
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
  "A run in a line: its steps, how it ended, its last e-graph."
  [{:keys [timeline iterations stop-reason status]}]
  (str (count timeline) (if (= 1 (count timeline)) " step, " " steps, ")
       iterations " iterations, "
       (if (= :running status) "not run yet" (stop-text stop-reason))
       "; " (egraph-summary (peek timeline))))

(defn- atom? [v]
  #?(:cljs (instance? Atom v)
     :default (instance? clojure.lang.Atom v)))

(defn- state?
  "Is v a state of the page, as far as printing it goes?"
  [v]
  (and (map? v) (contains? v :run-id) (vector? (get-in v [:repl :history]))))

(defn abridge
  "v as the history prints it, a value `pr-str` takes: an e-graph in
  it as its counts, since one printed whole is pages of vectors, a
  run as its steps and how it ended, and in a state of the page the
  REPL's history as its length, since a swap of `state` returns the
  state and the history holds every value so far; a collection past
  `most` elements cut there, which is also what lets an endless
  sequence print; a collection `deepest` levels down left closed; an
  atom as what it holds; a function by its kind."
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
  "The text of v, abridged."
  [v]
  (pr-str (abridge v)))
