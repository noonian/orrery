(ns orrery.page-test
  "The whole page, built on the JVM and Jolt: every live lesson, every
  step of its curated run, in either print mode, with its input class
  opened, with and without the graph picture (filtered to what the
  opened class reaches, zoomed), and with a REPL history, as the
  hiccup Replicant renders in the browser. Every event handler in it is data over
  orrery.actions and never a function, which is what lets the page
  build here at all."
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
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
   :ui {:print :notation :playing nil :selected nil :hover nil :drawing? false
        :graph? false :graph-filter? false :graph-zoom nil :export-status nil}})

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
          graph? [false true]
          :let [s (-> (state-for l run step) (assoc-in [:ui :print] mode))
                s (assoc-in s [:ui :selected] (derived/root-at s))
                s (cond-> s graph? (update :ui assoc :graph? true :graph-filter? true :graph-zoom 1.5 :export-status "copied"))
                h (page/page s)
                hs (vec (handlers h))
                where (str (:title l) ", step " step ", " (name mode) (when graph? ", the graph"))]]
    (is (vector? h) where)
    (is (seq hs) where)
    (is (every? actions/known? hs) (str where ": " (pr-str (remove actions/known? hs))))
    (is (empty? (functions h)) where)
    (is (some #(= [:select (derived/root-at s)] %) hs) (str where ": the class list links the input's class"))
    (is (some #(= [:deselect] %) hs) (str where ": the opened class can be closed"))
    (is (some #(= [:graph/toggle] %) hs) (str where ": the graph can be drawn or hidden"))
    (is (some #(= [:export/download] %) hs) (str where ": the step can be exported"))
    (is (= graph? (boolean (some #(= [:graph/zoom :fit] %) hs))) (str where ": the graph's controls are there when it is drawn"))
    (when graph?
      (is (some #(= [:tree/open (derived/root-at s)] %) hs) (str where ": the graph draws the input's class")))))

(deftest the-picture-is-the-lessons-until-the-switch-is-touched
  (let [zoom? (fn [l choice]
                (let [run (run/run-all (lessons/make-run l))
                      _ (derived/clear-cache!)
                      s (assoc-in (state-for l run (run/last-step run)) [:ui :graph?] choice)]
                  (boolean (some #(= [:graph/zoom :fit] %) (handlers (page/page s))))))]
    (is (zoom? lessons/basics nil) "the basics draw their picture unasked")
    (is (zoom? lessons/intro nil) "and the introduction")
    (is (not (zoom? lessons/intro false)) "and hides it when told to")
    (is (not (zoom? lessons/sharing nil)) "a lesson does not")
    (is (zoom? lessons/sharing true) "until it is asked")))

(deftest the-prose-links-the-lessons
  (let [l lessons/intro
        run (run/run-all (lessons/make-run l))
        _ (derived/clear-cache!)
        h (page/page (state-for l run (run/last-step run)))
        hrefs (set (keep :href (filter map? (tree-seq coll? seq h))))]
    (is (every? hrefs (map #(str "#" (lessons/address %)) lessons/all)))))

(defn- text
  "What an element says: its strings, its attributes aside."
  [x]
  (cond (string? x) x
        (map? x) ""
        (coll? x) (apply str (map text x))
        :else ""))

(defn- texts
  "What every element of hiccup with this tag says."
  [hiccup tag]
  (for [x (tree-seq coll? seq hiccup)
        :when (and (vector? x) (= tag (first x)))]
    (text x)))

(deftest the-panels-say-what-they-are-the-work-of
  (doseq [l lessons/all
          mode [:notation :native]
          :let [run (run/run-all (lessons/make-run l))
                _ (derived/clear-cache!)
                s (assoc-in (state-for l run (run/last-step run)) [:ui :print] mode)
                h (page/page s)
                op (lessons/operation l)
                where (str (:title l) ", " (name mode))]]
    (is (some #{(:name op)} (texts h :h3)) (str where ": the operation heads the inputs"))
    (is (some #{(:fn op)} (texts h :code.of)) (str where ": and names its function"))
    (is (some #{(str/join "\n" (lessons/call l {}))} (texts h :pre.call)) where)
    (is (= (map :arg (:inputs l)) (remove #{"cost"} (texts h :code.arg)))
        (str where ": a field per argument, by its name"))
    (is (= (> (count (:costs l)) 1) (boolean (some #{"cost"} (texts h :code.arg))))
        (str where ": and the cost, where there is one to choose"))
    (is (some #{"g at this step"} (texts h :code.of)) (str where ": the classes are g"))
    (when (derived/root-at s)
      (is (some #{"ex/extract"} (texts h :code.of)) where)))
  (testing "an alternative's options show in the call"
    (let [l lessons/saturation
          alt (first (filter #(= "five atoms under a node limit of 100" (:label %)) (:alternatives l)))
          run (run/run-all (lessons/make-run l (merge (:values l) (:values alt)) (:opts alt)))
          _ (derived/clear-cache!)
          s (assoc-in (state-for l run 0) [:input :opts] (:opts alt))]
      (is (some #(re-find #":node-limit 100" %) (texts (page/page s) :pre.call)))))
  (testing "a run adopted from the REPL says so"
    (let [l lessons/basics
          run (assoc (run/run-all (lessons/make-run l)) :from :repl)
          _ (derived/clear-cache!)]
      (is (some #{"from the REPL"} (texts (page/page (state-for l run 0)) :code.of))))))

(defn- page-at-the-end [l]
  (let [run (run/run-all (lessons/make-run l))]
    (derived/clear-cache!)
    (page/page (state-for l run (run/last-step run)))))

(defn- position
  "Where in the page, in reading order, the first element with this
  tag is, or nil."
  [hiccup tag]
  (first (keep-indexed (fn [i x] (when (and (vector? x) (= tag (first x))) i))
                       (tree-seq coll? seq hiccup))))

(deftest the-site-says-what-it-is-before-anything-links-into-the-widgets
  (let [h (page-at-the-end (lessons/by-key lessons/start))]
    (is (= [(text (into [:aside] lessons/about))]
           (map #(subs % (count "what this is")) (texts h :aside.about))))
    (is (< (position h :aside.about) (position h :h2) (position h :a.act))
        "over the heading, and the heading over the first link that does something")
    (is (empty? (filter #(and (vector? %) (= :a.act (first %)))
                        (tree-seq coll? seq (first (filter #(and (vector? %) (= :aside.about (first %)))
                                                           (tree-seq coll? seq h))))))
        "and nothing in it to pull"))
  (doseq [l lessons/all
          :let [h (page-at-the-end l)]]
    (is (= (= lessons/start (:key l)) (some? (position h :aside.about)))
        (str (:title l) ": only the page the site opens on says it at length"))
    (is (= [(text lessons/colophon)] (texts h :footer.colophon))
        (str (:title l) ": every page says it in a line"))
    (is (re-find #"largely written using LLMs" (first (texts h :footer.colophon))) (:title l))))

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
