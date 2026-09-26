(ns orrery.page-test
  "The whole page, built on the JVM and Jolt: every live lesson, every
  step of its curated run, in either print mode, with its input class
  opened and with a REPL history, as the hiccup Replicant renders in
  the browser. Every event handler in it is data over
  orrery.actions and never a function, which is what lets the page
  build here at all."
  (:require [clojure.test :refer [deftest is testing]]
            [cromulent.core :as eg]
            [orrery.actions :as actions]
            [orrery.derived :as derived]
            [orrery.lessons :as lessons]
            [orrery.run :as run]
            [orrery.views.lesson :as page]))

(defn- state-for [l run step]
  {:lesson (:key l)
   :input {:fields {} :values (:values l) :error nil :alternative nil :opts {}}
   :run run :run-id 1 :step step :follow? true
   :cost (or (first (:costs l)) :ast-size)
   :repl {:input "" :history []}
   :ui {:print :notation :playing nil :selected nil :hover nil :drawing? false}})

(defn- handlers
  "Every event handler in a hiccup tree."
  [hiccup]
  (for [x (tree-seq coll? seq hiccup)
        :when (and (map? x) (contains? x :on))
        [_ h] (:on x)]
    h))

(defn- functions
  "Every function anywhere in a hiccup tree: there must be none."
  [hiccup]
  (filter fn? (tree-seq coll? seq hiccup)))

(deftest the-page-builds-for-every-lesson-at-every-step
  (doseq [l lessons/all :when (lessons/live? l)
          :let [run (run/run-all (lessons/make-run l))
                ;; one run id per lesson here, as the app's install-run! does per run
                _ (derived/clear-cache!)]
          step (range (count (:timeline run)))
          mode [:notation :native]
          :let [s (-> (state-for l run step) (assoc-in [:ui :print] mode))
                s (assoc-in s [:ui :selected] (derived/root-at s))
                h (page/page s)
                hs (vec (handlers h))
                where (str (:title l) ", step " step ", " (name mode))]]
    (is (vector? h) where)
    (is (seq hs) where)
    (is (every? actions/known? hs) (str where ": " (pr-str (remove actions/known? hs))))
    (is (empty? (functions h)) where)
    (is (some #(= [:select (derived/root-at s)] %) hs) (str where ": the class list links the input's class"))
    (is (some #(= [:deselect] %) hs) (str where ": the opened class can be closed"))))

(deftest the-repl-panel-builds-with-a-history
  (let [l lessons/tree
        run (run/run-all (lessons/make-run l))
        _ (derived/clear-cache!)
        g (peek (:timeline run))
        s (-> (state-for l run 0)
              (assoc-in [:repl :history] [{:in "g" :ok g}
                                          {:in "[g 0]" :ok [g 0]}
                                          {:in "(rw/embiggen g [])" :ok {:egraph g :stats [] :iterations 0 :stop-reason :saturated}}
                                          {:in "(+ 1 1)" :ok 2}
                                          {:in "(boom)" :error "no such var"}]))
        h (page/page s)
        hs (set (handlers h))]
    (is (empty? (functions h)))
    (is (every? actions/known? hs))
    (is (contains? hs [:adopt 0]))
    (is (contains? hs [:adopt 1]))
    (is (contains? hs [:adopt 2]))
    (is (not (contains? hs [:adopt 3])) "a printed value has no button")))

(deftest the-snapshot-reads-the-same-here
  (let [l lessons/blowup
        run (run/run-all (lessons/make-run l))
        _ (derived/clear-cache!)
        s (state-for l run (run/last-step run))
        snap (derived/snapshot s)]
    (is (= {:lesson "blowup" :steps 8 :status "done" :iterations 7 :stopReason "saturated" :classes 31 :nodes 185 :dirty false :bestCost 9}
           (dissoc snap :step)))
    (is (= 7 (:step snap)))
    (testing "the action table documents every action"
      (is (every? string? (vals actions/all))))))
