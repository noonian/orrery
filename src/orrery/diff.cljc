(ns orrery.diff
  "Computes what changed between two e-graph values of one run.

  Ids only grow along a run. So the later graph's union-find covers
  every id of the earlier graph, and the earlier graph's nodes can be
  canonicalized in the later graph.

  The functions are pure. The counts are the same on every runtime,
  but the ids are not."
  (:require [cromulent.core :as eg]))

(defn egraph?
  "Returns true when `x` is an e-graph value. A sorted map is none:
  in ClojureScript, looking up a keyword in a sorted map with number
  keys throws."
  [x]
  (and (map? x) (not (sorted? x)) (vector? (:uf x)) (vector? (:classes x)) (map? (:memo x))))

(defn between
  "Returns what changed from `g0` to `g1`. `g1` is later than `g0` in
  the same run. The result is a map:

    :new-classes #{root}
      The roots of `g1` that hold nothing of `g0`. Such a root is
      neither an old root nor a class an old root merged into.
    :merged {old new}
      The roots of `g0` that are no longer roots, and where they
      went.
    :absorbing #{root}
      The roots of `g1` that received a merge.
    :added {root #{node}}
      The nodes of `g1` that `g0` did not have, by class.
    :added-count n
      The number of nodes in :added.
    :collapsed n
      How many fewer nodes `g0` has under the partition of `g1`,
      because some of its nodes became one node."
  [g0 g1]
  (let [roots0 (eg/roots g0)
        roots1 (eg/roots g1)
        merged (into {} (keep (fn [r] (when-not (eg/root? g1 r) [r (eg/find g1 r)]))) roots0)
        absorbing (set (vals merged))
        next0 (:next-id g0)
        new-classes (into #{} (filter #(and (>= % next0) (not (absorbing %)))) roots1)
        nodes0 (into [] (mapcat #(eg/nodes g0 %)) roots0)
        nodes0-in-1 (into #{} (map #(eg/canonicalize g1 %)) nodes0)
        added (into {} (keep (fn [r]
                               (let [ns (remove nodes0-in-1 (eg/nodes g1 r))]
                                 (when (seq ns) [r (set ns)]))))
                    roots1)]
    {:new-classes new-classes
     :merged merged
     :absorbing absorbing
     :added added
     :added-count (reduce + 0 (map count (vals added)))
     :collapsed (- (count nodes0) (count nodes0-in-1))}))
