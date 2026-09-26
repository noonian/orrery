(ns orrery.views.lesson
  "The page for one lesson: navigation, prose, the inputs, and the
  panels the lesson asks for. This is the one namespace that reads
  the state; the other views take values."
  (:require [cromulent.core :as eg]
            [orrery.costs :as costs]
            [orrery.lessons :as lessons]
            [orrery.run :as run]
            [orrery.state :as state]
            [orrery.views.classes :as classes]
            [orrery.views.common :as common]
            [orrery.views.matches :as matches]
            [orrery.views.repl :as repl]
            [orrery.views.scrubber :as scrubber]
            [orrery.views.stats :as stats]
            [orrery.views.tree :as tree]))

(defn- prose
  "Lesson prose with its widgets resolved: [:notation t], [:native t],
  [:step k label]."
  [x]
  (cond
    (and (vector? x) (= :notation (first x))) (common/term-view (second x) :notation)
    (and (vector? x) (= :native (first x))) (common/term-view (second x) :native)
    (and (vector? x) (= :step (first x)))
    (let [[_ k label] x] [:a.step {:on {:click #(state/set-step! k)}} label])
    (vector? x) (into [(first x)] (map prose (rest x)))
    :else x))

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
  (let [{:keys [fields error alternative]} (:input s)]
    [:div.panel.input-area
     (for [{:keys [key label type]} (:inputs l)]
       [:div.field {:replicant/key key}
        [:h3 label]
        [:textarea {:id (str "input-" (name key)) :rows (if (= :rules type) 4 2)
                    :value (get fields key "")
                    :on {:input (fn [e] (state/set-field! key (.. e -target -value)))}}]])
     [:div.row
      [:button.primary {:id "run" :on {:click #(state/submit-input!)}} "run"]
      [:span.status "native format: tagged vectors, keyword operators and variables"]]
     (when error [:div.error error])
     (when (seq (:alternatives l))
       [:div
        [:h3 {:style {:margin-top "12px"}} "try another"]
        (into [:div.alternatives]
              (for [alt (:alternatives l)]
                [:button {:replicant/key (:label alt)
                          :class (when (= alternative (:label alt)) "current")
                          :on {:click #(state/choose-alternative! alt)}}
                 (:label alt)]))])]))

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

(defn- class-panel [title g s opts]
  [:div.panel
   [:h3 title]
   (classes/class-list (merge {:g g :mode (get-in s [:ui :print])
                               :selected (get-in s [:ui :selected])
                               :hovered (get-in s [:ui :hover])
                               :on-select state/select-class!}
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
        (into [:div.prose] (map prose (:prose l)))
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
                                :hovered (get-in s [:ui :hover]) :on-hover state/hover!})])
            (if (contains? panels :fork)
              [:div.fork
               (class-panel "the original, step 0" (state/egraph-at s 0) s {:root (:root r)})
               (class-panel (str "this step: " (nth (:labels r) (:step s) "")) g s
                            {:diff (state/diff-at s) :best (state/best-at s) :root (state/root-at s)})]
              (class-panel "the classes" g s
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
