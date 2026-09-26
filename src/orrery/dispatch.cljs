(ns orrery.dispatch
  "Where hiccup meets the page. Every event handler in the views is
  data, [action & args] over orrery.actions, and Replicant's global
  dispatch (replicant.dom/set-dispatch!, in orrery.app) routes it
  here with the DOM event beside it. This is the only place a DOM
  event is read."
  (:require [orrery.state :as state]))

(defn- dom-event [e] (:replicant/dom-event e))

(defn- target-value [e] (.. (dom-event e) -target -value))

(defn dispatch
  "Replicant's dispatch: the event map and the handler data."
  [e [action & args :as handler]]
  (case action
    :step (state/set-step! (first args))
    :step-from-range (state/set-step! (js/parseInt (target-value e) 10))
    :play (state/play!)
    :pause (state/pause!)
    :stop (state/stop!)
    :select (state/select-class! (first args))
    :deselect (state/deselect!)
    :select-term (let [[t k] args] (state/select-term! t k))
    :tree/hover (do (.stopPropagation (dom-event e)) (state/hover! (first args)))
    :tree/open (do (.stopPropagation (dom-event e)) (state/select-class! (first args)))
    :cost (state/set-cost! (first args))
    :alternative (state/choose-alternative-by-label! (first args))
    :print (state/set-print! (first args))
    :field (state/set-field! (first args) (target-value e))
    :run (state/submit-input!)
    :surprise (state/surprise!)
    :repl/input (state/set-repl-input! (target-value e))
    :repl/keydown (let [d (dom-event e)]
                    (when (and (= "Enter" (.-key d)) (or (.-ctrlKey d) (.-metaKey d)))
                      (.preventDefault d)
                      (state/eval-repl!)))
    :repl/eval (state/eval-repl!)
    :repl/clear (state/clear-repl!)
    :adopt (state/adopt-entry! (first args))
    (js/console.warn "orrery: no such action" (pr-str handler))))
