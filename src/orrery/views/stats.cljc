(ns orrery.views.stats
  "Renders the statistics of a saturation, one row per iteration:
  the matches and applications per rule, the counts, the bans, and
  the time.

  The rules are the rules of the run, in the order of the run. A
  runner result put on show from the REPL names no rules, so its
  columns are the names that its first iteration counted."
  (:require [clojure.string :as str]
            [orrery.views.common :as common]))

(defn stats-table [{:keys [stats rules step]}]
  (let [names (if (seq rules)
                (mapv :name rules)
                (vec (sort (keys (:matches (first stats))))))]
    [:div
     [:div.legend [:span "per rule: applied / matched"] [:span "click a row to scrub to it"]]
     [:table.stats
      [:thead
       (-> [:tr [:th "iter"]]
           (into (for [n names] [:th n]))
           (into [[:th "nodes"] [:th "classes"] [:th "banned"] [:th "ms"]]))]
      (into [:tbody]
            (for [s stats]
              (-> [:tr {:replicant/key (:iter s)
                        :class (when (= (:iter s) step) "current")
                        :on {:click [:step (:iter s)]}}
                   [:td (str (:iter s))]]
                  (into (for [n names]
                          [:td {:class (when (pos? (get-in s [:applied n] 0)) "applied")}
                           (str (get-in s [:applied n] 0) " / " (get-in s [:matches n] 0))]))
                  (into [[:td (str (:nodes s))]
                         [:td (str (:classes s))]
                         [:td (str/join ", " (sort (:banned s)))]
                         [:td (str (common/round (+ (:search-ms s) (:apply-ms s) (:rebuild-ms s))))]]))))]]))
