(ns orrery.explanation
  "Why the e-graph holds two terms equal, as the page shows it: a
  chain of rewrites of the whole term, each by one rule at one place,
  and the verdict of the checker.

  cromulent.explain reads a derivation out of an e-graph that keeps a
  journal, and cromulent.derivation checks it against the rules. A
  derivation is a tree: a chain, a step inside a context (congruence),
  a rule used backwards. `steps` flattens it into the chain a person
  reads, the way one writes a calculation out on paper:

    (a·2)/2
    = a·(2/2)    regroup
    = a·1        cancel, at 2/2 → 1
    = a          mul-1"
  (:require [cromulent.derivation :as derivation]
            [cromulent.explain :as explain]
            [cromulent.pattern :as pat]
            [cromulent.term :as term]))

(defn- subst [p sigma]
  (cond
    (pat/variable? p) (get sigma p)
    (term/compound? p) (term/make (term/operator p) (mapv #(subst % sigma) (term/children p)))
    :else p))

(defn- one
  "A flat derivation of one step, from a to b, at the root."
  [a b by]
  {:lhs a :rhs b :steps [{:before a :after b :at [] :was a :now b :by by :backwards? false}]})

(defn- reverse-steps [{:keys [lhs rhs steps]}]
  {:lhs rhs :rhs lhs
   :steps (vec (for [{:keys [before after was now backwards?] :as st} (rseq steps)]
                 (assoc st :before after :after before :was now :now was :backwards? (not backwards?))))})

(defn- flat
  "d as {:lhs a :rhs b :steps [...]}: the equation it derives, and the
  rewrites of the whole term that get from a to b. Each step holds
  the whole term before and after it, the path :at to the place it
  rewrote (child indexes from 0), what was there and what is there
  now, and what it was :by."
  [d rules]
  (case (nth d 0)
    :refl {:lhs (nth d 1) :rhs (nth d 1) :steps []}
    :sym (reverse-steps (flat (nth d 1) rules))
    :trans (let [fs (mapv #(flat % rules) (subvec d 1))]
             {:lhs (:lhs (first fs)) :rhs (:rhs (peek fs)) :steps (into [] (mapcat :steps) fs)})
    :cong (let [[_ op ds] d
                fs (mapv #(flat % rules) ds)
                lhss (mapv :lhs fs)
                rhss (mapv :rhs fs)
                in-context (fn [i t] (term/make op (into (into (subvec rhss 0 i) [t]) (subvec lhss (inc i)))))]
            {:lhs (term/make op lhss)
             :rhs (term/make op rhss)
             :steps (vec (for [i (range (count fs))
                               st (:steps (nth fs i))]
                           (assoc st
                                  :before (in-context i (:before st))
                                  :after (in-context i (:after st))
                                  :at (into [i] (:at st)))))})
    :rule (let [[_ name sigma] d
                {:keys [lhs rhs]} (get rules name)]
            (one (subst lhs sigma) (subst rhs sigma) {:rule name}))
    :computed (let [[_ name a b] d] (one a b {:computed name}))
    :asserted (let [[_ a b reason] d] (one a b {:asserted reason}))))

(defn steps
  "The rewrites of the whole term that derivation d makes, in order
  (see `flat`). rules is a sequence of rule maps."
  [d rules]
  (:steps (flat d (into {} (map (juxt :name identity)) rules))))

(defn between
  "Why g holds terms a and b equal, as {:from a :to b :derivation d
  :steps [...] :check {...}}, the check being cromulent.derivation's
  verdict against rules. {:journal? false} when g keeps no journal;
  nil when g does not hold both terms, or holds them apart."
  [g rules a b]
  (if-not (:journal g)
    {:journal? false}
    (when-let [d (try (explain/explain-terms g a b)
                      (catch #?(:clj Exception :default :default) _ nil))]
      {:journal? true :from a :to b :derivation d
       :steps (steps d rules)
       :check (derivation/check d rules)})))
