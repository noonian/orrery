(ns orrery.lessons-test
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [cromulent.core :as eg]
            [orrery.eclass :as eclass]
            [orrery.expect :as expect]
            [orrery.notation :as notation]
            [orrery.lessons :as lessons]
            [orrery.names :as names]
            [orrery.run :as run]))

(deftest every-live-lesson-meets-its-expectation
  (doseq [c (expect/checks)]
    (is (:ok? c) (pr-str (dissoc c :ok?)))))

(deftest every-live-lesson-has-an-expectation
  (doseq [l lessons/all :when (lessons/live? l)]
    (is (map? (get expect/runs (:key l))) (:title l))
    (doseq [a (:alternatives l)]
      (is (contains? (get expect/runs (:key l)) (:label a)) (str (:title l) " · " (:label a))))))

(defn- balanced?
  "Do the brackets of code close, strings aside?"
  [code]
  (let [bare (str/replace code #"\"(?:[^\"\\]|\\.)*\"" "")
        closes {\) \( \] \[ \} \{}]
    (empty? (reduce (fn [open c]
                      (cond (#{\( \[ \{} c) (conj open c)
                            (closes c) (if (= (closes c) (peek open)) (pop open) (reduced [:unbalanced]))
                            :else open))
                    []
                    bare))))

(deftest prose-widgets-hold-for-the-curated-run
  (doseq [l lessons/all
          :when (lessons/live? l)
          :let [run (run/run-all (lessons/make-run l))
                last (run/last-step run)
                labels (set (map :label (:alternatives l)))
                costs (set (:costs l))
                where (fn [w] (str (:title l) ": " (pr-str w)))]
          w (lessons/widgets l)
          :let [[kind a b c] w]]
    (case kind
      :notation (is (string? (notation/term->str a)) (where w))
      :native (is (string? (pr-str a)) (where w))
      :step (is (<= 0 a last) (where w))
      :select (do (is (string? b) (where w))
                  (is (or (nil? c) (<= 0 c last)) (where w))
                  (is (some? (eclass/class-of (run/egraph-at run (or c last)) a)) (where w)))
      :cost (is (contains? costs a) (where w))
      :alternative (is (contains? labels a) (where w))
      :print (is (contains? #{:native :notation} a) (where w))
      :lesson (do (is (string? b) (where w))
                  (is (some-> (lessons/by-key a) lessons/live?) (where w))
                  (is (not= a (:key l)) (where w)))
      :eval (do (is (and (string? a) (seq a)) (where w))
                (is (= 2 (count w)) (str (where w) ": the code is the link"))
                (is (balanced? a) (where w))
                (is (every? names/aliases (names/qualifiers a)) (str (where w) ": every namespace it names is in scope")))
      :cite (do (is (seq (rest w)) (where w))
                (is (every? #(contains? lessons/reading %) (rest w)) (where w)))))
  (is (seq (lessons/widgets lessons/tree)))
  (testing "the check on a line to evaluate"
    (is (balanced? "(push! (eg/rebuild g) \"a ) in a string\")"))
    (is (not (balanced? "(eg/add g [:+ :a :b)")))
    (is (not (balanced? "(eg/add g :a))")))))

(deftest every-citation-resolves-and-every-work-is-cited
  (doseq [l lessons/all :when (lessons/live? l)]
    (is (seq (lessons/credits l)) (str (:title l) " cites someone"))
    (doseq [k (lessons/credits l)]
      (is (contains? lessons/reading k) (str (:title l) ": " k))))
  (doseq [[k work] lessons/reading]
    (is (some #(some #{k} (lessons/credits %)) lessons/all) (str k " is cited by a lesson"))
    (is (every? work [:who :what :where :year :short]) (str k))
    (when-let [url (:url work)]
      (is (re-find #"^https://" url) (str k)))))

(defn- links [l] (map second (filter #(= :lesson (first %)) (lessons/widgets l))))

(deftest the-page-opens-on-the-basics
  (let [l (lessons/by-key lessons/start)
        [before others] (split-with (comp nil? :n) lessons/all)
        [numbered after] (split-with :n others)]
    (is (= [lessons/basics lessons/intro] before) "two pages before the lessons, the basics first")
    (is (= [lessons/repl] after) "and one after them, the REPL's")
    (is (= lessons/basics l))
    (is (lessons/live? l))
    (is (= (range 1 12) (map :n numbered)) "the lessons are 1 to 11, in order")
    (testing "a page before the lessons goes by its title alone, and by its key in the address"
      (is (= "Many ways to write one thing" (lessons/heading l)))
      (is (= "Start here" (lessons/nav-label l)))
      (is (= "What is an e-graph?" (lessons/heading lessons/intro) (lessons/nav-label lessons/intro)))
      (is (= "7. The blowup" (lessons/heading lessons/blowup) (lessons/nav-label lessons/blowup)))
      (is (= "The REPL" (lessons/heading lessons/repl) (lessons/nav-label lessons/repl)))
      (is (= ["basics" "intro" "1" "11" "repl"]
             (map lessons/address [l lessons/intro lessons/tree lessons/differentiation lessons/repl]))))
    (testing "every address finds its lesson, and nothing else finds one"
      (doseq [x lessons/all]
        (is (= x (lessons/by-address (lessons/address x)))))
      (is (apply distinct? (map lessons/address lessons/all)))
      (is (every? nil? (map lessons/by-address ["" "0" "12" "nowhere"]))))
    (is (= [:intro] (links l)) "the basics hand the reader to the introduction")
    (is (= (set (map :key numbered)) (set (links lessons/intro))) "whose prose links every lesson")))

(defn- said
  "What hiccup says: its strings, in order."
  [x]
  (apply str (filter string? (tree-seq coll? seq x))))

(deftest the-site-says-what-it-is
  (let [text (said lessons/about)]
    (is (every? #(and (vector? %) (= :p (first %))) lessons/about))
    (is (empty? (lessons/widgets {:prose lessons/about})) "nothing in it links into the widgets")
    (doseq [word ["interactive tool" "exploring" "learning" "author's" "cromulent" "bendix"
                  "immutable, persistent" "nascent computer algebra system" "widgets"
                  "largely written using LLMs"]]
      (is (re-find (re-pattern word) text) word)))
  (testing "and in a line under every page, with the way back"
    (is (re-find #"largely written using LLMs" (said lessons/colophon)))
    (is (= [[:lesson lessons/start "What this is"]] (lessons/widgets {:prose [lessons/colophon]})))))

(deftest every-lesson-names-what-it-runs
  (doseq [l lessons/all
          :let [{:keys [name says call] f :fn} (lessons/operation l)
                lines (lessons/call l {})
                where (:title l)]]
    (is (contains? lessons/operations (:operation l)) where)
    (is (every? #(and (string? %) (seq %)) [name f says]) where)
    (is (seq call) where)
    (is (<= (count call) (count lines)) where)
    (is (not-any? #(re-find #"opts" %) lines) (str where ": the options are written out"))
    (is (every? #(<= (count %) 36) lines) (str where ": a line fits the panel"))
    (testing "the fields are the call's arguments"
      (doseq [{:keys [arg label says] :as input} (:inputs l)]
        (is (every? #(and (string? %) (seq %)) [arg label says]) (str where " " (:key input)))
        (is (string? (lessons/input-says input :notation)) where)
        (is (some #(re-find (re-pattern (str "(^|[ (\\[])" arg "($|[ ),\\]])")) %) lines)
            (str where ": " arg " is an argument of " (pr-str lines)))))
    (is (or (empty? (:inputs l)) (apply distinct? (map :arg (:inputs l)))) where))
  (testing "the REPL's page has no fields: its editor is the input"
    (is (empty? (:inputs lessons/repl)))
    (is (= [lessons/repl] (remove (comp seq :inputs) lessons/all))))
  (testing "the options in force are the lesson's under the alternative's"
    (is (= ["(eg/add (eg/egraph) term)"
            "(rw/saturate g rules"
            "  {:scheduler :simple})"]
           (lessons/call lessons/basics {})))
    (is (= ["(eg/add (eg/egraph) term)"
            "(rw/saturate g rules"
            "  {:scheduler :backoff"
            "   :match-limit 4"
            "   :ban-length 2"
            "   :iter-limit 30})"]
           (lessons/call lessons/saturation {})))
    (is (= ["  {:scheduler :simple" "   :match-limit 4" "   :ban-length 2" "   :iter-limit 30" "   :node-limit 100})"]
           (subvec (lessons/call lessons/saturation {:scheduler :simple :node-limit 100}) 2)))
    (is (= ["(bx/simplify term"
            "  {:rules rules"
            "   :cost cost"
            "   :scheduler :simple"
            "   :iter-limit 12"
            "   :node-limit 5000})"]
           (lessons/call lessons/fix {})))
    (is (= ["(bx/simplify term" "  {:rules rules/trig" "   :cost cost" "   :scheduler :simple})"]
           (lessons/call lessons/polynomial-rule {})))
    (is (= ["(bx/differentiate term x" "  {:scheduler :simple})"]
           (lessons/call lessons/differentiation {})))
    (is (= ["(eg/add (eg/egraph) term)"] (lessons/call lessons/tree {})))
    (is (= ["(rw/saturate g rules" "  {})"]
           (rest (lessons/call (dissoc lessons/basics :opts) {})))
        "no options, an empty map")))

(deftest stepping-is-one-run
  (let [stepped (run/run-all (lessons/make-run lessons/blowup))
        g (peek (:timeline stepped))]
    (is (= (inc (:iterations stepped)) (count (:timeline stepped))))
    (is (= (count (:stats stepped)) (:iterations stepped)))
    (is (= (mapv inc (range (:iterations stepped))) (mapv :iter (:stats stepped))))
    (is (= [31 185] [(eg/class-count g) (eg/node-count g)]))
    (is (nil? (:engine stepped)) "the engine is dropped once the run is done")))

(deftest stopping-a-run
  (let [r (run/stop (run/step (lessons/make-run lessons/blowup)))]
    (is (= :stopped (:stop-reason r)))
    (is (= :done (:status r)))
    (is (= 1 (:iterations r)))
    (is (= r (run/step r)) "a stopped run is a fixed point")))
