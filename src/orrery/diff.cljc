(ns orrery.diff
  "What changed between two e-graph values of one run. Ids only grow
  along a run, so the later graph's union-find covers every id of the
  earlier one and the earlier graph's nodes can be canonicalized in
  the later. Pure; counts are the same on every runtime, ids are not."
  (:require [cromulent.core :as eg]))

(defn egraph?
  "Is x an e-graph value?"
  [x]
  (and (map? x) (vector? (:uf x)) (vector? (:classes x)) (map? (:memo x))))

(defn between
  "From g0 to g1, later in the same run:

    :new-classes #{root}        roots of g1 that g0 did not have
    :merged      {old new}      roots of g0 that are roots no longer, and where they went
    :absorbing   #{root}        roots of g1 that received a merge
    :added       {root #{node}} nodes of g1 that g0 did not have, by class
    :added-count n
    :collapsed   n              nodes of g0 that became one node under g1's partition"
  [g0 g1]
  (let [roots0 (eg/roots g0)
        roots1 (eg/roots g1)
        merged (into {} (keep (fn [r] (when-not (eg/root? g1 r) [r (eg/find g1 r)]))) roots0)
        absorbing (set (vals merged))
        next0 (:next-id g0)
        new-classes (into #{} (filter #(>= % next0)) roots1)
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
