(ns orrery.views.lesson
  "Renders the page for one lesson: the navigation, the prose, the
  inputs, and the panels that the lesson asks for. This is the one
  view that reads the state value, which it does through
  `orrery.derived`. The other views take values.

  Every handler is data: an `[action & args]` vector over the
  actions in `orrery.actions`. So the page builds on the JVM and on
  Jolt too. `orrery.page-test` builds it there for every lesson at
  every step."
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
  "Renders a work of the reading list as a link to the paper, or as
  text when the work has no link. `k` is the key of the work and
  `text` is the text to show."
  [k text]
  (if-let [url (:url (get lessons/reading k))]
    [:a {:href url :target "_blank" :rel "noreferrer"} text]
    [:span text]))

(defn- act
  "Renders a link in the prose that does something to the page."
  [kind handler label]
  [:a.act {:data-act (name kind) :on {:click handler}} label])

(defn- prose
  "Renders lesson prose with its widgets resolved
  (`orrery.lessons/widget-kinds`). The widgets are:

  - a term in either spelling;
  - links that scrub to a step, open the class of a term (at a step,
    if one is named), set a cost, choose an alternative, switch the
    print mode, go to another lesson, or evaluate a line at the
    REPL;
  - citations, as superscript author-year links to the papers.

  A link to a class renders as plain text when the e-graph it would
  open the class in does not hold the term of the link. That e-graph
  is the one at the step the link names, or the one on show when
  the link names no step. This happens when the input was edited."
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
        :eval (act :eval [:repl/run a] [:code {:class (when (str/includes? a "\n") "block")} a])
        :cite (into [:sup.cite] (interpose ", " (for [k (rest x)] (work-link k (:short (get lessons/reading k))))))))
    (vector? x) (into [(first x)] (map #(prose s %) (rest x)))
    :else x))

(defn- about-view
  "Renders what the site is, but only on the page that the site
  opens on. It goes over the heading, so it comes before any prose
  that links into the widgets."
  [s l]
  (when (= lessons/start (:key l))
    (into [:aside.about {:id "about"} [:span.label "what this is"]]
          (map #(prose s %) lessons/about))))

(defn- colophon-view
  "Renders the one-line description of the site that goes under
  every page."
  [s]
  [:footer.colophon {:id "colophon"} (prose s lessons/colophon)])

(defn- reading-view
  "Renders the works that the prose of the lesson cites, with links.
  These are the footnotes of the lesson."
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
  "Renders what the workbench runs, over the fields that are its
  arguments. Shows the algorithm in a word, the function that
  implements it, what it does, and the call with the runner options
  in force."
  [s l]
  (when-let [{:keys [name says] f :fn} (lessons/operation l)]
    [:div.operation {:id "operation"}
     (common/title name f)
     [:p.says says]
     [:pre.call {:id "call"} (str/join "\n" (lessons/call l (get-in s [:input :opts])))]]))

(defn- input-area
  "Renders the operation and its arguments, for a page that has
  arguments to edit."
  [s l]
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
         "Type notation as the page prints it: 2·x + y, x^2 or x², sin x, 1/2 for an exact half, ?x in a pattern. The native format, [:+ [:* 2 :x] :y], is read too."
         "Type the native format: tagged vectors with keyword operators and variables. Notation such as 2·x + y is read too.")]]
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
         [:span.status "Draws a dozen random terms, runs and scores them, and picks one, weighted by its score."]]
        (when drawn
          [:div.drawn {:id "drawn"} (score/explain drawn (get-in l [:surprise :wants]))])])]))

(defn- run-panel
  "Renders the counters and how the run ended. The heading names the
  function that made the run, or says \"from the REPL\" when the
  REPL put the run on show. The transport is not in this panel. It
  is in the replay bar, over the class list."
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
  "Renders the replay bar: the transport and, under it, the opened
  class at the step on show. The bar sits directly above the class
  list and sticks to the top of the viewport while the list scrolls
  under it. So the classes change in view as you scrub, and the
  opened class never scrolls away."
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
  "Renders the row under the replay bar. The row holds the switch
  for the graph picture, and the buttons that copy or download the
  e-graph on show as egraph-serialize JSON."
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
      [:code "(ex/extract g root cost)"] " returns the cheapest term of " [:code "root"] " in " [:code "g"] ". "
      [:code "root"] " is the class of the input, and " [:code "g"] " is the e-graph at this step."]
     (when (> (count picker) 1)
       (into [:div.cost-picker [:code.arg "cost"]]
             (for [c picker]
               [:label {:replicant/key (:key c) :title (:blurb c)}
                [:input {:type "radio" :name "cost" :value (name (:key c)) :checked (= (:key c) (:cost s))
                         :on {:change [:cost (:key c)]}}]
                " " (:label c)])))]))

(defn- class-panel
  "Renders the class list over `g`, the e-graph at step `k`. The
  heading is `title`, with `of` beside it, which is what the REPL
  calls that e-graph. The class that is open at that step marks its
  own row and the rows of its relatives."
  [title of g s k opts]
  [:div.panel
   (common/title title of)
   (classes/class-list (merge {:g g :mode (get-in s [:ui :print])
                               :selected (get-in s [:ui :selected])
                               :hovered (get-in s [:ui :hover])
                               :detail (derived/detail-at s k)}
                              opts))])

(defn- matches-view [matches]
  [:div {:style {:margin-top "16px"}}
   (matches/matches-panel {:matches matches})])

(defn- stats-panel [s r]
  [:div.panel {:style {:margin-top "16px"}}
   (common/title "the iterations" ":stats")
   (stats/stats-table {:stats (:stats r) :rules (:rules r) :step (:step s)})])

(defn- tree-panel
  "Renders the term as a tree over the e-graph on show."
  [s g term style]
  [:div.panel {:style style}
   (common/title "the tree" "term")
   (tree/tree-view {:g g :term term
                    :hovered (or (get-in s [:ui :hover]) (get-in s [:ui :selected]))})])

(defn- classes-view [s r g matches]
  (class-panel "the classes" "g at this step" g s (:step s)
               {:diff (derived/diff-at s) :best (derived/best-at s) :root (derived/root-at s)
                :matches (derived/matched-classes matches)}))

(defn- repl-view [s beside?]
  (repl/repl-panel {:input (get-in s [:repl :input])
                    :history (get-in s [:repl :history])
                    :mode (get-in s [:ui :print])
                    :line-numbers? (get-in s [:ui :line-numbers?])
                    :beside? beside?}))

(defn- workbench
  "Renders the panels of a lesson. The left column holds what the
  operation is given and what it made. The right column holds the
  e-graph."
  [s l r g panels matches]
  [:div.workbench
   [:div
    (when (seq (:inputs l)) (input-area s l))
    (run-panel s l r g)
    (when (derived/root-at s) (best-panel s l))
    (when (contains? panels :matches) (matches-view matches))
    (when (contains? panels :stats) (stats-panel s r))]
   [:div
    (when (contains? panels :tree)
      (tree-panel s g (get-in s [:input :values :term]) {:margin-bottom "16px"}))
    (replay-bar s r g)
    (tools s)
    (graph-panel s matches)
    (if (contains? panels :fork)
      [:div.fork
       (class-panel "the original, step 0" "(first timeline)" (derived/egraph-at s 0) s 0 {:root (:root r)})
       (class-panel (str "this step: " (nth (:labels r) (:step s) "")) "g at this step" g s (:step s)
                    {:diff (derived/diff-at s) :best (derived/best-at s) :root (derived/root-at s)})]
      (classes-view s r g matches))]])

(defn- beside
  "Renders the panels of the REPL's page. The editor is on the left
  and stays in view. On the right is every panel that reads the run
  on show, whatever put the run there.

  A panel with nothing to read is left out. The best term and the
  matches need a run that knows its root and its rules. The tree
  needs a run that knows the term it started from."
  [s l r g panels matches]
  [:div.workbench.beside
   [:div.repl-column (repl-view s true)]
   [:div
    (replay-bar s r g)
    (tools s)
    (graph-panel s matches)
    (classes-view s r g matches)
    (run-panel s l r g)
    (when (derived/root-at s) (best-panel s l))
    (when (and (contains? panels :matches) matches) (matches-view matches))
    (when (and (contains? panels :stats) (seq (:stats r))) (stats-panel s r))
    (when (and (contains? panels :tree) (:term r))
      (tree-panel s g (:term r) {:margin-top "16px"}))]])

(defn page [s]
  (let [l (derived/lesson s)
        r (:run s)
        g (derived/current-egraph s)
        mode (get-in s [:ui :print])
        panels (or (:panels l) #{})
        matches (derived/matches-at s)
        beside? (= :beside (:layout l))]
    [:main.page {:class (when beside? "wide")}
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
        (if beside?
          [:div.docs
           [:div
            (into [:div.prose] (map #(prose s %) (:prose l)))
            (reading-view l)]
           (repl/names-view)]
          (list (into [:div.prose] (map #(prose s %) (:prose l)))
                (reading-view l)))
        (when (and r g)
          (if beside?
            (beside s l r g panels matches)
            (workbench s l r g panels matches)))
        (when (and r (not beside?))
          [:div {:style {:margin-top "16px"}}
           (repl-view s false)])])
     (colophon-view s)]))
