(ns orrery.views.lesson
  "The page for one lesson: navigation, prose, the input, and the
  panels. This is the one namespace that reads the state; the other
  views take values."
  (:require [cromulent.core :as eg]
            [orrery.costs :as costs]
            [orrery.lessons :as lessons]
            [orrery.run :as run]
            [orrery.state :as state]
            [orrery.views.classes :as classes]
            [orrery.views.common :as common]
            [orrery.views.scrubber :as scrubber]))

(defn- prose
  "Lesson prose with its widgets resolved: [:lay t], [:native t],
  [:step k label]."
  [x mode]
  (cond
    (and (vector? x) (= :lay (first x))) (common/term-view (second x) :lay)
    (and (vector? x) (= :native (first x))) (common/term-view (second x) :native)
    (and (vector? x) (= :step (first x)))
    (let [[_ k label] x] [:a.step {:on {:click #(state/set-step! k)}} label])
    (vector? x) (into [(first x)] (map #(prose % mode) (rest x)))
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
  (let [{:keys [text error alternative]} (:input s)]
    [:div.panel.input-area
     [:h3 "the term"]
     [:textarea {:id "term-input" :rows 2 :value text
                 :on {:input (fn [e] (state/set-input-text! (.. e -target -value)))}}]
     [:div.row
      [:button.primary {:id "run" :on {:click #(state/submit-input!)}} "run"]
      [:span.status "native format: a tagged vector, keyword operators and variables"]]
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

(defn- best-panel [s l g]
  (let [best (state/best-at s)
        root (state/root-at s)
        {:keys [cost term]} (best root)
        mode (get-in s [:ui :print])
        picker (filter (comp (set (:costs l)) :key) costs/all)]
    [:div.panel.best
     [:h3 "best so far"]
     [:div.term {:id "best-lay"} (common/term-view term :lay)]
     [:div {:id "best-term"} (common/term-view term :native)]
     [:div.changed (str "cost " (pr-str cost) " under " (:label (first (filter #(= (:key %) (:cost s)) costs/all))))]
     (when (> (count picker) 1)
       (into [:div.cost-picker]
             (for [c picker]
               [:label {:replicant/key (:key c)}
                [:input {:type "radio" :name "cost" :checked (= (:key c) (:cost s))
                         :on {:change #(state/set-cost! (:key c))}}]
                " " (:label c)])))]))

(defn page [s]
  (let [l (state/lesson s)
        r (:run s)
        g (state/current-egraph s)
        mode (get-in s [:ui :print])]
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
        (into [:div.prose] (map #(prose % mode) (:prose l)))
        (when (and r g)
          [:div.workbench
           [:div
            (input-area s l)
            [:div.panel {:style {:margin-top "16px"}}
             [:h3 "the run"]
             (common/tiles {:classes (eg/class-count g) :nodes (eg/node-count g)
                            :step (:step s) :iterations (:iterations r)
                            :status (:status r) :stop-reason (:stop-reason r) :ms (:ms r)})
             [:div {:id "run-status" :data-status (name (:status r))
                    :data-stop-reason (some-> (:stop-reason r) name)}]
             (scrubber/scrubber {:step (:step s) :n (run/last-step r) :labels (:labels r)
                                 :playing? (some? (get-in s [:ui :playing])) :status (:status r)
                                 :on-step state/set-step! :on-play state/play!
                                 :on-pause state/pause! :on-stop state/stop!})]
            [:div {:style {:margin-top "16px"}} (best-panel s l g)]]
           [:div.panel
            [:h3 "the classes"]
            (classes/class-list {:g g :diff (state/diff-at s) :best (state/best-at s)
                                 :mode mode :root (state/root-at s)
                                 :selected (get-in s [:ui :selected])
                                 :on-select state/select-class!})]])])]))
