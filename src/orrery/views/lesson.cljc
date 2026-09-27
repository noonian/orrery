(ns orrery.views.lesson
  "Renders the page for one lesson: the navigation, the prose, the
  inputs, and the panels that the lesson asks for, which
  orrery.views.panels renders. This view and the panels read the
  state value, which they do through `orrery.derived`. The other
  views take values.

  Every handler is data: an `[action & args]` vector over the
  actions in `orrery.actions`. So the page builds on the JVM and on
  Jolt too. `orrery.page-test` builds it there for every lesson at
  every step."
  (:require [clojure.string :as str]
            [orrery.derived :as derived]
            [orrery.eclass :as eclass]
            [orrery.lessons :as lessons]
            [orrery.score :as score]
            [orrery.views.common :as common]
            [orrery.views.panels :as panels]
))

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

(defn- text
  "Renders the words of a page: what the site is, the heading, the
  prose and its reading list."
  [s l]
  (list (about-view s l)
        [:h2 (lessons/heading l)]
        (into [:div.prose] (map #(prose s %) (:prose l)))
        (reading-view l)))

(defn- lesson-layout
  "Renders a lesson in three parts. The text comes first. The
  controls are the operation, its arguments and what it made. The
  e-graph is the replay bar, the tools, the graph and the classes.

  On a wide screen the text and the controls make the left column,
  which scrolls with the page. The e-graph is the right column, which
  stays in view and scrolls inside itself, so a link in the prose
  changes what is in view. On a narrow screen the three stack, with
  the e-graph after the text. The style sheet does both."
  [s l r g panels matches]
  [:div.lesson-grid
   [:div.text (text s l)]
   (when (and r g)
     [:div.egraph
      ;; the term of the run, or of the input when the run knows none;
      ;; a run from the REPL that knows no term has no tree
      (when-let [t (and (contains? panels :tree)
                        (or (:term r) (when-not (= :repl (:from r)) (get-in s [:input :values :term]))))]
        (panels/tree-panel s g t))
      (panels/replay-bar s r g)
      (panels/tools s)
      (panels/graph-panel s matches)
      (if (contains? panels :fork)
        (panels/fork-panels s r g)
        (panels/classes-panel s g matches))])
   (when (and r g)
     [:div.controls
      (when (seq (:inputs l)) (input-area s l))
      (panels/run-panel s r g (:fn (lessons/operation l)))
      (when (derived/root-at s) (panels/best-panel s (:costs l)))
      (when (and (contains? panels :matches) matches) (panels/matches-panel matches))
      (when (and (contains? panels :stats) (seq (:stats r))) (panels/stats-panel s r))])])

(defn page
  "Renders the page. Every page has the REPL in a dock along the
  bottom of the viewport. The dock's height is the style variable
  `--dock-h`, which the page's padding and the e-graph column read,
  so nothing is hidden under the dock."
  [s]
  (let [l (derived/lesson s)
        r (:run s)
        g (derived/current-egraph s)
        mode (get-in s [:ui :print])
        panels (or (:panels l) #{})
        matches (derived/matches-at s)
        {:keys [repl-open? dock-height]} (:ui s)]
    [:main.page {:class "has-dock"
                 :style (when repl-open?
                          {:--dock-h (if dock-height (str dock-height "px") "40vh")})}
     [:header.masthead
      [:h1 "orrery"]
      [:span.tagline "an e-graph explorer, for understanding"]
      [:span.spacer]
      (common/print-toggle mode)]
     (nav (:key l))
     (when l
       [:section.lesson (lesson-layout s l r g panels matches)])
     (colophon-view s)
     (panels/repl-dock s)]))
