(ns orrery.editor
  "Builds the REPL's editor from prism-code-editor. The editor lays
  highlighted code over a real textarea. It colours brackets by depth,
  marks the pair around the caret, closes brackets and double quotes,
  and has its own undo.

  The page's state holds the editor's text. The editor tells the page
  of every change, and the page tells the editor when its text is
  replaced: on a recall, or after the closed dock evaluates its line.
  This namespace knows nothing of the state. It is given callbacks,
  and orrery.dispatch supplies them.

  The editor stands in the REPL's dock, which is closed or open. The
  closed dock is one line, like a terminal's. The open dock is an
  editor for several forms. The keys, besides prism-code-editor's
  own:

    - Enter, in the closed dock, evaluates the line when every
      bracket in it is closed. When a bracket is open, it opens the
      dock and starts a new line. In the open dock, Enter starts a new
      line, indented as Clojure is (`orrery.forms/indent`).
    - Shift-Enter starts a new line, and opens the dock.
    - Ctrl-Enter or Cmd-Enter evaluates the form at the caret. In the
      closed dock it evaluates the line.
    - Ctrl-Shift-Enter or Cmd-Shift-Enter evaluates every form.
    - The up arrow brings back an earlier input, and the down arrow a
      later one, in the closed dock.
    - Escape closes the open dock.
    - Tab indents the lines of the caret or the selection, with
      spaces. Shift-Tab outdents them. Ctrl-M (Ctrl-Shift-M on a Mac)
      lets Tab leave the editor, or indent again.

  Text of several lines is never emptied by an evaluation, even in
  the closed dock. Only a single line is evaluated as a terminal's
  line."
  (:require ["prism-code-editor" :refer [createEditor]]
            ["prism-code-editor/commands" :refer [defaultKeymap editHistory editorCommands
                                                  ignoreTab indentSelectedLines]]
            ["prism-code-editor/highlight-brackets" :refer [highlightBracketPairs]]
            ["prism-code-editor/languages/clojure"]
            ["prism-code-editor/match-brackets" :refer [matchBrackets]]
            ["prism-code-editor/prism/languages/clojure"]
            ["prism-code-editor/utils" :refer [insertText setSelection]]
            [clojure.string :as str]
            [orrery.forms :as forms]))

(defn- first-line? [^js ed]
  (let [[start] (.getSelection ed)]
    (not (str/includes? (subs (.-value ed) 0 start) "\n"))))

(defn- last-line? [^js ed]
  (let [[_ end] (.getSelection ed)]
    (not (str/includes? (subs (.-value ed) end) "\n"))))

(defn- one-line? [^js ed] (not (str/includes? (.-value ed) "\n")))

(defn- new-line!
  "Replaces the selection with a line break and the indentation of
  the new line. The spaces before the caret at the end of its line go,
  and the spaces after it become the new line's indentation."
  [^js ed]
  (let [v (.-value ed)
        [start end] (.getSelection ed)
        before (count (re-find #"[ \t]*$" (subs v 0 start)))
        after (count (re-find #"^[ \t]*" (subs v end)))]
    (insertText ed (str "\n" (apply str (repeat (forms/indent v start) " ")))
                (- start before) (+ end after))
    true))

(defn- flash!
  "Marks the lines of the forms `fs` for a moment, to show what was
  evaluated. `v` is the text the forms are positions in."
  [^js ed v fs]
  (let [line-of (fn [pos] (inc (count (re-seq #"\n" (subs v 0 pos)))))
        lines (.-lines ed)
        els (for [{:keys [start end]} fs
                  i (range (line-of start) (inc (line-of (max start (dec end)))))
                  :let [el (aget lines i)]
                  :when el]
              el)]
    (doseq [el els] (.add (.-classList el) "evaluated"))
    (js/setTimeout #(doseq [el els] (.remove (.-classList el) "evaluated")) 450)))

(defn- evaluate!
  "Evaluates as `how` says, :form or :all. In the closed dock, a
  single line is evaluated as a terminal's line instead."
  [^js ed {:keys [on-eval open?]} how]
  (let [v (.-value ed)]
    (if (and (not (open?)) (one-line? ed))
      (on-eval :line nil)
      (flash! ed v (on-eval how (first (.getSelection ed)))))
    true))

(defn- keymap
  "Returns prism-code-editor's default keys with the REPL's in place.
  A key's function returns true when it did something. Otherwise the
  key does what it would have done."
  [{:keys [on-eval on-recall on-open on-close open?] :as callbacks}]
  (js/Object.assign
   #js {} defaultKeymap
   #js {"Enter" (fn [ed]
                  (cond
                    (open?) (new-line! ed)
                    (not (one-line? ed)) (do (on-open) true)
                    (forms/complete? (.-value ed)) (do (on-eval :line nil) true)
                    :else (do (on-open) (new-line! ed))))
        "Shift+Enter" (fn [ed] (when-not (open?) (on-open)) (new-line! ed))
        "Ctrl+Enter" (fn [ed] (evaluate! ed callbacks :form))
        "Mod+Enter" (fn [ed] (evaluate! ed callbacks :form))
        "Shift+Ctrl+Enter" (fn [ed] (evaluate! ed callbacks :all))
        "Shift+Mod+Enter" (fn [ed] (evaluate! ed callbacks :all))
        "Escape" (fn [_] (when (open?) (on-close) true))
        "ArrowUp" (fn [ed] (and (not (open?)) (first-line? ed) (on-recall :back)))
        "ArrowDown" (fn [ed] (and (not (open?)) (last-line? ed) (on-recall :forward)))
        "Tab" (fn [ed] (and (not ignoreTab) (indentSelectedLines ed) true))}))

(defn mount!
  "Builds an editor inside the element `node` and returns it.
  `callbacks` holds:

    - :on-change, called with the text after every change;
    - :on-eval, called with :line, :form or :all and the caret's
      position, which returns the forms it evaluated;
    - :on-recall, called with :back or :forward, which returns true
      when it recalled an input;
    - :on-open and :on-close, which open and close the dock;
    - :open?, which returns true when the dock is open.

  `attrs` are set on the editor's textarea, and its id among them."
  [node {:keys [text line-numbers? attrs]} {:keys [on-change] :as callbacks}]
  (let [ed (createEditor node
                         #js {:language "clojure"
                              :value text
                              :tabSize 2
                              :insertSpaces true
                              :lineNumbers (boolean line-numbers?)
                              :wordWrap true
                              :onUpdate (fn [v _] (on-change v))}
                         (matchBrackets true)
                         (highlightBracketPairs)
                         (editHistory)
                         (editorCommands (keymap callbacks) #js ["\"\"" "()" "[]" "{}"]))
        ta (.-textarea ed)]
    (doseq [[k v] attrs] (.setAttribute ta (name k) v))
    ed))

(defn sync!
  "Makes the editor `ed` show `text` and the line numbers or not. It
  replaces the text only when the text differs, which leaves the
  caret alone while the learner types. A replaced text puts the caret
  at its end, as a terminal does."
  [^js ed {:keys [text line-numbers?]}]
  (when (not= (boolean line-numbers?) (boolean (.. ed -options -lineNumbers)))
    (.setOptions ed #js {:lineNumbers (boolean line-numbers?)}))
  (when (not= text (.-value ed))
    (.setOptions ed #js {:value text})
    (when (.-focused ed) (setSelection ed (count text)))))

(defn remove! [^js ed] (.remove ed))
