(ns orrery.actions
  "The vocabulary of the views' event handlers. Every handler in the
  hiccup is data, [action & args] over these, never a function:
  Replicant compares handlers by value and leaves an unchanged one
  alone, where a closure is re-registered on every render, and a
  view with no functions in it builds on the JVM and Jolt, where the
  suite checks every handler against this table. orrery.dispatch
  routes them in the browser.")

(def all
  "Action -> what its handler means."
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
   :export/copy "[:export/copy]: the e-graph on show as egraph-serialize JSON, to the clipboard"
   :export/download "[:export/download]: the same, as a file"
   :cost "[:cost key]: the cost in force"
   :alternative "[:alternative label]: run the lesson's alternative with this label"
   :print "[:print mode]: the print mode, :notation or :native"
   :field "[:field key]: the input field's text, from the event target"
   :run "[:run]: read every field and run"
   :surprise "[:surprise]: draw, score and run a candidate"
   :repl/input "[:repl/input]: the REPL prompt's text, from the event target"
   :repl/keydown "[:repl/keydown]: evaluate on Ctrl-Enter or Cmd-Enter"
   :repl/eval "[:repl/eval]"
   :repl/clear "[:repl/clear]"
   :adopt "[:adopt i]: make the value of REPL history entry i the run on show"})

(defn known?
  "Is h a handler over this vocabulary?"
  [h]
  (and (vector? h) (contains? all (first h))))
