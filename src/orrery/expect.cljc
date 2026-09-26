(ns orrery.expect
  "What each lesson's run must produce, the same on the JVM, on Jolt
  and in the browser: counts, iterations, stop reasons and costs.
  Never class ids, and never a term whose cost is tied, because the
  tie falls to ids and ids follow hash iteration order, which differs
  per runtime. Asserted by orrery.lessons-test on the JVM and Jolt and
  by the page's self-test in the browser.")

(def runs
  "By lesson key, then by alternative label (nil for the curated example):
  the run's :iterations, :stop-reason, final :classes and :nodes, the
  :nodes after each iteration, and the :best-cost of the input's class
  under AST size."
  {:blowup
   {nil {:iterations 7 :stop-reason :saturated :classes 31 :nodes 185
         :nodes-per-iteration [19 45 98 162 187 185 185] :best-cost 9}
    "four atoms" {:iterations 6 :stop-reason :saturated :classes 15 :nodes 54
                  :nodes-per-iteration [14 28 44 54 54 54] :best-cost 7}
    "six atoms" {:iterations 8 :stop-reason :saturated :classes 63 :nodes 608
                 :nodes-per-iteration [24 62 178 393 599 611 608 608] :best-cost 11}
    "six atoms under a node limit of 500" {:iterations 5 :stop-reason :node-limit :classes 92 :nodes 599
                                            :nodes-per-iteration [24 62 178 393 599] :best-cost 11}}})
