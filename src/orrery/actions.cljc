(ns orrery.actions
  "The vocabulary of the views' event handlers. Every handler in the
  hiccup is data and never a function. A handler is a vector
  [action & args], where the action is a key of `all`.

  There are two reasons to use data:

    - Replicant compares handlers by value and leaves an unchanged
      handler alone. A closure is registered again on every render.
    - A view with no functions in it builds on the JVM and Jolt.
      There the suite checks every handler against this table.

  orrery.dispatch routes the handlers in the browser.")

(def all
  "Maps each action to a description of what its handler means."
  {:step "[:step k]: scrub to step k"
   :step-from-range "[:step-from-range]: scrub to the range input's value"
   :play "[:play]: play the timeline"
   :pause "[:pause]"
   :stop "[:stop]: stop a running saturation"
   :select "[:select id]: open the class of id, or close it if open"
   :deselect "[:deselect]: close the opened class"
   :select-term "[:select-term t k]: open the class holding t, scrubbing to k first when given"
   :tree/hover "[:tree/hover id]: light the class of a tree or graph node, nil to unlight; stops propagation"
   :tree/open "[:tree/open id]: open the class of a tree or graph node; stops propagation"
   :graph/toggle "[:graph/toggle]: draw the graph, or hide it"
   :graph/filter "[:graph/filter]: draw only what the opened class reaches, or everything again"
   :graph/zoom "[:graph/zoom dir]: the graph larger (:in), smaller (:out), or fitted to the width (:fit)"
   :graph/pan "[:graph/pan]: the graph moved in its frame while the pointer that pressed it drags"
   :export/copy "[:export/copy]: the e-graph on show as egraph-serialize JSON, to the clipboard"
   :export/download "[:export/download]: the same, as a file"
   :cost "[:cost key]: the cost in force"
   :alternative "[:alternative label]: run the lesson's alternative with this label"
   :print "[:print mode]: the print mode, :notation or :native"
   :field "[:field key]: the input field's text, from the event target"
   :run "[:run]: read every field and run"
   :surprise "[:surprise]: draw, score and run a candidate"
   :repl/editor "[:repl/editor opts]: a life-cycle hook, not an event: builds the REPL's line or buffer (:role opts), keeps it showing (:text opts) and numbering its lines when (:line-numbers? opts), and removes it"
   :repl/dock "[:repl/dock]: open the REPL's dock along the bottom of the page, or close it"
   :repl/help "[:repl/help]: show the REPL's keys and the names in scope, or hide them"
   :repl/resize "[:repl/resize]: on a press of the dock's top edge, resize the dock by dragging"
   :repl/line-numbers "[:repl/line-numbers]: number the editor's lines, or stop"
   :repl/eval "[:repl/eval how]: evaluate the form at the buffer's caret (:form) or every form in the buffer (:all)"
   :repl/to-buffer "[:repl/to-buffer i]: add the code of history entry i to the end of the buffer"
   :repl/code "[:repl/code code]: add code to the end of the buffer, and open the dock"
   :repl/snippets "[:repl/snippets]: show the snippets of code for the buffer, or hide them"
   :repl/run "[:repl/run code]: evaluate code from a link in the prose, as if it had been typed"
   :repl/clear "[:repl/clear]: empty the history"
   :repl/clear-buffer "[:repl/clear-buffer]: empty the buffer"
   :repl/scroll "[:repl/scroll]: a life-cycle hook, not an event: the history, rendered, scrolls to its last entry"
   :adopt "[:adopt i]: make the value of REPL history entry i the run on show"})

(defn known?
  "Returns true when `h` is a handler over this vocabulary: a vector
  whose first element is a key of `all`."
  [h]
  (and (vector? h) (contains? all (first h))))
