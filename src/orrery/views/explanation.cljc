(ns orrery.views.explanation
  "Renders why the input equals the best term: the chain of rewrites
  of the whole term, each with the rule that made it and, for a step
  inside the term, the place it rewrote, and under the chain the
  verdict of the checker, which shares no code with the e-graph."
  (:require [orrery.views.common :as common]))

(defn- by-view [{:keys [rule computed asserted] :as by} backwards?]
  (cond
    rule [:span.rule-name rule (when backwards? [:span.backwards " backwards"])]
    (contains? by :computed) [:span.rule-name computed [:span.trusted " computed, on trust"]]
    :else [:span.trusted (str "asserted" (when (some? asserted) (str ": " asserted)) ", on trust")]))

(defn- verdict-view [{:keys [ok? trusted problem path]} n]
  [:div.verdict {:id "derivation-verdict" :data-ok (str (boolean ok?)) :data-trusted (str (count trusted))}
   (cond
     (not ok?) [:span.bad (str "✗ the checker refuses step " (pr-str path) ": " problem)]
     (seq trusted) [:span.ok (str "✓ the checker accepts all " n " steps, " (count trusted) " of them on trust")]
     :else [:span.ok (str "✓ the checker accepts all " n (if (= 1 n) " step" " steps")
                          ": each one is a rule, used at one place")])])

(defn explanation-panel
  "Renders `e`, what `orrery.explanation/between` returns, in the
  print mode `mode`. `code` is the code behind the panel, for the
  REPL's buffer."
  [e mode code]
  [:div.panel.explanation {:id "explanation"}
   (common/title "why they are equal" "explain/explain-terms" code)
   (cond
     (nil? e) [:p.hint "The e-graph does not hold the input and the best term equal at this step."]
     (not (:journal? e)) [:p.hint "This run keeps no journal, so there is nothing to explain from. "
                          [:code "(eg/egraph {:explain? true})"] " makes an e-graph that keeps one."]
     (empty? (:steps e)) [:p.hint {:id "explanation-none"}
                          "At this step the best term is the input itself, so there is nothing to explain. Scrub forward."]
     :else
     (let [steps (:steps e)]
       (list
        (into [:ol.derivation {:id "derivation"}
               [:li.start [:span.eq ""] [:span.term (common/term-view (:from e) mode)]]]
              (for [[i {:keys [after at was now by backwards?]}] (map-indexed vector steps)]
                [:li.step {:replicant/key i}
                 [:span.eq "="]
                 [:span.term (common/term-view after mode)]
                 [:span.by (by-view by backwards?)
                  (when (seq at)
                    [:span.local " at " (common/term-view was mode) " → " (common/term-view now mode)])]]))
        (verdict-view (:check e) (count steps))
        [:div.says
         [:code "(explain/explain-terms g term best)"] " reads a derivation from the journal the e-graph keeps, and "
         [:code "(derivation/check d rules)"] " checks it against the rules."])))])
