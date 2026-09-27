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
            [cromulent.rewrite :as rw]
            [orrery.run :as run]
            [orrery.views.lesson :as page]
            [orrery.printed :as printed]
            [orrery.workbench :as workbench]))

(defn- state-for
  "The page of l over run at step, as the browser's atom would hold
  it, with the picture switched off."
  [l run step]
  (assoc-in (workbench/page l run step) [:ui :graph?] false))

(def ^:private hooks
  [:replicant/on-render :replicant/on-mount :replicant/on-unmount :replicant/on-update])

(defn- handlers
  "Every event handler in a hiccup tree, and every life-cycle hook,
  which Replicant routes through the same dispatch."
  [hiccup]
  (for [x (tree-seq coll? seq hiccup)
        :when (map? x)
        h (concat (vals (:on x)) (keep x hooks))]
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
          :when (seq (:inputs l))
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

(defn- elements
  "Every element of hiccup with this tag."
  [hiccup tag]
  (filter #(and (vector? %) (= tag (first %))) (tree-seq coll? seq hiccup)))

(deftest the-repl-has-a-page-of-its-own
  (let [l lessons/repl
        run (run/run-all (lessons/make-run l))
        _ (derived/clear-cache!)
        s (workbench/page l run)
        h (page/page s)
        hs (set (handlers h))]
    (testing "the editor beside the e-graph, over it in reading order, where a lesson has it under"
      (is (< (position h :div.panel.repl) (position h :table.classes)))
      (is (seq (elements h :div.workbench.beside)))
      (let [lesson (page-at-the-end lessons/taste)]
        (is (> (position lesson :div.panel.repl) (position lesson :table.classes)))
        (is (empty? (elements lesson :div.workbench.beside)))))
    (testing "no fields: the editor is the input"
      (is (empty? (elements h :textarea)) "prism-code-editor makes the editor's textarea")
      (is (= ["repl-input"] (map #(-> % second :replicant/on-render second :attrs :id) (elements h :div.editor)))
          "the one place to type")
      (is (empty? (texts h :pre.call)))
      (is (not (contains? hs [:run]))))
    (testing "every panel that reads a run, each saying what it is the work of"
      (is (= ["names in scope" "the REPL" "the classes" "the run" "best so far" "matches in this step" "the iterations" "the tree"]
             (texts h :h3)))
      (is (some #{"rw/saturate"} (texts h :code.of)))
      (is (some #{"user"} (texts h :code.of)))
      (is (contains? hs [:graph/zoom :fit]) "and the picture, unasked")
      (is (= 7 (count (filter #(and (vector? %) (= :cost (first %))) hs))) "every cost in the picker"))
    (testing "the prose's lines are the REPL's to evaluate, and the names in scope are listed"
      (let [lines (map second (filter #(= :eval (first %)) (lessons/widgets l)))]
        (is (seq lines))
        (is (every? #(contains? hs [:repl/run %]) lines)))
      (is (contains? hs [:repl/scroll]) "the history scrolls to its last entry")
      (let [names (first (elements h :aside.panel.names))]
        (is (every? (set (texts names :code)) ["g" "timeline" "state" "show!" "push!" "eg" "rw" "ex" "bx" "wb"]))))
    (testing "a lesson's REPL panel links to it"
      (is (some #{"#repl"} (keep :href (filter map? (tree-seq coll? seq (page-at-the-end lessons/taste)))))))))

(deftest what-the-repl-puts-on-show-builds
  (let [l lessons/repl
        curated (run/run-all (lessons/make-run l))
        g (peek (:timeline curated))
        [g' id] (eg/add g [:+ :b :b])
        [merged _] (eg/union g' (second (eg/add g' :a)) (second (eg/add g' :b)))
        page-of (fn [s]
                  (derived/clear-cache!)
                  (let [h (page/page s)]
                    (is (nil? (workbench/problem s)))
                    (is (empty? (functions h)))
                    (is (every? actions/known? (handlers h)))
                    h))
        base (workbench/page l curated)]
    (testing "an e-graph: the classes, and no panel that needs a root, rules or a term"
      (let [h (page-of (workbench/put base g'))]
        (is (= ["names in scope" "the REPL" "the classes" "the run"] (texts h :h3)))
        (is (some #{"from the REPL"} (texts h :code.of)))))
    (testing "a [g id] pair: the class to extract for"
      (let [s (workbench/put base [g' id])
            h (page-of s)]
        (is (= id (derived/root-at s)))
        (is (some #{"best so far"} (texts h :h3)))))
    (testing "steps pushed by hand, the second dirty until the third rebuilds it"
      (let [s (-> (workbench/put base [g' id])
                  (workbench/push merged "a = b, rebuild pending")
                  (workbench/push (eg/rebuild merged) "rebuilt"))]
        (is (= [2 3] [(:step s) (count (:timeline (:run s)))]))
        (is (= [6 5 4] (mapv eg/class-count (:timeline (:run s)))))
        (doseq [k (range 3)]
          (page-of (assoc-in (workbench/scrub s k) [:ui :selected] id)))))
    (testing "a runner's result: its timeline, and the rules its iterations counted as the table's columns"
      (let [res (rw/embiggen (first (eg/add (eg/egraph) [:+ :a :b])) (lessons/rules-of lessons/ac-rules)
                             {:scheduler :simple :timeline? true})
            s (workbench/put base res)
            h (page-of s)]
        (is (= 3 (count (:timeline (:run s)))))
        (is (every? (set (texts h :th)) ["assoc" "comm"]))))
    (testing "a run that has not run: on show at its input, to be stepped"
      (let [s (workbench/put base (run/start [:+ [:+ :a :b] :c] (lessons/rules-of lessons/ac-rules) {}))
            h (page-of s)]
        (is (= [:running 0] [(:status (:run s)) (:step s)]))
        (is (contains? (set (handlers h)) [:stop]))
        (is (some #{"the tree"} (texts h :h3)) "it knows the term it started from")))))

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
                                          {:in "(boom)" :error "no such var"}
                                          {:in "(println 1)" :ok nil :out "1\n"}
                                          {:in "(run/start t rules {})" :ok (lessons/make-run lessons/taste)}
                                          {:in "@state" :ok (state-for l run 0)}
                                          {:in "inc" :ok inc}]))
        h (page/page s)
        hs (set (handlers h))]
    (is (empty? (functions h)) "a function in the history is printed, not put in the page")
    (is (every? actions/known? hs))
    (is (contains? hs [:adopt 0]))
    (is (contains? hs [:adopt 1]))
    (is (contains? hs [:adopt 2]))
    (is (not (contains? hs [:adopt 3])) "a printed value has no button")
    (is (contains? hs [:adopt 6]) "a run has one")
    (is (= ["1\n"] (texts h :pre.out)) "what was printed stands over the value")
    (is (= ["2" "nil" "#function"] (remove #(re-find #"^\{" %) (texts h :pre.result))))
    (is (some #(re-find #"^a run: 1 step, 0 iterations, not run yet; 2 classes, 2 nodes$" %) (texts h :span.summary)))
    (testing "the text an evaluation printed of its value is the text shown"
      (let [h (page/page (assoc-in s [:repl :history] [{:in "(range 3)" :ok (range 3) :printed "(0 1 2), as printed then"}]))]
        (is (= ["(0 1 2), as printed then"] (texts h :pre.result)))))))

(deftest a-value-is-printed-abridged
  (let [run (run/run-all (lessons/make-run lessons/blowup))
        g (peek (:timeline run))
        s (-> (workbench/page lessons/blowup run)
              (assoc-in [:repl :history] [{:in "g" :ok g} {:in "1" :ok 1}]))]
    (is (= "[#egraph[31 classes, 185 nodes]]" (printed/printed [g])) "an e-graph inside a value, by its counts")
    (is (re-find #"^\(0 1 2 .* 47 …\)$" (printed/printed (range))) "an endless sequence, cut")
    (is (= (inc printed/most) (count (re-seq #"[^ ()]+" (printed/printed (range))))) "at `most` elements and the mark")
    (is (= "[[[[[[…]]]]]]" (printed/printed [[[[[[[1]]]]]]])) "and one nested deep, closed")
    (is (= "(atom {:a 1})" (printed/printed (atom {:a 1}))))
    (is (= "#function" (printed/printed inc)))
    (is (= "{:term [:+ :a 1/2]}" (printed/printed {:term [:+ :a 1/2]})))
    (testing "the state of the page: its run in a line, its history by its length"
      (let [text (printed/printed s)]
        (is (< (count text) 1200))
        (is (re-find #":run #run\[8 steps, 7 iterations, saturated" text))
        (is (re-find #":history #history\[2\]" text))
        (is (re-find #":lesson :blowup" text))))))

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
