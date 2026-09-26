(ns orrery.expect
  "What each lesson's run must produce, the same on the JVM, on Jolt
  and in the browser: counts, iterations, stop reasons and costs, and
  a term only where its cost is a unique minimum. Never class ids,
  and never a tied term, because the tie falls to ids and ids follow
  hash iteration order, which differs per runtime. `checks` runs every
  live lesson and its alternatives; orrery.lessons-test asserts it on
  the JVM and Jolt, the page's self-test in the browser."
  (:require [cromulent.core :as eg]
            [cromulent.extract :as ex]
            [orrery.costs :as costs]
            [orrery.lessons :as lessons]
            [orrery.run :as run]))

(def runs
  "By lesson key, then by alternative label (nil for the curated
  example): the keys of `observe` that must match."
  {:tree
   {nil {:steps 1 :classes 5 :nodes 5 :tree-nodes 5}
    "(x + 1)·(y − 2)" {:classes 7 :nodes 7 :tree-nodes 7}
    "sin(2·x)" {:classes 4 :nodes 4 :tree-nodes 4}
    "(x + 1)³" {:classes 5 :nodes 5 :tree-nodes 5}}
   :sharing
   {nil {:steps 1 :classes 4 :nodes 4 :tree-nodes 7}
    "a·b + a·b" {:classes 4 :nodes 4 :tree-nodes 7}
    "(x + 2·y)·(x + 2·y)" {:classes 6 :nodes 6 :tree-nodes 11}
    "(a + a) + (a + a)" {:classes 3 :nodes 3 :tree-nodes 7}}
   :congruence
   {nil {:steps 3 :classes-per-step [7 6 5] :classes 5 :nodes 6 :dirty-per-step [false true false]}
    "x + 0 = x, under a sine" {:steps 3 :classes-per-step [5 4 3] :classes 3 :nodes 4}
    "deeper: (?x + 1)·(?x + 1)" {:steps 3 :classes-per-step [9 8 6] :classes 6 :nodes 7}}
   :rule
   {nil {:iterations 1 :stop-reason :iter-limit :classes-per-step [6 7] :classes 7 :nodes 9
         :best-terms {:prefer-shift [:+ [:<< :a 1] [:<< :b 1]]}}
    "egg's rules on 0 + 1·a, one iteration" {:iterations 1 :stop-reason :iter-limit :classes 5 :nodes 7}
    "commutativity on a + b" {:iterations 1 :stop-reason :iter-limit :classes 3 :nodes 4}}
   :saturation
   {nil {:stop-reason :saturated :classes 15 :nodes 54 :banned? true :quiet-end? true}
    "the same, every match every time" {:iterations 6 :stop-reason :saturated :classes 15 :nodes 54 :banned? false}
    "egg's rules on 0 + 1·a" {:iterations 3 :stop-reason :saturated :classes 3 :nodes 7 :best-cost 1}
    "five atoms under a node limit of 100" {:stop-reason :node-limit}}
   :taste
   {nil {:iterations 3 :stop-reason :saturated :classes 4 :nodes 6
         :best-terms {:prefer-add [:+ :a :a] :prefer-mul [:* :a 2] :prefer-shift [:<< :a 1]}}
    "(b·2) + (b·2)" {:stop-reason :saturated
                     :best-terms {:prefer-add [:+ [:+ :b :b] [:+ :b :b]] :prefer-mul [:* [:* :b 2] 2] :prefer-shift [:<< [:<< :b 1] 1]}}
    "with commutativity" {:stop-reason :saturated :best-terms {:prefer-add [:+ :a :a] :prefer-shift [:<< :a 1]}}}
   :blowup
   {nil {:iterations 7 :stop-reason :saturated :classes 31 :nodes 185
         :nodes-per-iteration [19 45 98 162 187 185 185] :best-cost 9}
    "four atoms" {:iterations 6 :stop-reason :saturated :classes 15 :nodes 54
                  :nodes-per-iteration [14 28 44 54 54 54] :best-cost 7}
    "six atoms" {:iterations 8 :stop-reason :saturated :classes 63 :nodes 608
                 :nodes-per-iteration [24 62 178 393 599 611 608 608] :best-cost 11}
    "six atoms under a node limit of 500" {:iterations 5 :stop-reason :node-limit :classes 92 :nodes 599
                                            :nodes-per-iteration [24 62 178 393 599] :best-cost 11}}
   :what-if
   {nil {:steps 3 :classes-per-step [5 4 3] :classes 3 :nodes 4 :dirty-per-step [false true false]}
    "x·y + y·x, what if y = x" {:steps 3 :classes-per-step [5 4 3] :classes 3 :nodes 4}
    "sin x + sin y, what if x = y" {:steps 3 :classes-per-step [5 4 3] :classes 3 :nodes 4}}})

(defn- tree-nodes [t] (count (tree-seq vector? rest t)))

(defn observe
  "Everything `runs` may assert about a finished run of lesson."
  [lesson values run]
  (let [g (peek (:timeline run))
        root (when-let [r (:root run)] (eg/find g r))
        stats (:stats run)]
    {:steps (count (:timeline run))
     :iterations (:iterations run)
     :stop-reason (:stop-reason run)
     :classes (eg/class-count g)
     :nodes (eg/node-count g)
     :tree-nodes (when-let [t (:term values)] (tree-nodes t))
     :classes-per-step (mapv eg/class-count (:timeline run))
     :dirty-per-step (mapv (comp boolean :dirty?) (:timeline run))
     :nodes-per-iteration (run/nodes-per-iteration run)
     :banned? (boolean (some (comp seq :banned) stats))
     :quiet-end? (boolean (and (seq stats) (every? zero? (vals (:applied (peek stats))))))
     :best-cost (when root (:cost (ex/extract g root)))
     :best-terms (when root
                   (into {} (for [c (:costs lesson)]
                              [c (:term (ex/extract g root (costs/cost-fn c)))])))}))

(defn- narrow
  "observed cut down to what expected mentions."
  [expected observed]
  (let [o (select-keys observed (keys expected))]
    (if (:best-terms expected)
      (update o :best-terms select-keys (keys (:best-terms expected)))
      o)))

(defn checks
  "One {:name :expected :actual :ok?} per live lesson and alternative."
  []
  (vec
   (for [lesson lessons/all
         :when (lessons/live? lesson)
         [label expected] (get runs (:key lesson) {nil {}})
         :let [alt (some (fn [a] (when (= label (:label a)) a)) (:alternatives lesson))
               values (if alt (merge (:values lesson) (:values alt)) (:values lesson))
               run (run/run-all (lessons/make-run lesson values (or (:opts alt) {})))
               actual (narrow expected (observe lesson values run))]]
     {:name (str (:n lesson) ". " (:title lesson) (when label (str " · " label)))
      :expected expected :actual actual :ok? (= expected actual)})))
