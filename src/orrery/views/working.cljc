(ns orrery.views.working
  "Renders an operation of bendix's ring worked through, as
  `orrery.working/work` returns it. The prose widget
  `[:working op & terms]` stands between two paragraphs."
  (:require [orrery.views.common :as common]))

(defn- t [term mode] (common/term-view term mode))

(defn- product-view [{:keys [a b grid? rows cols cells like result]} mode]
  [:div.working-out {:data-op "product"}
   [:div.working-head "(" (t a mode) ")·(" (t b mode) ")"]
   (when grid?
     (list
      [:table.product
       [:thead [:tr [:th "·"] (for [c cols] [:th (t c mode)])]]
       [:tbody
        (for [[r row] (map vector rows cells)]
          [:tr [:th (t r mode)]
           (for [{:keys [term mark]} row]
             [:td {:class (some-> mark name)} (t term mode)])])]]
      (for [{:keys [parts sum cancels?]} like]
        [:div.like {:class (when cancels? "cancels")}
         (interpose " + " (for [p parts] (t p mode)))
         " = " (t sum mode)
         (if cancels? ": like terms that cancel" ": like terms, collected")])))
   [:div.working-result "= " (t result mode)]])

(defn- steps-view [{:keys [steps]} mode]
  (for [{:keys [before after]} steps]
    [:div.step (t before mode) " → " (t after mode)]))

(defn- reduction-view [{:keys [base q term substituted result] :as w} mode]
  [:div.working-out {:data-op "reduction"}
   [:div.working-head (t term mode) ", where " (t [:expt base 2] mode) " = " (t q mode)]
   (steps-view w mode)
   [:div.step "= " (t substituted mode)]
   [:div.working-result "= " (t result mode)]])

(defn- derivative-view [{:keys [term x result] :as w} mode]
  [:div.working-out {:data-op "derivative"}
   [:div.working-head "d/d" (t x mode) " of " (t term mode) ", one monomial at a time"]
   (steps-view w mode)
   [:div.working-result "= " (t result mode)]])

(defn working-view
  "Renders the working `w` in the print mode `mode`, or nothing when
  there is no working."
  [w mode]
  (case (:op w)
    :product (product-view w mode)
    :reduction (reduction-view w mode)
    :derivative (derivative-view w mode)
    nil))
