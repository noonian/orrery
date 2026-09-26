(ns orrery.views.lesson
  "The page for one lesson: navigation, prose, the inputs, and the
  panels the lesson asks for. This is the one namespace that reads
  the state; the other views take values."
  (:require [cromulent.core :as eg]
            [orrery.costs :as costs]
            [orrery.eclass :as eclass]
            [orrery.lessons :as lessons]
            [orrery.run :as run]
            [orrery.score :as score]
            [orrery.state :as state]
            [orrery.views.classes :as classes]
            [orrery.views.common :as common]
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
  [kind f label]
  [:a.act {:data-act (name kind) :on {:click f}} label])

(defn- prose
  "Lesson prose with its widgets resolved (orrery.lessons/widget-kinds):
  a term in either spelling, links that scrub to a step, open the
  class of a term (at a step, if one is named), set a cost, choose an
  alternative or switch the print mode, and citations as superscript
  author-year links to the papers. A class link whose term the
  e-graph on show does not hold, because the input was edited, is
  plain text."
  [s x]
  (cond
    (and (vector? x) (contains? lessons/widget-kinds (first x)))
    (let [[kind a b c] x]
      (case kind
        :notation (common/term-view a :notation)
        :native (common/term-view a :native)
        :step (act :step #(state/set-step! a) b)
        :select (if (some-> (if c (state/egraph-at s c) (state/current-egraph s)) (eclass/class-of a))
                  (act :select #(state/select-term! a c) b)
                  b)
        :cost (act :cost #(state/set-cost! a) b)
        :alternative (act :alternative #(state/choose-alternative-by-label! a) b)
        :print (act :print #(state/set-print! a) b)
        :cite (into [:sup.cite] (interpose ", " (for [k (rest x)] (work-link k (:short (get lessons/reading k))))))))
    (vector? x) (into [(first x)] (map #(prose s %) (rest x)))
    :else x))

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
             [:a {:href (str "#" (:n l))
                  :class (when (= current (:key l)) "current")}
              (str (:n l) ". " (:title l))]
             [:span.coming (str (:n l) ". " (:title l) " · coming")])])))

(defn- input-area [s l]
  (let [{:keys [fields error alternative drawn]} (:input s)
        drawing? (get-in s [:ui :drawing?])
        mode (get-in s [:ui :print])]
    [:div.panel.input-area
     (for [{:keys [key type] :as input} (:inputs l)]
       [:div.field {:replicant/key key}
        [:h3 (lessons/input-label input mode)]
        [:textarea {:id (str "input-" (name key)) :rows (if (= :rules type) 4 2)
                    :value (get fields key "")
                    :on {:input (fn [e] (state/set-field! key (.. e -target -value)))}}]])
     [:div.row
      [:button.primary {:id "run" :on {:click #(state/submit-input!)}} "run"]
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
                          :on {:click #(state/choose-alternative! alt)}}
                 (:label alt)]))])
     (when (:surprise l)
       [:div
        [:div.row
         [:button {:id "surprise" :disabled drawing? :on {:click #(state/surprise!)}}
          (if drawing? "drawing…" "surprise me")]
         [:span.status "a dozen random terms, run and scored; one picked, weighted by score"]]
        (when drawn
          [:div.drawn {:id "drawn"} (score/explain drawn (get-in l [:surprise :wants]))])])]))

(defn- run-panel [s r g]
  [:div.panel {:style {:margin-top "16px"}}
   [:h3 "the run"]
   (common/tiles {:classes (eg/class-count g) :nodes (eg/node-count g)
                  :step (:step s) :n (run/last-step r)
                  :status (:status r) :stop-reason (:stop-reason r) :ms (:ms r)})
   [:div {:id "run-status" :data-status (name (:status r))
          :data-stop-reason (some-> (:stop-reason r) name)
          :data-dirty (str (boolean (:dirty? g)))}]
   (when (:dirty? g)
     [:div.status [:span.badge.dirty "rebuild pending: the invariants are not restored yet"]])
   (scrubber/scrubber {:step (:step s) :n (run/last-step r) :labels (:labels r)
                       :playing? (some? (get-in s [:ui :playing])) :status (:status r)
                       :on-step state/set-step! :on-play state/play!
                       :on-pause state/pause! :on-stop state/stop!})])

(defn- best-panel [s l]
  (let [best (state/best-at s)
        root (state/root-at s)
        {:keys [cost term]} (best root)
        picker (filter (comp (set (:costs l)) :key) costs/all)]
    [:div.panel.best {:style {:margin-top "16px"}}
     [:h3 "best so far"]
     [:div.term {:id "best-notation"} (common/term-view term :notation)]
     [:div {:id "best-term"} (common/term-view term :native)]
     [:div.changed (str "cost " (costs/cost-str cost) " under " (costs/label (:cost s)))]
     (when (> (count picker) 1)
       (into [:div.cost-picker]
             (for [c picker]
               [:label {:replicant/key (:key c) :title (:blurb c)}
                [:input {:type "radio" :name "cost" :value (name (:key c)) :checked (= (:key c) (:cost s))
                         :on {:change #(state/set-cost! (:key c))}}]
                " " (:label c)])))]))

(defn- class-panel
  "The class list over the e-graph at step k, with the opened class."
  [title g s k opts]
  [:div.panel
   [:h3 title]
   (classes/class-list (merge {:g g :mode (get-in s [:ui :print])
                               :selected (get-in s [:ui :selected])
                               :hovered (get-in s [:ui :hover])
                               :detail (state/detail-at s k)
                               :labels (:labels (:run s))
                               :cost-label (costs/label (:cost s))
                               :on-select state/select-class!
                               :on-step state/set-step!
                               :on-close state/deselect!}
                              opts))])

(defn page [s]
  (let [l (state/lesson s)
        r (:run s)
        g (state/current-egraph s)
        mode (get-in s [:ui :print])
        panels (or (:panels l) #{})
        matches (state/matches-at s)]
    [:main.page
     [:header.masthead
      [:h1 "orrery"]
      [:span.tagline "an e-graph explorer, for understanding"]
      [:span.spacer]
      (common/print-toggle mode state/set-print!)]
     (nav (:key l))
     (when l
       [:section.lesson
        [:h2 (str (:n l) ". " (:title l))]
        (into [:div.prose] (map #(prose s %) (:prose l)))
        (reading-view l)
        (when (and r g)
          [:div.workbench
           [:div
            (input-area s l)
            (run-panel s r g)
            (when (state/root-at s) (best-panel s l))
            (when (contains? panels :matches)
              [:div {:style {:margin-top "16px"}}
               (matches/matches-panel {:matches matches :on-select state/select-class!})])
            (when (contains? panels :stats)
              [:div.panel {:style {:margin-top "16px"}}
               [:h3 "the iterations"]
               (stats/stats-table {:stats (:stats r) :rules (:rules r) :step (:step s) :on-step state/set-step!})])]
           [:div
            (when (contains? panels :tree)
              [:div.panel {:style {:margin-bottom "16px"}}
               [:h3 "the tree"]
               (tree/tree-view {:g g :term (get-in s [:input :values :term])
                                :hovered (or (get-in s [:ui :hover]) (get-in s [:ui :selected]))
                                :on-hover state/hover! :on-select state/select-class!})])
            (if (contains? panels :fork)
              [:div.fork
               (class-panel "the original, step 0" (state/egraph-at s 0) s 0 {:root (:root r)})
               (class-panel (str "this step: " (nth (:labels r) (:step s) "")) g s (:step s)
                            {:diff (state/diff-at s) :best (state/best-at s) :root (state/root-at s)})]
              (class-panel "the classes" g s (:step s)
                           {:diff (state/diff-at s) :best (state/best-at s) :root (state/root-at s)
                            :matches (state/matched-classes matches)}))]])
        (when r
          [:div {:style {:margin-top "16px"}}
           (repl/repl-panel {:input (get-in s [:repl :input])
                             :history (get-in s [:repl :history])
                             :mode mode
                             :on-input state/set-repl-input!
                             :on-eval state/eval-repl!
                             :on-clear state/clear-repl!
                             :on-adopt state/adopt!
                             :on-select state/select-class!})])])]))
