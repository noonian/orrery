(ns orrery.views.lesson
  "The page for one lesson: navigation, prose, the inputs, and the
  panels the lesson asks for. This is the one view that reads the
  state value (through orrery.derived); the other views take values.
  Every handler is data over orrery.actions, so the page builds on
  the JVM and Jolt too, which orrery.page-test does for every lesson
  at every step."
  (:require [clojure.string :as str]
            [cromulent.core :as eg]
            [orrery.costs :as costs]
            [orrery.derived :as derived]
            [orrery.eclass :as eclass]
            [orrery.lessons :as lessons]
            [orrery.run :as run]
            [orrery.score :as score]
            [orrery.views.classes :as classes]
            [orrery.views.common :as common]
            [orrery.views.detail :as detail]
            [orrery.views.graph :as graph]
            [orrery.views.matches :as matches]
            [orrery.views.repl :as repl]
            [orrery.views.scrubber :as scrubber]
            [orrery.views.stats :as stats]
            [orrery.views.tree :as tree]))

(defn- work-link
  "A work of the reading list as a link to the paper, or as text when
  it has none."
  [k text]
  (if-let [url (:url (get lessons/reading k))]
    [:a {:href url :target "_blank" :rel "noreferrer"} text]
    [:span text]))

(defn- act
  "A link in the prose that does something to the page."
  [kind handler label]
  [:a.act {:data-act (name kind) :on {:click handler}} label])

(defn- prose
  "Lesson prose with its widgets resolved (orrery.lessons/widget-kinds):
  a term in either spelling, links that scrub to a step, open the
  class of a term (at a step, if one is named), set a cost, choose an
  alternative, switch the print mode or go to another lesson, and
  citations as superscript author-year links to the papers. A class
  link whose term the e-graph on show does not hold, because the
  input was edited, is plain text."
  [s x]
  (cond
    (and (vector? x) (contains? lessons/widget-kinds (first x)))
    (let [[kind a b c] x]
      (case kind
        :notation (common/term-view a :notation)
        :native (common/term-view a :native)
        :step (act :step [:step a] b)
        :select (if (some-> (if c (derived/egraph-at s c) (derived/current-egraph s)) (eclass/class-of a))
                  (act :select [:select-term a c] b)
                  b)
        :cost (act :cost [:cost a] b)
        :alternative (act :alternative [:alternative a] b)
        :print (act :print [:print a] b)
        :lesson [:a {:href (str "#" (lessons/address (lessons/by-key a)))} b]
        :cite (into [:sup.cite] (interpose ", " (for [k (rest x)] (work-link k (:short (get lessons/reading k))))))))
    (vector? x) (into [(first x)] (map #(prose s %) (rest x)))
    :else x))

(defn- about-view
  "What the site is, on the page it opens on: over the heading, so
  before any prose that links into the widgets."
  [s l]
  (when (= lessons/start (:key l))
    (into [:aside.about {:id "about"} [:span.label "what this is"]]
          (map #(prose s %) lessons/about))))

(defn- colophon-view
  "The site in a line, under every page."
  [s]
  [:footer.colophon {:id "colophon"} (prose s lessons/colophon)])

(defn- reading-view
  "The works the lesson's prose cites, with links: its footnotes."
  [l]
  (when-let [ks (seq (lessons/credits l))]
    (into [:div.reading [:span.label "reading"]]
          (interpose [:span.sep " · "]
                     (for [k ks :let [{:keys [who what where year]} (get lessons/reading k)]]
                       [:span.work {:replicant/key k} who ", " (work-link k [:i what]) " (" where ", " year ")"])))))

(defn- nav [current]
  (into [:ul.lesson-nav]
        (for [l lessons/all]
          [:li {:replicant/key (:key l)}
           (if (lessons/live? l)
             [:a {:href (str "#" (lessons/address l))
                  :class (when (= current (:key l)) "current")}
              (lessons/nav-label l)]
             [:span.coming (str (lessons/nav-label l) " · coming")])])))

(defn- operation-view
  "What the workbench runs, over the fields that are its arguments:
  the algorithm in a word and the function that is it, what it does,
  and the call under the runner options in force."
  [s l]
  (when-let [{:keys [name says] f :fn} (lessons/operation l)]
    [:div.operation {:id "operation"}
     (common/title name f)
     [:p.says says]
     [:pre.call {:id "call"} (str/join "\n" (lessons/call l (get-in s [:input :opts])))]]))

(defn- input-area [s l]
  (let [{:keys [fields error alternative drawn]} (:input s)
        drawing? (get-in s [:ui :drawing?])
        mode (get-in s [:ui :print])]
    [:div.panel.input-area
     (operation-view s l)
     (for [{:keys [key type arg] :as input} (:inputs l)]
       [:div.field {:replicant/key key}
        [:h3 [:code.arg arg] [:span.says (lessons/input-says input mode)]]
        [:textarea {:id (str "input-" (name key)) :rows (if (= :rules type) 4 2)
                    :value (get fields key "")
                    :on {:input [:field key]}}]])
     [:div.row
      [:button.primary {:id "run" :on {:click [:run]}} "run"]
      [:span.status {:id "input-help"}
       (if (= :notation mode)
         "notation as the page prints it: 2·x + y, x^2 or x², sin x, 1/2 exact, ?x in a pattern; native [:+ [:* 2 :x] :y] reads too"
         "native format: tagged vectors, keyword operators and variables; notation such as 2·x + y reads too")]]
     (when error [:div.error error])
     (when (seq (:alternatives l))
       [:div
        [:h3 {:style {:margin-top "12px"}} "try another"]
        (into [:div.alternatives]
              (for [alt (:alternatives l)]
                [:button {:replicant/key (:label alt)
                          :class (when (= alternative (:label alt)) "current")
                          :on {:click [:alternative (:label alt)]}}
                 (:label alt)]))])
     (when (:surprise l)
       [:div
        [:div.row
         [:button {:id "surprise" :disabled drawing? :on {:click [:surprise]}}
          (if drawing? "drawing…" "surprise me")]
         [:span.status "a dozen random terms, run and scored; one picked, weighted by score"]]
        (when drawn
          [:div.drawn {:id "drawn"} (score/explain drawn (get-in l [:surprise :wants]))])])]))

(defn- run-panel
  "The counters and how the run ended, under the function whose run
  it is; the transport is the replay bar over the class list."
  [s l r g]
  [:div.panel {:style {:margin-top "16px"}}
   (common/title "the run" (if (= :repl (:from r)) "from the REPL" (:fn (lessons/operation l))))
   (common/tiles {:classes (eg/class-count g) :nodes (eg/node-count g)
                  :step (:step s) :n (run/last-step r)
                  :status (:status r) :stop-reason (:stop-reason r) :ms (:ms r)})
   [:div {:id "run-status" :data-status (name (:status r))
          :data-stop-reason (some-> (:stop-reason r) name)
          :data-dirty (str (boolean (:dirty? g)))}]
   (when (:dirty? g)
     [:div.status [:span.badge.dirty "rebuild pending: the invariants are not restored yet"]])])

(defn- replay-bar
  "The transport and, under it, the opened class at the step on show,
  directly above the class list and stuck to the top of the viewport
  while the list scrolls under it, so the classes change in view as
  you scrub and the opened class never scrolls away."
  [s r g]
  [:div.replay {:id "replay"}
   (scrubber/scrubber {:step (:step s) :n (run/last-step r) :labels (:labels r)
                       :playing? (some? (get-in s [:ui :playing])) :status (:status r)
                       :summary (str (eg/class-count g) " classes · " (eg/node-count g) " nodes")})
   (when-let [d (derived/detail-at s (:step s))]
     (detail/detail-view d {:mode (get-in s [:ui :print])
                            :cost-label (costs/label (:cost s))
                            :labels (:labels r)
                            :root-id (derived/root-at s)}))])

(defn- tools
  "The row under the replay bar: the graph picture's switch, and the
  e-graph on show as egraph-serialize JSON, copied or downloaded."
  [s]
  (let [graph? (derived/graph? s)]
    [:div.tools {:id "tools"}
     [:button {:id "graph-toggle" :class (when graph? "current") :on {:click [:graph/toggle]}}
      (if graph? "hide the graph" "draw the graph")]
     [:span.spacer]
     [:span.status {:title "the e-graph on show in the egraph-serialize format, which egg's and egglog's tools read"}
      "this step as egraph-serialize JSON:"]
     [:button {:id "export-copy" :on {:click [:export/copy]}} "copy"]
     [:button {:id "export-download" :on {:click [:export/download]}} "download"]
     (when-let [m (get-in s [:ui :export-status])]
       [:span.status {:id "export-status"} m])]))

(defn- graph-panel [s matches]
  (when (derived/graph? s)
    (let [selected (get-in s [:ui :selected])]
      (graph/graph-view {:layout (derived/graph-at s)
                         :root (derived/root-at s)
                         :selected selected
                         :hovered (get-in s [:ui :hover])
                         :diff (derived/diff-at s)
                         :matches (derived/matched-classes matches)
                         :zoom (get-in s [:ui :graph-zoom])
                         :filter? (get-in s [:ui :graph-filter?])
                         :filterable? (some? selected)}))))

(defn- best-panel [s l]
  (let [best (derived/best-at s)
        root (derived/root-at s)
        {:keys [cost term]} (best root)
        picker (filter (comp (set (:costs l)) :key) costs/all)]
    [:div.panel.best {:style {:margin-top "16px"}}
     (common/title "best so far" "ex/extract")
     [:div.term {:id "best-notation"} (common/term-view term :notation)]
     [:div {:id "best-term"} (common/term-view term :native)]
     [:div.changed (str "cost " (costs/cost-str cost) " under " (costs/label (:cost s)))]
     [:p.says {:id "extract-says"}
      [:code "(ex/extract g root cost)"] ": the cheapest term of " [:code "root"] ", the input's class, in "
      [:code "g"] ", the e-graph at this step"]
     (when (> (count picker) 1)
       (into [:div.cost-picker [:code.arg "cost"]]
             (for [c picker]
               [:label {:replicant/key (:key c) :title (:blurb c)}
                [:input {:type "radio" :name "cost" :value (name (:key c)) :checked (= (:key c) (:cost s))
                         :on {:change [:cost (:key c)]}}]
                " " (:label c)])))]))

(defn- class-panel
  "The class list over the e-graph at step k, under what the REPL
  calls that e-graph; the opened class there marks its own row and
  its relatives' rows."
  [title of g s k opts]
  [:div.panel
   (common/title title of)
   (classes/class-list (merge {:g g :mode (get-in s [:ui :print])
                               :selected (get-in s [:ui :selected])
                               :hovered (get-in s [:ui :hover])
                               :detail (derived/detail-at s k)}
                              opts))])

(defn page [s]
  (let [l (derived/lesson s)
        r (:run s)
        g (derived/current-egraph s)
        mode (get-in s [:ui :print])
        panels (or (:panels l) #{})
        matches (derived/matches-at s)]
    [:main.page
     [:header.masthead
      [:h1 "orrery"]
      [:span.tagline "an e-graph explorer, for understanding"]
      [:span.spacer]
      (common/print-toggle mode)]
     (nav (:key l))
     (when l
       [:section.lesson
        (about-view s l)
        [:h2 (lessons/heading l)]
        (into [:div.prose] (map #(prose s %) (:prose l)))
        (reading-view l)
        (when (and r g)
          [:div.workbench
           [:div
            (input-area s l)
            (run-panel s l r g)
            (when (derived/root-at s) (best-panel s l))
            (when (contains? panels :matches)
              [:div {:style {:margin-top "16px"}}
               (matches/matches-panel {:matches matches})])
            (when (contains? panels :stats)
              [:div.panel {:style {:margin-top "16px"}}
               (common/title "the iterations" ":stats")
               (stats/stats-table {:stats (:stats r) :rules (:rules r) :step (:step s)})])]
           [:div
            (when (contains? panels :tree)
              [:div.panel {:style {:margin-bottom "16px"}}
               (common/title "the tree" "term")
               (tree/tree-view {:g g :term (get-in s [:input :values :term])
                                :hovered (or (get-in s [:ui :hover]) (get-in s [:ui :selected]))})])
            (replay-bar s r g)
            (tools s)
            (graph-panel s matches)
            (if (contains? panels :fork)
              [:div.fork
               (class-panel "the original, step 0" "(first timeline)" (derived/egraph-at s 0) s 0 {:root (:root r)})
               (class-panel (str "this step: " (nth (:labels r) (:step s) "")) "g at this step" g s (:step s)
                            {:diff (derived/diff-at s) :best (derived/best-at s) :root (derived/root-at s)})]
              (class-panel "the classes" "g at this step" g s (:step s)
                           {:diff (derived/diff-at s) :best (derived/best-at s) :root (derived/root-at s)
                            :matches (derived/matched-classes matches)}))]])
        (when r
          [:div {:style {:margin-top "16px"}}
           (repl/repl-panel {:input (get-in s [:repl :input])
                             :history (get-in s [:repl :history])
                             :mode mode})])])
     (colophon-view s)]))
