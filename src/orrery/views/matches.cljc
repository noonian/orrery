(ns orrery.views.matches
  "The matches of each pattern rule in the e-graph on show: the class
  and the bindings, which the next iteration will apply."
  (:require [orrery.notation :as notation]
            [orrery.views.common :as common]))

(defn matches-panel [{:keys [matches]}]
  [:div.panel.matches {:id "matches"}
   (common/title "matches in this step" "pat/ematch")
   (if (every? (comp empty? :matches) matches)
     [:p.hint "no rule matches anything here"]
     (into [:div]
           (for [{:keys [name lhs matches]} matches]
             [:div.rule-matches {:replicant/key name}
              [:div.rule-name [:b name] " " [:code.native (pr-str lhs)]
               [:span.count (str " · " (count matches) (if (= 1 (count matches)) " match" " matches"))]]
              (into [:ul]
                    (for [m matches]
                      [:li
                       [:span.ref {:on {:click [:select (:class m)]}} (notation/class-ref (:class m))]
                       (for [[v id] (sort-by str (:bindings m))]
                         (str "  " v " = " (notation/class-ref id)))]))])))])
