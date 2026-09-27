(ns orrery.editor
  "Builds the REPL's editor from prism-code-editor. The editor lays
  highlighted code over a real textarea. It colours brackets by depth,
  marks the pair around the caret, closes brackets and double quotes,
  indents a line opened between two brackets, and has its own undo.

  The page's state holds the editor's text. The editor tells the page
  of every change, and the page tells the editor when its text is
  replaced: on a recall, a clear, or after an evaluation. This
  namespace knows nothing of the state. It is given callbacks, and
  orrery.dispatch supplies them.

  The keys, besides prism-code-editor's own:

    - Ctrl-Enter or Cmd-Enter evaluates.
    - The up arrow in the first line brings back an earlier input, and
      the down arrow in the last line a later one, when there is one.
    - Tab indents the lines of the caret or the selection, with
      spaces. Shift-Tab outdents them. Ctrl-M (Ctrl-Shift-M on a Mac)
      lets Tab leave the editor, or indent again."
  (:require ["prism-code-editor" :refer [createEditor]]
            ["prism-code-editor/commands" :refer [defaultKeymap editHistory editorCommands
                                                  ignoreTab indentSelectedLines]]
            ["prism-code-editor/highlight-brackets" :refer [highlightBracketPairs]]
            ["prism-code-editor/languages/clojure"]
            ["prism-code-editor/match-brackets" :refer [matchBrackets]]
            ["prism-code-editor/prism/languages/clojure"]
            ["prism-code-editor/utils" :refer [setSelection]]
            [clojure.string :as str]))

(defn- first-line? [^js ed]
  (let [[start] (.getSelection ed)]
    (not (str/includes? (subs (.-value ed) 0 start) "\n"))))

(defn- last-line? [^js ed]
  (let [[_ end] (.getSelection ed)]
    (not (str/includes? (subs (.-value ed) end) "\n"))))

(defn- keymap
  "Returns prism-code-editor's default keys with the REPL's in place.
  A key's function returns true when it did something. Otherwise the
  key does what it would have done."
  [{:keys [on-eval on-recall]}]
  (js/Object.assign
   #js {} defaultKeymap
   #js {"Ctrl+Enter" (fn [_] (on-eval) true)
        "Mod+Enter" (fn [_] (on-eval) true)
        "ArrowUp" (fn [ed] (and (first-line? ed) (on-recall :back)))
        "ArrowDown" (fn [ed] (and (last-line? ed) (on-recall :forward)))
        "Tab" (fn [ed] (and (not ignoreTab) (indentSelectedLines ed) true))}))

(defn mount!
  "Builds an editor inside the element `node` and returns it.
  `callbacks` holds :on-change, called with the text after every
  change, :on-eval, and :on-recall, called with :back or :forward,
  which returns true when it recalled an input. `attrs` are set on
  the editor's textarea, and its id among them."
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
