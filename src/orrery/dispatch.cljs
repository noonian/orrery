(ns orrery.dispatch
  "Routes the views' event handlers to the page's actions. Every
  event handler in the views is data: a vector [action & args], where
  the action is one that orrery.actions lists.

  Replicant's global dispatch routes each handler here, together
  with the DOM event. orrery.app sets that dispatch with
  replicant.dom/set-dispatch!. This is the only place a DOM event is
  read."
  (:require [orrery.derived :as derived]
            [orrery.editor :as editor]
            [orrery.state :as state]))

(defn- dom-event [e] (:replicant/dom-event e))

(defn- target-value [e] (.. (dom-event e) -target -value))

(defn- recall!
  "Brings back an earlier input when `dir` is :back, or a later one
  when it is :forward. Returns true when there was one to bring back."
  [dir]
  (let [{:keys [history recall]} (:repl @state/app-state)]
    (when (case dir :back (seq history) :forward recall)
      (state/recall-repl! dir)
      true)))

(def ^:private editor-callbacks
  {:on-change (fn [text]
                (when (not= text (get-in @state/app-state [:repl :input]))
                  (state/set-repl-input! text)))
   :on-eval state/eval-repl!
   :on-recall recall!})

(defn- editor-hook!
  "Handles the life cycle of the REPL's editor. The view renders an
  empty element for it, and the element's hook carries the text and
  whether to number the lines. The hook builds the editor when the
  element mounts, and Replicant remembers the editor on the element.
  When the text or the numbering changes, the hook brings the editor
  in line. When the element unmounts, it removes the editor."
  [e {:keys [text] :as opts}]
  (let [ed (:replicant/memory e)]
    (case (:replicant/life-cycle e)
      :replicant.life-cycle/mount
      ((:replicant/remember e) (editor/mount! (:replicant/node e) opts editor-callbacks))
      :replicant.life-cycle/unmount
      (some-> ed editor/remove!)
      (some-> ed (editor/sync! opts)))))

(defn- export-name [s] (str "orrery-" (name (:lesson s)) "-step-" (:step s) ".json"))

(defn- copy-export!
  "Copies the e-graph on show to the clipboard, as JSON. The tools
  row says when the JSON is there, or that it could not be copied."
  []
  (let [s @state/app-state
        json (derived/export-json s)]
    (-> (js/navigator.clipboard.writeText json)
        (.then (fn [] (state/set-export-status! (str "copied " (export-name s) " to the clipboard")))
               (fn [e] (state/set-export-status! (str "could not copy: " e)))))))

(defn- download-export!
  "Downloads the e-graph on show as a JSON file. Makes a link to the
  file and clicks it for the learner."
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
  "Calls the action that a handler names. Replicant calls this with
  the event map `e` and the handler data `handler`."
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
    :repl/editor (editor-hook! e (first args))
    :repl/line-numbers (state/toggle-line-numbers!)
    :repl/eval (state/eval-repl!)
    :repl/run (state/run-repl! (first args))
    :repl/clear (state/clear-repl!)
    :repl/scroll (let [el (:replicant/node e)] (set! (.-scrollTop el) (.-scrollHeight el)))
    :adopt (state/adopt-entry! (first args))
    (js/console.warn "orrery: no such action" (pr-str handler))))
