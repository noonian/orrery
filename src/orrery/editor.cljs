(ns orrery.editor
  "Builds the REPL's two editors from prism-code-editor. An editor
  lays highlighted code over a real textarea. It colours brackets by
  depth, marks the pair around the caret, closes brackets and double
  quotes, and has its own undo.

  The REPL has two editors, each with its own text:

    - The line is the REPL's input, as a terminal's line is. It is the
      closed dock, and it stands under the history in the open dock.
    - The buffer is the editor on the left of the open dock, for
      longer code. Nothing that the REPL does empties it.

  The page's state holds both texts. An editor tells the page of
  every change, and the page tells the editor when its text is
  replaced: on a recall, after the line is evaluated, or when an
  entry of the history is copied into the buffer. This namespace
  knows nothing of the state. It is given callbacks, and
  orrery.dispatch supplies them.

  The keys of the line, besides prism-code-editor's own:

    - Enter evaluates the line when every bracket in it is closed,
      and empties it. When a bracket is open, Enter starts a new line,
      indented as Clojure is (`orrery.forms/indent`).
    - Shift-Enter starts a new line. Ctrl-Enter evaluates the line
      whatever it holds.
    - The up arrow in the first line brings back an earlier input, and
      the down arrow in the last line a later one.

  The keys of the buffer:

    - Enter starts a new line, indented as Clojure is.
    - Ctrl-Enter or Cmd-Enter evaluates the form at the caret.
    - Ctrl-Shift-Enter or Cmd-Shift-Enter evaluates every form.

  In both, Escape closes the open dock, and Tab indents the lines of
  the caret or the selection, with spaces. Shift-Tab outdents them.
  Ctrl-M (Ctrl-Shift-M on a Mac) lets Tab leave the editor, or indent
  again."
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
  "Evaluates the buffer as `how` says, :form or :all, and marks what
  was evaluated."
  [^js ed {:keys [on-eval]} how]
  (let [v (.-value ed)]
    (flash! ed v (on-eval how (first (.getSelection ed))))
    true))

(defn- common-keys
  "Returns the keys that the line and the buffer share."
  [{:keys [on-close open?]}]
  {"Escape" (fn [_] (when (open?) (on-close) true))
   "Tab" (fn [ed] (and (not ignoreTab) (indentSelectedLines ed) true))})

(defn- line-keys [{:keys [on-eval on-recall]}]
  {"Enter" (fn [ed]
             (if (forms/complete? (.-value ed))
               (do (on-eval) true)
               (new-line! ed)))
   "Shift+Enter" new-line!
   "Ctrl+Enter" (fn [_] (on-eval) true)
   "Mod+Enter" (fn [_] (on-eval) true)
   "ArrowUp" (fn [ed] (and (first-line? ed) (on-recall :back)))
   "ArrowDown" (fn [ed] (and (last-line? ed) (on-recall :forward)))})

(defn- buffer-keys [callbacks]
  {"Enter" new-line!
   "Shift+Enter" new-line!
   "Ctrl+Enter" (fn [ed] (evaluate! ed callbacks :form))
   "Mod+Enter" (fn [ed] (evaluate! ed callbacks :form))
   "Shift+Ctrl+Enter" (fn [ed] (evaluate! ed callbacks :all))
   "Shift+Mod+Enter" (fn [ed] (evaluate! ed callbacks :all))})

(defn- keymap
  "Returns prism-code-editor's default keys with those of `role`,
  :line or :buffer, in place. A key's function returns true when it
  did something. Otherwise the key does what it would have done."
  [role callbacks]
  (js/Object.assign
   #js {} defaultKeymap
   (clj->js (merge (common-keys callbacks)
                   (case role
                     :line (line-keys callbacks)
                     :buffer (buffer-keys callbacks))))))

(defn mount!
  "Builds an editor inside the element `node` and returns it. `role`
  is :line or :buffer. `callbacks` holds:

    - :on-change, called with the text after every change;
    - :on-eval, which evaluates. The line calls it with no arguments.
      The buffer calls it with :form or :all and the caret's position,
      and it returns the forms it evaluated;
    - :on-recall, for the line, called with :back or :forward, which
      returns true when it recalled an input;
    - :on-close, which closes the dock, and :open?, which returns
      true when the dock is open.

  `attrs` are set on the editor's textarea, and its id among them."
  [node {:keys [role text line-numbers? attrs]} {:keys [on-change] :as callbacks}]
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
                         (editorCommands (keymap role callbacks) #js ["\"\"" "()" "[]" "{}"]))
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
