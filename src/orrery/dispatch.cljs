(ns orrery.dispatch
  "Where hiccup meets the page. Every event handler in the views is
  data, [action & args] over orrery.actions, and Replicant's global
  dispatch (replicant.dom/set-dispatch!, in orrery.app) routes it
  here with the DOM event beside it. This is the only place a DOM
  event is read."
  (:require [orrery.derived :as derived]
            [orrery.state :as state]))

(defn- dom-event [e] (:replicant/dom-event e))

(defn- target-value [e] (.. (dom-event e) -target -value))

(defn- export-name [s] (str "orrery-" (name (:lesson s)) "-step-" (:step s) ".json"))

(defn- copy-export!
  "The e-graph on show as JSON, to the clipboard; the tools row says
  when it is there."
  []
  (let [s @state/app-state
        json (derived/export-json s)]
    (-> (js/navigator.clipboard.writeText json)
        (.then (fn [] (state/set-export-status! (str "copied " (export-name s) " to the clipboard")))
               (fn [e] (state/set-export-status! (str "could not copy: " e)))))))

(defn- download-export!
  "The same as a file, through a link clicked for the learner."
  []
  (let [s @state/app-state
        json (derived/export-json s)
        url (js/URL.createObjectURL (js/Blob. #js [json] #js {:type "application/json"}))
        a (js/document.createElement "a")]
    (set! (.-href a) url)
    (set! (.-download a) (export-name s))
    (.click a)
    (js/setTimeout #(js/URL.revokeObjectURL url) 1000)
    (state/set-export-status! (str "downloaded " (export-name s)))))

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
    :graph/toggle (state/toggle-graph!)
    :graph/filter (state/toggle-graph-filter!)
    :graph/zoom (state/zoom-graph! (first args))
    :export/copy (copy-export!)
    :export/download (download-export!)
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
