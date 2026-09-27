(ns orrery.workbench-test
  "The page's state as a value: the constructor, the steps, the values
  of the REPL made runs, and what the atom refuses."
  (:require [clojure.test :refer [deftest is testing]]
            [cromulent.core :as eg]
            [cromulent.rewrite :as rw]
            [orrery.lessons :as lessons]
            [orrery.run :as run]
            [orrery.workbench :as wb]))

(deftest the-page-before-it-opens-anything
  (let [s (wb/initial)]
    (is (nil? (wb/problem s)))
    (is (= lessons/start (:lesson s)))
    (is (nil? (:run s)))
    (is (= [0 0] [(:step s) (:run-id s)]))
    (is (= :notation (wb/print-mode s)))
    (is (nil? (get-in s [:ui :graph?])) "the lesson decides until the switch is touched")))

(deftest a-page-for-every-lesson
  (doseq [l lessons/all
          :let [s (wb/page l)
                done (wb/page l (run/run-all (:run s)))]]
    (is (nil? (wb/problem s)) (:title l))
    (is (nil? (wb/problem done)) (:title l))
    (is (= (:key l) (:lesson s)))
    (is (= 1 (:run-id s)) "one run put on show")
    (is (= (:values l) (get-in s [:input :values])))
    (is (= (set (map :key (:inputs l))) (set (keys (get-in s [:input :fields])))) "a field per input")
    (is (= (or (first (:costs l)) :ast-size) (:cost s)))
    (is (= (if (= :running (:status (:run s))) 0 (run/last-step (:run s))) (:step s))
        "a run still running is on show at its input, a finished one at its end")
    (is (= (run/last-step (:run done)) (:step done)))
    (is (= (min 2 (run/last-step (:run done))) (:step (wb/page l (:run done) 2))) (:title l))))

(deftest the-repl-page-opens-the-dock
  (let [s (wb/initial)]
    (is (false? (get-in s [:ui :repl-open?])))
    (is (false? (get-in (wb/open s lessons/taste) [:ui :repl-open?])) "a lesson leaves the dock as it is")
    (is (true? (get-in (wb/open s lessons/repl) [:ui :repl-open?])))
    (is (true? (get-in (-> s (wb/open lessons/repl) (wb/open lessons/taste)) [:ui :repl-open?]))
        "and the dock stays open after it")))

(deftest a-lesson-opens-in-the-print-mode-in-force
  (let [native (-> (wb/initial) (assoc-in [:ui :print] :native) (wb/open lessons/rule))
        notation (wb/open (wb/initial) lessons/rule)]
    (is (= "[:+ [:* :a 2] [:* :b 2]]" (get-in native [:input :fields :term])))
    (is (= "a·2 + b·2" (get-in notation [:input :fields :term])))
    (is (= "mul-2-to-shift: ?x·2 → ?x << 1" (get-in notation [:input :fields :rules])))
    (is (= :native (wb/print-mode native)) "opening a lesson leaves the mode alone")
    (is (nil? (:run notation)) "and makes no run: that is start's")))

(deftest a-run-on-show
  (let [s (wb/page lessons/blowup)
        done (run/run-all (:run s))
        shown (-> s (assoc-in [:ui :selected] 3) (wb/show done))]
    (is (= :running (:status (:run s))))
    (is (= 2 (:run-id shown)) "every run put on show is another")
    (is (= [7 true] [(:step shown) (:follow? shown)]))
    (is (nil? (get-in shown [:ui :selected])) "nothing of the last run stays open")
    (testing "stepping, the scrub position following until it is moved"
      (let [one (wb/advance s (run/step (:run s)))
            held (wb/advance (wb/scrub one 0) (run/step (:run one)))]
        (is (= [1 true] [(:step one) (:follow? one)]))
        (is (= [0 false] [(:step held) (:follow? held)]))
        (is (= 3 (count (:timeline (:run held)))))
        (is (= 1 (:run-id one)) "the same run")))
    (testing "scrubbing is clamped to the timeline"
      (is (= [0 false] ((juxt :step :follow?) (wb/scrub shown -3))))
      (is (= [7 true] ((juxt :step :follow?) (wb/scrub shown 99))))
      (is (= [4 false] ((juxt :step :follow?) (wb/scrub shown 4)))))))

(deftest a-value-of-the-repl-as-a-run
  (let [[g id] (eg/add (eg/egraph) [:+ :a :b])
        rules (lessons/rules-of lessons/ac-rules)
        kept (rw/embiggen g rules {:scheduler :simple :timeline? true})
        bare (rw/embiggen g rules {:scheduler :simple})
        unrun (run/start [:+ :a :b] rules {})]
    (testing "an e-graph, a pair, a runner's result, a run"
      (is (= {:timeline [g] :labels ["from the REPL"] :root nil :from :repl}
             (select-keys (wb/run-of g) [:timeline :labels :root :from])))
      (is (= id (:root (wb/run-of [g id]))))
      (is (= [3 ["the input" "iteration 1" "iteration 2"] :saturated 2]
             ((juxt (comp count :timeline) :labels :stop-reason :iterations) (wb/run-of kept))))
      (is (= (:stats kept) (:stats (wb/run-of kept))))
      (is (= [(:egraph bare)] (:timeline (wb/run-of bare))) "without a timeline, its last e-graph")
      (is (= (assoc unrun :from :repl) (wb/run-of unrun)) "a run as it is, to be stepped")
      (is (= id (:root (wb/run-of (assoc bare :root id)))) "bendix's results name their root"))
    (testing "every one is a run, and nothing else is"
      (is (every? (comp wb/run? wb/run-of) [g [g id] kept bare unrun]))
      (is (every? (comp nil? wb/run-of) [nil 42 "g" [] [g] [g :a] {} {:timeline []} {:egraph g}])))
    (testing "put: on show, or the page as it was"
      (let [s (wb/page lessons/tree)]
        (is (= s (wb/put s 42)))
        (is (= [2 0 :repl] ((juxt :run-id :step (comp :from :run)) (wb/put s [g id]))))
        (is (nil? (wb/problem (wb/put s kept))))))))

(deftest a-run-made-by-hand
  (let [[g a] (eg/add (eg/egraph) [:+ :a :a])
        [g' b] (eg/add g [:+ :b :b])
        [u _] (eg/union g' (second (eg/add g' :a)) (second (eg/add g' :b)))
        s (-> (wb/page lessons/repl) (wb/put [g a]))
        pushed (-> s (wb/push g' "b + b, added") (wb/push u "a = b") (wb/push (eg/rebuild u) "rebuilt"))]
    (is (nil? (wb/problem pushed)))
    (is (= ["from the REPL" "b + b, added" "a = b" "rebuilt"] (:labels (:run pushed))))
    (is (= [2 4 3 2] (mapv eg/class-count (:timeline (:run pushed)))))
    (is (= [3 true] [(:step pushed) (:follow? pushed)]) "the scrub position on the step pushed")
    (is (= (:run-id s) (:run-id pushed)) "the same run, a step longer")
    (is (= a (:root (:run pushed))))
    (is (= "from the REPL" (peek (:labels (:run (wb/push s g'))))) "a label of its own, unless one is given")
    (testing "an e-graph that cannot follow the last is put on show alone"
      (let [alone (wb/push pushed g)]
        (is (= [g] (:timeline (:run alone))))
        (is (= (inc (:run-id pushed)) (:run-id alone)))))
    (testing "a run still running is stopped first"
      (let [running (wb/page lessons/blowup)
            p (wb/push running (eg/rebuild (first (eg/add (peek (:timeline (:run running))) :z))) "z")]
        (is (= [:done :stopped] ((juxt :status :stop-reason) (:run p))))
        (is (= ["the input" "z"] (:labels (:run p))))
        (is (nil? (wb/problem p)))))
    (testing "what is not an e-graph changes nothing"
      (is (= s (wb/push s 42))))))

(deftest the-editor-recalls
  (let [r {:input "" :history [{:in "(a)"} {:in "(b)"} {:in "(c)"}] :recall nil}
        back #(wb/recall % :back)
        forward #(wb/recall % :forward)]
    (is (= ["(c)" 2] ((juxt :input :recall) (back r))))
    (is (= ["(b)" 1] ((juxt :input :recall) (back (back r)))))
    (is (= ["(a)" 0] ((juxt :input :recall) (back (back (back (back r)))))) "and stops at the first")
    (is (= ["(c)" 2] ((juxt :input :recall) (forward (back (back r))))))
    (is (= r (forward r)) "nothing later than what is being typed")
    (testing "what was being typed comes back past the last entry"
      (let [typing (assoc r :input "(eg/ad")]
        (is (= ["(eg/ad" nil] ((juxt :input :recall) (forward (back typing)))))
        (is (= ["(eg/ad" nil] ((juxt :input :recall) (forward (forward (back (back typing)))))))))
    (is (= {:input "x" :history [] :recall nil} (back {:input "x" :history [] :recall nil})) "no history, nothing to recall")))

(deftest what-the-atom-refuses
  (let [s (wb/page lessons/taste (run/run-all (lessons/make-run lessons/taste)))
        [g _] (eg/add (eg/egraph) :z)
        says (fn [s re] (let [p (wb/problem s)] (boolean (and p (re-find re p)))))]
    (is (nil? (wb/problem s)))
    (is (says nil #"is a map"))
    (is (says 42 #"is a map"))
    (is (says (assoc s :lesson :nowhere) #":lesson names no page: :nowhere"))
    (is (says (assoc s :run-id nil) #":run-id"))
    (is (says (assoc s :run {:timeline []}) #":run is a run"))
    (is (says (assoc s :run g) #":run is a run") "an e-graph is not one: wb/run-of makes it one")
    (is (says (update-in s [:run :labels] pop) #":run is a run") "a label for every e-graph")
    (is (says (update-in s [:run :timeline] conj g) #":run is a run"))
    (is (says (-> s (update-in [:run :timeline] conj g) (update-in [:run :labels] conj "z")) #"ids only grow"))
    (is (says (assoc s :step 99) #":step is a step of the run, 0 to 3: 99"))
    (is (says (assoc s :step -1) #":step"))
    (is (says (assoc s :step nil) #":step"))
    (is (says (assoc s :cost :cheapest) #":cost names no cost: :cheapest"))
    (is (says (assoc-in s [:ui :print] :latex) #":notation or :native"))
    (is (says (assoc-in s [:ui :selected] :a) #"class id"))
    (is (says (assoc-in s [:repl :input] nil) #"editor's text"))
    (is (says (assoc-in s [:repl :history] ()) #"vector"))
    (is (says (assoc s :input nil) #":input"))
    (testing "what the page's own steps make, it accepts"
      (is (nil? (wb/problem (assoc s :run nil :step 0))))
      (is (nil? (wb/problem (assoc s :cost :no-D))))
      (is (nil? (wb/problem (assoc-in s [:ui :selected] 2)))))))
