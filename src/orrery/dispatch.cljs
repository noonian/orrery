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

(defn- focus!
  "Puts the focus in the textarea whose id is `id`, with the caret at
  the end of its text when `end?` is true."
  ([id] (focus! id false))
  ([id end?]
   (when-let [ta (js/document.getElementById id)]
     (.focus ta)
     (when end?
       (let [n (count (.-value ta))]
         (.setSelectionRange ta n n))))))

(defn- focus-dock!
  "Puts the focus in the buffer when the dock is open, and on the
  line when it is closed. Opening or closing the dock moves the
  line's element, and a moved element loses the focus."
  []
  (focus! (if (get-in @state/app-state [:ui :repl-open?]) "repl-buffer" "repl-input")))

(defn- close-dock! []
  (state/close-dock!)
  (focus-dock!))

(def ^:private editor-callbacks
  "The callbacks of each editor, by its role (`orrery.editor`)."
  {:line {:on-change (fn [text]
                       (when (not= text (get-in @state/app-state [:repl :input]))
                         (state/set-repl-input! text)))
          :on-eval state/eval-line!
          :on-recall recall!
          :on-close close-dock!
          :open? #(boolean (get-in @state/app-state [:ui :repl-open?]))}
   :buffer {:on-change (fn [text]
                         (when (not= text (get-in @state/app-state [:repl :buffer]))
                           (state/set-repl-buffer! text)))
            :on-eval state/eval-buffer!
            :on-close close-dock!
            :open? #(boolean (get-in @state/app-state [:ui :repl-open?]))}})

(defn- toggle-dock!
  "Opens the REPL's dock, or closes it, and puts the caret in the
  buffer or on the line."
  []
  (state/toggle-dock!)
  (focus-dock!))

(defn- to-buffer!
  "Copies the code of history entry `i` into the buffer, and puts the
  caret at the buffer's end."
  [i]
  (state/to-buffer! i)
  (focus! "repl-buffer" true))

(defn- add-code!
  "Adds `code` to the end of the buffer, opens the dock, and puts the
  caret at the buffer's end. The buffer is built when the dock opens,
  so the focus waits for the render."
  [code]
  (state/add-code! code)
  (js/requestAnimationFrame #(focus! "repl-buffer" true)))

(defn- editor-hook!
  "Handles the life cycle of one of the REPL's editors. The view
  renders an empty element for it, and the element's hook carries the
  editor's role, its text and whether to number the lines. The hook
  builds the editor when the element mounts, and Replicant remembers
  the editor on the element. When the text or the numbering changes,
  the hook brings the editor in line. When the element unmounts, it
  removes the editor."
  [e {:keys [role] :as opts}]
  (let [ed (:replicant/memory e)]
    (case (:replicant/life-cycle e)
      :replicant.life-cycle/mount
      ((:replicant/remember e) (editor/mount! (:replicant/node e) opts (editor-callbacks role)))
      :replicant.life-cycle/unmount
      (some-> ed editor/remove!)
      (some-> ed (editor/sync! opts)))))

(defn- resize-dock!
  "Resizes the REPL's dock while the pointer that pressed its top
  edge is dragged. While it drags, the height is set on the page's
  style directly, so the page is not rendered again on every move.
  When the pointer is let go, the height goes into the state. The
  dock is kept between 120 pixels and 85% of the viewport."
  [e]
  (let [ev (dom-event e)
        dock (.closest (:replicant/node e) "#repl-dock")
        page (.closest dock "main.page")
        start-y (.-clientY ev)
        start-h (.-offsetHeight dock)
        height (fn [^js ev]
                 (js/Math.round (max 120 (min (+ start-h (- start-y (.-clientY ev)))
                                              (* 0.85 js/innerHeight)))))
        move (fn [ev] (.setProperty (.-style page) "--dock-h" (str (height ev) "px")))]
    (.preventDefault ev)
    (letfn [(up [ev]
              (js/window.removeEventListener "pointermove" move)
              (js/window.removeEventListener "pointerup" up)
              (state/set-dock-height! (height ev)))]
      (js/window.addEventListener "pointermove" move)
      (js/window.addEventListener "pointerup" up))))

(defn- pan-graph!
  "Moves the graph in its frame while the pointer that pressed it is
  dragged, by scrolling the frame. A press that moves more than a few
  pixels is a drag, and the click that ends a drag does not open the
  class under the pointer."
  [e]
  (let [ev (dom-event e)
        frame (.-currentTarget ev)
        x0 (.-clientX ev)
        y0 (.-clientY ev)
        left0 (.-scrollLeft frame)
        top0 (.-scrollTop frame)
        dragged? (volatile! false)
        swallow (fn [^js ev] (.stopPropagation ev) (.preventDefault ev))
        move (fn [^js ev]
               (let [dx (- (.-clientX ev) x0)
                     dy (- (.-clientY ev) y0)]
                 (when (and (not @dragged?) (< 4 (max (js/Math.abs dx) (js/Math.abs dy))))
                   (vreset! dragged? true)
                   (.add (.-classList frame) "panning"))
                 (when @dragged?
                   (set! (.-scrollLeft frame) (- left0 dx))
                   (set! (.-scrollTop frame) (- top0 dy)))))]
    (when (zero? (.-button ev))
      (.preventDefault ev)
      (letfn [(up [_]
                (js/window.removeEventListener "pointermove" move)
                (js/window.removeEventListener "pointerup" up)
                (.remove (.-classList frame) "panning")
                (when @dragged?
                  ;; The click that ends the drag follows this pointerup with no
                  ;; press between them. A new press is a new click, so it stops
                  ;; the swallowing: a browser handles input before timers, and a
                  ;; busy page would otherwise swallow the next click too.
                  (letfn [(stop [_]
                            (js/window.removeEventListener "click" swallow true)
                            (js/window.removeEventListener "pointerdown" stop true))]
                    (js/window.addEventListener "click" swallow true)
                    (js/window.addEventListener "pointerdown" stop true)
                    (js/setTimeout stop 0))))]
        (js/window.addEventListener "pointermove" move)
        (js/window.addEventListener "pointerup" up)))))

(defn- zoom-graph!
  "Zooms the graph, and scrolls its frame so that the point of the
  picture in the middle of the frame stays there."
  [dir]
  (if-let [frame (some-> (js/document.getElementById "graph") (.querySelector ".graph-scroll"))]
    (let [w0 (.-scrollWidth frame)
          h0 (.-scrollHeight frame)
          fx (/ (+ (.-scrollLeft frame) (/ (.-clientWidth frame) 2)) w0)
          fy (/ (+ (.-scrollTop frame) (/ (.-clientHeight frame) 2)) h0)]
      (state/zoom-graph! dir)
      ;; The render is synchronous, so the picture has its new size.
      (set! (.-scrollLeft frame) (- (* fx (.-scrollWidth frame)) (/ (.-clientWidth frame) 2)))
      (set! (.-scrollTop frame) (- (* fy (.-scrollHeight frame)) (/ (.-clientHeight frame) 2))))
    (state/zoom-graph! dir)))

(defn key!
  "Handles a key pressed anywhere on the page. Ctrl-` opens the
  REPL's dock, or closes it."
  [^js ev]
  (when (and (.-ctrlKey ev) (= "Backquote" (.-code ev)))
    (.preventDefault ev)
    (toggle-dock!)))

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
    :graph/zoom (zoom-graph! (first args))
    :graph/pan (pan-graph! e)
    :export/copy (copy-export!)
    :export/download (download-export!)
    ;; act first, and stay on the inputs when the run could not read them;
    ;; a new tab starts at the top of the column
    :tab/show (let [[t h] args]
                (when h (dispatch e h))
                (when-not (or (= t (get-in @state/app-state [:ui :tab]))
                              (and (= :run (first h)) (get-in @state/app-state [:input :error])))
                  (some-> (js/document.querySelector ".lesson-grid > .egraph") (.scrollTo 0 0))
                  (state/show-tab! t)))
    :cost (state/set-cost! (first args))
    :alternative (state/choose-alternative-by-label! (first args))
    :print (state/set-print! (first args))
    :field (state/set-field! (first args) (target-value e))
    :run (state/submit-input!)
    :surprise (state/surprise!)
    :repl/editor (editor-hook! e (first args))
    :repl/dock (toggle-dock!)
    :repl/help (state/toggle-repl-help!)
    :repl/resize (resize-dock! e)
    :repl/line-numbers (state/toggle-line-numbers!)
    :repl/eval (do (state/eval-buffer! (first args) (some-> (js/document.getElementById "repl-buffer") .-selectionStart))
                   (focus! "repl-buffer"))
    :repl/to-buffer (to-buffer! (first args))
    :repl/code (add-code! (first args))
    :repl/snippets (state/toggle-snippets!)
    :repl/run (state/run-repl! (first args))
    :repl/clear (state/clear-repl!)
    :repl/clear-buffer (do (state/clear-buffer!) (focus! "repl-buffer"))
    :repl/scroll (let [el (:replicant/node e)] (set! (.-scrollTop el) (.-scrollHeight el)))
    :adopt (state/adopt-entry! (first args))
    (js/console.warn "orrery: no such action" (pr-str handler))))
