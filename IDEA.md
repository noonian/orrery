# orrery — an e-graph explorer, for understanding

A web page that teaches what an e-graph is by letting you watch one:
add a term, assert an equality, run a rule, scrub a saturation
forwards and backwards, change the cost and see a different answer
come out, and see the polynomial that makes a hundred arrangements of
a sum one class. Every picture is a real execution of cromulent and
bendix, not a cartoon of one.

Named **orrery**: the clockwork model of the planets, turned by a
crank, built so that a person can see how the system moves (naming,
below). Third project of the symbolics
workspace, alongside cromulent (the e-graph) and bendix (the CAS), in
the layout of ../design/overview.md.

## 0. The idea

Two facts make this cheap and honest at once.

- **The engine keeps its own history.** cromulent's e-graph is a
  persistent value. `embiggen` with `:timeline? true` returns the
  e-graph before the first iteration and after every one, at the
  cost of a union-find vector each; the difference between two
  iterations is a set difference on the hashcons; extraction under
  another cost is another call on the same value; a what-if is a
  fork. A mutable engine cannot export any of this without
  instrumentation. Ours exports it by returning values. The page is
  a scrubber over those values.
- **The fleet already knows the shape.** ../../time-and-space/webapp
  had one architectural insight, the one above: a widget is a scrubber
  over a real execution. It never got past its first example, so orrery keeps the insight and not the
  construction: the engine is compiled, not interpreted, and the page
  is built to carry eleven lessons, a REPL and forks (section 6).

The thesis of bendix is *simplification is equality saturation plus
taste*. orrery's job is to make a person see both halves: the graph
that holds every form, and the cost that picks one.

## 1. Audience (decided 2026-09-25)

**Learners first.** People who want to understand what an e-graph is
and how it works, who may not know much mathematics, and who should
be able to watch interesting behaviour without typing any. The
page teaches what e-graphs are and how they work, and it has a
REPL and examples that run without typing.

Second, Clojure programmers who will use cromulent or bendix, for
whom the native format *is* the interface. Third, us, debugging rule
sets, which is the user who keeps every technical panel honest.

Consequences:

- The **lessons** (section 2) are the spine of the page. Every
  technical panel of section 3 stays and is one click away, but none
  is the entry.
- Examples come to the user (section 4). Typing a term is optional.
- There are two surface syntaxes over one canonical form (section 5):
  the native tagged vectors, and a mathematical notation. The
  native mode comes first; *display* in the notation is early because a learner
  needs it from the first lesson; *input* in the notation is a later addition.

## 2. Lessons

Each lesson is one widget state, a paragraph of prose, a curated
example, and a "try another" that draws from the example bank
(section 4). Each maps to engine calls that exist today.

| # | lesson | what the learner sees | engine |
|---|---|---|---|
| 1 | A term is a tree | `2·x + y` as a tree; the same as `[:+ [:* 2 :x] :y]` | `eg/add`, classes and nodes one to one |
| 2 | Sharing | `(x + 1)·(x + 1)`: the two `x + 1` are one node, the tree has 7 nodes and the graph 4 | `eg/add`, `eg/node-count` |
| 3 | Equality and congruence | assert `a·2 = a<<1`; the classes merge; then `(a·2)/2` and `(a<<1)/2` merge on rebuild without being told (the egg README example) | `eg/union`, `eg/rebuild`, `:dirty?` before and after |
| 4 | A rule | one rule, one iteration: matches highlighted, then the nodes it adds and the classes it joins | `pat/ematch`, `embiggen {:iter-limit 1}` |
| 5 | Saturation | scrub the iterations; what each added, in colour; why it stopped | `:timeline? true`, `:stats`, `:stop-reason` |
| 6 | Extraction is taste | the same saturated graph under three costs gives three answers | `ex/extract` with `ast-size`, a shift-preferring cost, bendix `default-cost`, `no-D` |
| 7 | The blowup | a sum of five atoms under commutativity and associativity: 31 classes, 180 compound nodes plus the five atoms, and a counter that climbs as 3ⁿ; six atoms reach 608 nodes, and under a node limit of 500 they stop at it | `embiggen`, `:node-limit` (../design/ac-problem.md section 1) |
| 8 | The fix | the same sum under the polynomial analysis is one class; the polynomial sits beside it; every arrangement typed lands there | `bendix.core/saturate`, `eg/data g id :poly`, `poly/->term` (experiment 2) |
| 9 | A rule over the polynomial | `sin²x + cos²x` buried anywhere in a sum collapses to 1, however the sum is arranged | `rules/trig`, `normal-form-rule` (experiment 4) |
| 10 | What if | assert `x = 2` in a copy of the graph; watch what collapses; the original is untouched | persistence: the fork is a `let` |
| 11 | Differentiation is simplification | `d/dx sin(2x)` under the derivative rules with a cost that refuses `D` | `differentiate`, `no-D` |

Lessons 1 to 7 and 10 need cromulent only; 8, 9 and 11 need bendix,
ported in phase 2. All eleven are live.

Before them come two pages (2026-09-26; section 12, decisions 10
and 11), lesson data like the others but without a number, headed by
their titles alone and addressed by their keys, `#basics` and
`#intro`, each with the picture drawn unasked and no "surprise me".

**The basics**, *Many ways to write one thing*, "Start here" in the
navigation, is where the page opens. It is for a reader who does not
know what rewriting or simplifying is and has no identity by heart,
who may write code or may only like such things: six, half a dozen
and 2·3 are one number; `(x + 0)·1` is a long way to write `x`; a
rule is a shape and another shape that always means the same, and
the two it uses, adding nothing and times one, can be checked with a
number in hand; simplifying is finding a shorter way to write
something; an e-graph crosses nothing out, collects every way the
rules turn up and keeps the ways that mean the same in one box; and
at the end it chooses the shortest. The run is two iterations, five
classes becoming three, and its prose does without the words "term",
"node", "rewriting", "congruence" and "extraction". Its last link is
the introduction.

**The introduction**, *What is an e-graph?*, is the next page: what
an e-graph is and why it is interesting, in five short paragraphs
over one run, the example egg's paper opens with. `(a·2)/2` under four
rules: a rewriter that turns the product into a shift first is left
with `(a << 1)/2` and nothing to cancel, which is an alternative the
prose hands over; the e-graph keeps the shift beside the product,
saturates in four iterations to four classes and eight nodes, and
extraction reads off `a`. Its last paragraph links every lesson.

After them comes one more page (2026-09-26; section 12, decision
14), lesson data again and without a number: **the REPL's**, `#repl`,
"The REPL" at the end of the navigation. Its prose is the REPL's
documentation and its figures are lines of code, each a link that
evaluates it (`[:eval code]`); it has no fields, the editor being
the input; and its workbench is the editor beside every panel that
reads an e-graph, over the run of `a + a` under lesson 6's two rules
until the REPL puts something else on show.

## 3. What it shows

The panels, each mapped to what exists:

- **What each panel is the work of** (2026-09-26; section 12,
  decision 12). The inputs sit under the operation they are the
  arguments of (`orrery.lessons/operations`, a lesson naming one):
  the algorithm in a word, add, union then rebuild, union in a copy,
  saturate, simplify, differentiate; the function that is it, as the
  REPL under the page names it, `eg/add`, `eg/union · eg/rebuild`,
  `rw/saturate`, `bx/simplify`, `bx/differentiate`; what it does in
  a sentence; and the call (`lessons/call`), its arguments named as
  the fields are and the runner options in force written out one to
  a line, the lesson's under the alternative's. A field is headed by
  its argument's name and what that is, `term` the expression to
  start from, where it used to say "the term"; a problem still
  names the field in words. Beside the heading of every other panel
  is the function or the value it shows: the run its operation, or
  "from the REPL" for a run adopted there; best so far `ex/extract`,
  with the call and its three arguments said under the term, and the
  cost picker headed `cost`; the class list `g at this step`, and in
  lesson 10's fork `(first timeline)` beside it; the iterations
  `:stats`; the matches `pat/ematch`; the tree `term`.

- **The graph** (2026-09-26). Classes as boxes, nodes inside, an
  edge from each node's child slot to the class it points at, drawn
  by the page itself: `orrery.graph` lays it out and
  `orrery.views.graph` renders SVG. Layers by height with the leaves
  at the bottom, so every edge points down except one that closes a
  cycle, drawn back up and dashed; within a layer the boxes are
  ordered by the barycenter of their neighbours, ties by id; integer
  coordinates, the same counts on every runtime, positions that
  follow the ids. The picture carries the marks of the list, the
  input's class, the opened and the hovered class, what the step
  added or merged, where a rule matches, and a bendix class's
  polynomial in its head; hovering a box lights its row and clicking
  it opens the class, as with the tree. Off by default
  (2026-09-26: it takes the room), switched on from the tools row
  under the replay bar and kept on across lessons; zoom in and out
  or fit the width, and a filter to what the opened class reaches.
  The two pages before the lessons alone draw it unasked, their
  e-graphs being three to six boxes and their prose pointing at
  them; the switch is the learner's from the first touch, there as
  anywhere.
  Past a few dozen classes the picture is a wall, lesson 7's blowup
  on purpose, and the class list is primary. Why not the
  egraphs-good visualizer: section 9 and section 12, decision 9.
- **The export** (2026-09-26). The tools row copies or downloads the
  step on show as `egraph-serialize` JSON (`cromulent.export` through
  `orrery.derived/export-json`): every node costed under the cost in
  force, the input's class as the root, and on a bendix graph each
  class's kind as its type and its polynomial in the notation as
  class data, which that format's tools show inside the class.
- **The class list.** Every root, its nodes, its parents, its analysis
  data rendered. `eg/roots`, `eg/nodes`, `eg/eclass`, `eg/data`.
- **The opened class** (2026-09-26). A click on a class id, on a
  `#id` inside any node, on a node of the tree, or on a link in the
  prose opens the class in the replay bar, under the transport and
  stuck with it to the top of the viewport (moved there the same
  day: above the list it scrolled out of view as soon
  as you read down the classes; the list keeps the marks on its row
  and on the rows of its children and parents, and past 45% of the
  viewport the detail scrolls inside the bar), with what the engine
  knows about it (`orrery.eclass`): each node with the cost of the
  cheapest term it heads under the cost in force, the cheapest
  marked; how many terms the class stands for and the few cheapest
  (a k-best extraction, the bottom-up fixpoint of `best-costs`
  keeping k terms per class; the count is a walk with cycle
  detection, so `x = x + 0` reports infinitely many); the classes
  it points at and the nodes that point at it (between a union and
  the rebuild two parents that read the same sit in two classes,
  which is the broken invariant lesson 3 shows); and where it has
  been along the run, born, grew, classes became one, traced by
  which roots of each step the union-find of the step on show joins
  into it, since ids are not stable across a merge. Lesson 7's input
  class has thirty nodes and stands for 1680 terms, lesson 8's for
  37; opening the six-atom root takes 180 ms in Chromium.
- **The scrubber.** Iteration k of the timeline, forwards and
  backwards; the diff to k−1 highlighted: nodes added, classes
  merged. The diff is a set difference on `memo-entries`. The
  transport is a bar directly above the class list (2026-09-26:
  it landed too low on the screen under a tall input area),
  stuck to the top of the viewport while the list scrolls under it,
  with the label of the entry on show and its counts, and the opened
  class under it; the tiles stay in the run panel.
- **Best so far.** The extracted term at every iteration, under the
  cost in force. Watching it change is lineage-lite, and it is honest:
  the chain of rewrites from input to output is *explanations*, which
  cromulent lists as later; until then the page shows the term and
  the per-rule counts of the iteration that changed it.
- **The cost picker.** `ast-size`, bendix's `default-cost` and `no-D`,
  and one or two teaching costs (prefer shifts, prefer factored).
  Same graph, another extraction.
- **Per-rule stats.** Matches and applications per rule per iteration,
  phase times, and the backoff scheduler's bans, from `:stats`.
- **The polynomial.** For bendix graphs, the normal form beside each
  class, rendered by `poly/->term` and printed in the notation.
  This is the view no existing e-graph visualizer has.
- **The fork.** Assert an equality in a copy; the page holds both
  values and can show them side by side.
- **The REPL.** A Clojure REPL with the engine loaded, SCI embedded
  as a library with the compiled engine namespaces copied into its
  context: `(eg/add g [:+ :x 1])` at the REPL, the page rendering the
  value returned. This is what the native mode is. Every lesson has
  the REPL under it, and the REPL has a page of its own (section
  2; section 12, decision 14), the editor on the left, staying in
  view with its history scrolling inside the panel, and every panel
  on the right. In scope (`orrery.names`, one table that the prelude
  is made from and the page prints): `g` and `timeline`, bound anew
  before every evaluation; `state`, the page's own atom; `show!` and
  `push!`, which put a value on show and make an e-graph the next
  step of the run on show; `*1`, `*2`, `*3` and `*e`; `doc`, `dir`,
  `apropos` and `find-doc`; and twenty-one namespaces under short
  names, `wb` the steps of the page's state among them. A value is
  shown by what it is, an e-graph as a class list, a `[g id]` pair
  with the class marked and kept as the class to extract for, a
  runner's result or a run in a line with a button, and anything
  else printed, abridged (`orrery.printed`): an e-graph inside a
  value as its counts, a run as its steps, a collection cut at 48
  elements, which is what lets `(range)` print, and the state of
  the page in a few lines, since a swap of `state` returns it. The
  value is printed while its evaluation still holds, so what a lazy
  sequence prints when walked stands over it and what it throws is
  the evaluation's error, not the next render's. The up arrow in the
  editor's first line recalls the inputs, as a terminal does.

## 4. Proactive examples

The page offers examples; the learner need not type mathematics.
Two sources.

**The curriculum**: a curated example per lesson, seeded from what
the engine already knows to be interesting: the egg README example,
the sum-of-*n* fixture, the twenty-four rows of experiment 5's
textbook workload (`bendix` `bench/`), and the tables in the test
suites.

**The generator** (`orrery.generate`, built 2026-09-26): random
terms over the small signatures bendix's and cromulent's property
tests draw from (`term-gen`, `trig-term-gen`, `cromulent.gen`),
without test.check: a page has no use for shrinking, and one seed
must draw the same term on the JVM, on Jolt and in the browser, so
the stream is Park–Miller's generator (products under 2^53, exact
everywhere) and every choice indexes a vector, never a set. A
signature is leaves and shapes; a shape is an operator with slots: a
subterm, the subterm before it repeated (`a + a`) or reversed
(`a·b + b·a`, one polynomial and two nodes), or a constant from a
list; a term comes to share by reusing a subterm already drawn. Each
lesson names its draw: any term for lesson 1, a sharing one for 2,
two sides and a wrapper for 3, a term under the rules in force for 4
to 6, a random order and bracketing of three to five atoms for 7, a
ring term for 8, a context with sin²u and cos²u planted at two of its
leaves for 9, a small term of a variable planted beside the same
term of what it might equal for 10, a function of the variable in
force for 11. A candidate is run to the end and **scored**
(`orrery.score`) by features the engine reports, each a raw value
and a score in [0, 1]:

| feature                  | from                                | high means                                         |
|--------------------------|-------------------------------------|----------------------------------------------------|
| merges per node          | `:stats` `:applied`, class count    | the rules did something                            |
| shrink ratio             | input size over extracted size      | simplification is visible                          |
| node growth ratio        | node count per iteration            | the blowup is visible                              |
| analysis-only merges     | classes merged with no rule applied | the polynomial did the work                        |
| a normal-form rule fired | `:applied` by name                  | lesson 9 material                                  |
| iterations to saturate   | `:iterations`                       | a scrubbable story, neither one step nor a wall    |
| stop reason              | `:stop-reason`                      | `:saturated` for every draw; `:node-limit` stays lesson 7's alternative |

and, for the lessons that are scripts, tree nodes per graph node
(sharing), classes merged by congruence at the rebuild step, and the
tree's size (a bell over five to eleven nodes); for lesson 6, how
many distinct answers the lesson's costs give; for lesson 11,
whether the answer is free of `D`. A lesson names the features it
wants as weights (`:surprise :wants`), and its score is their
weighted mean. "Surprise me" draws a dozen candidates over the rules
and options in force, keeps one per shape of result (classes, nodes,
iterations, stop reason) so the bank is not twelve spellings of one
thing, picks one at random weighted by the cube of its score, runs it
on the page, and says what it did: *drawn from 12 candidates, 8
shapes of result; score 0.68: forms the analysis already had 2 (0.3)
· stop reason saturated (1.0) · shrink 3.7 (0.7) · tree nodes 11
(1.0)*. Interestingness is our score and nothing deeper; it is
tunable and it is small code. Counts, iterations, stop reasons and
costs are the same on every runtime; a feature that reads a best
term can differ where a tie falls differently, which only moves a
pick. The bank is drawn live in the browser, well under a second for
every lesson; the slowest dozen (lesson 9, on bendix) takes a tenth
of a second on the JVM.

## 5. Two modes over one canonical form

The canonical data is bendix's: tagged vectors, keyword operators,
keyword variables, exact numbers (../bendix/IDEA.md section 1), read
by the EDN reader, which every runtime has (`orrery.input`). The
**native mode** is that format at a REPL and in every panel.

The **notation mode** prints the same data as mathematics: `2·x + y`,
`sin²x`, `x^(n+1)`, `d/dx`. bendix's design already lists infix
printers and parsers as compilers to and from the canonical form,
none on its critical path. Here the printer is on the critical path,
because a learner meets lesson 1 in the notation; it is small
(precedence, parentheses, a few special forms: `orrery.notation`, built
2026-09-25) and prints nesting faithfully, `a + (b + c)` against
`a + b + c`, so an arrangement of a sum stays visible. The
**parser**, notation input, is `orrery.parse` (built 2026-09-26, phase
3): a Pratt parser over the printer's precedence table, so what the
page prints reads back as the term it printed, with typed spellings
beside the printed ones (`*` for `·`, `-` for `−`, `^` or `**` for a
superscript, `pi` for `π`, `->` for `→`), `?x` for a pattern variable,
`f(a, b)` for any operator, and rules one per line as `name: pattern
-> replacement`. Every input takes either spelling: text that starts
with a vector or a keyword is native, anything else is notation; the
fields show the values in the print mode in force and follow the
toggle while unedited. Section 12, decision 7, says what a numeral is.

## 6. Architecture

Decided 2026-09-25, replacing the first draft's scittle route.

- **Compiled ClojureScript.** shadow-cljs compiles cromulent's `.cljc`
  (unchanged: the files the JVM and Jolt suites test, on the classpath
  through a git dep, or `:local/root` under the `:local` alias) and orrery's own namespaces into one bundle
  that `public/index.html` loads. The first draft loaded the source
  through scittle with no build step; the compiled page was named
  as the end state, and the port is the same either way, so the
  plan went there directly. An interpreter is two orders of magnitude
  off the JVM where the compiler is a small multiple: the self-test's
  29 facts run in about 40 ms in Chromium and 54 ms on node.
- **SCI as a library, for the REPL only.** The one thing an
  interpreter is needed for. `orrery.repl` holds the SCI context
  with the engine namespaces copied in; nothing else may know it is
  there. It is handed a string and the function that prints a value,
  and hands back the value, its text and what was printed.
- **No React.** The page is Replicant: one hiccup tree computed from
  one atom (`orrery.state`), rendered on every change. Views are
  functions of values (`orrery.views.*`); only `orrery.views.lesson`
  reads the state, so the same class list serves the fork. **Every
  handler is data** (2026-09-26): `{:on {:click
  [:select id]}}` over the vocabulary of `orrery.actions`, routed by
  one dispatch function (`orrery.dispatch`, registered with
  `replicant.dom/set-dispatch!`), which is the only place a DOM event
  is read. Replicant compares handlers by value and leaves an
  unchanged one alone, where a closure was re-registered on every
  render; and a view with no functions in it builds anywhere, so the
  views are `.cljc`, what they show is computed by `orrery.derived`
  (`.cljc`, the memoized derived values; `orrery.state` keeps the
  atom and the actions), and `orrery.page-test` builds the whole page
  on the JVM and Jolt for every lesson at every step, in both print
  modes, with the input's class opened and a REPL history, checking
  every handler against the table and that no function is in the
  tree.
- **The state is a value, and the atom takes no other**
  (2026-09-26; section 12, decision 14). `orrery.workbench`, `.cljc`,
  is the page's state and the steps over it that need no browser:
  `initial`, `open` a lesson, `start` its run, `show` a run,
  `advance` and `scrub`, `put` and `push` for the values of the
  REPL, `recall` for the editor, and `page`, the constructor the
  suites build every page with. `orrery.state` keeps the atom, the
  timers and what reads the DOM's clock, and swaps those steps in.
  The atom has a validator, `workbench/problem`, which says in words
  what is wrong with a value that is not a state; and the run loop
  follows the value, not the action: the page's one watch calls
  `state/changed!` with the state before and after, which drops the
  derived values of the last run and steps a run still running when
  the run id is another, and then renders. So `(swap! state wb/show
  run)` at the REPL is everything a button does.
- **The run is the data model.** `orrery.run`: `{:timeline [g0 g1 …]
  :labels :stats :stop-reason :status :root}`, produced by a script of
  engine calls (lessons 1, 2, 3, 10) or by stepping `rw/embiggen` one
  iteration per timer tick (lessons 4 to 7), so the page repaints
  between iterations, the counters climb, and a stop button works
  between iterations. The e-graph values are live: the scrubber
  indexes the timeline, `orrery.diff` is a set difference between two
  entries, and extraction under another cost is another call on the
  same value. There is no trace format and no exporter; a recorded
  trace is needed only when the visualizer's `egraph-serialize` JSON
  or a golden file is.
- **Pure namespaces on three runtimes.** `orrery.derived`, `orrery.actions`,
  the views (above), `orrery.notation` (the printer),
  `orrery.diff`, `orrery.costs`, `orrery.input` (the EDN reader),
  `orrery.run`, `orrery.lessons` (prose as hiccup data, decided over
  markdown: no renderer to ship, inline widgets that are the prose's
  figures, `[:notation t]`, `[:native t]`, `[:step k label]`,
  `[:select t label k?]`, `[:cost key label]`, `[:alternative label
  text]`, `[:print mode label]`, and the tests walk them: every step
  is within the curated run, every term a class of it, every cost
  and alternative the lesson's, every citation a work of
  `lessons/reading`, which the reading line under each lesson is
  derived from), `orrery.eclass`
  (the opened class, section 3), `orrery.generate`,
  `orrery.score` (section 4) and `orrery.expect`
  are `.cljc`, tested by `clojure -M:test` and `jolt -M:test`, and
  compiled into the page. Runner code (`orrery.state`, the views,
  `orrery.app`) is `.cljs`.
- **Verification, one table four ways.** `cromulent.smoke` (28 facts),
  `bendix.smoke` (25) and `orrery.expect` (the lessons' counts,
  iterations, stop reasons and costs, and each lesson's values read
  back from their notation) are asserted by the JVM and Jolt suites, by
  `npm run smoke` on node, by the page's self-test tile in the browser, and by
  the Playwright suite in `test/e2e` (`npm run e2e`), which builds a
  release, serves it, and drives every live lesson, its alternatives
  and inputs, the transport and the REPL through the DOM. Never ids,
  never a tied term (section 8).

## 7. Plan, in phases, each with a deliverable

**Phase 1, the port and the live page** (delivered 2026-09-25).
cromulent as `.cljc` behind `cromulent.platform` (section 8) and its
runner as start/step/finish; the third runtime as a command; orrery's
pure namespaces and their tests on both runtimes; every cromulent
lesson live: 1 and 2 with the tree, 3 with the dirty badge and the
congruence merge at the rebuild step, 4 with the matches highlighted,
5 with the stats table and backoff's bans, 6 with the cost picker, 7
with the counters climbing, 10 with the original beside the copy;
learner-typed terms, patterns and rules with readable errors; the
REPL panel; the end-to-end check. What remains of the phase is
polish as the lessons get used: prose, the picture at scale, the
"surprise me" bank once bendix is live.

**Phase 2, bendix live** (delivered 2026-09-25). `bendix.num`, the
numeric layer (section 8), and bendix's five namespaces as `.cljc`
with no other change than routing every coefficient operation
through it; `bendix.smoke`, its facts on the three runtimes; lessons
8, 9 and 11 live, with the class list carrying each class's
polynomial, bendix's two costs in the picker, and a run that ends
with a materialization step so extraction can choose a normal form.
The generator and scorer followed on 2026-09-26 (section 4).

**Phase 3, notation input and the prose** (delivered 2026-09-26).
The parser (`orrery.parse`, the inputs reading either spelling and
showing the mode in force, the printer made faithful to it, the round
trip over every lesson's values and thirty seeds of every draw); then
the pass on the prose in the explorable-explanation voice, with the
widgets as its figures (section 12, decision 8), and the opened
class the prose points at (section 3).

**Phase 4, the picture and the exporter** (delivered 2026-09-26).
`cromulent.export`, the `egraph-serialize` JSON with a printer of its
own (section 12, decision 2); bendix's `class-data` and `serialize`
over it; the page's copy and download of the step on show; and the
graph drawn by the page, off by default (section 3; section 12,
decision 9).

**Later.** Per-rule timings at scale; static deployment.

## 8. Portability ledger

Verified against the source on the JVM, on Jolt v0.8.12 and under the
ClojureScript compiler, 2026-09-25. The port was four edits and a
rename, and cost nothing measurable on the primary runtimes.

| item | decision |
|---|---|
| packed hashcons keys | one layout for all three runtimes, `op·2^42 + a·2^21 + b`, under 2^53: 2 048 operators and 2 097 150 class ids per e-graph, both overflows throwing; bench medians unchanged within noise on both runtimes (../cromulent/IDEA.md section 5, decision 2). Retiring the key altogether is a separate, benchmarked question (../cromulent/IDEA.md section 12) |
| register and match arrays | `long-array`, `aset`, `aget`, `long` compile unchanged under ClojureScript; the hints are ignored |
| the clock | `cromulent.platform/now-ms`, the library's one conditional file |
| backoff arithmetic | doubling by multiplication, on all runtimes |
| exceptions | `(catch #?(:clj Exception :default :default) e …)`, twice in `cromulent.rewrite` |
| regexes | `re-matches` in ClojureScript only checks that the regex's first match is the whole string, so a lazy group stops short where Java's `matches()` would extend it: a pattern meant for the whole line is anchored `^…$` (`orrery.parse`, 2026-09-26, found by the browser suite after the JVM and Jolt suites passed). `re-find` against a `^`-anchored pattern behaves the same on both |
| determinism | class and node counts, iterations, stop reasons, per-rule counts and costs are identical on the three runtimes (`cromulent.smoke`). Root ids and tied extractions are stable per runtime only: hash iteration order differs, and the tie falls to child ids. Every arrangement of the five-atom sum costs 9, so lesson 7 asserts the cost, not the spelling |
| bendix numerics | `bendix.num` (2026-09-25): on the JVM and Jolt the operations are `+'`, `*'` and `/` with bignum promotion; in ClojureScript an integer is a number within 2^53 (past it, the operation throws) and a non-integer rational is a `Ratio` deftype, normalized, hashed and compared by value, printed as `1/2`, never zero and never an integer, so `zero?`, `integer?` and `=` against small literals keep their meaning on plain numbers. Only the sites that add, multiply, divide or compare a coefficient call it; exponents are plain integers. `bendix.num/read-string` is the EDN reader with ratios exact everywhere: ClojureScript's reader turns `1/2` into `0.5`, so the text is scanned for ratio tokens first and each becomes a tagged literal. The one ratio literal in bendix's source, `65/64` in the default cost, is written as a division because the ClojureScript compiler has no ratio constant; it is the exact double 1.015625 there, so costs stay exact and comparable, and the page prints a cost that is a multiple of 1/64 as `k/64`. `cromulent.term/compare-nodes` ranks a `Ratio` leaf after vectors in ClojureScript and among numbers on the JVM, which only moves a tie |
| performance | compiled: 29 self-test facts in about 40 ms in Chromium; the sum of six atoms (608 nodes, 8 iterations) well under a second; the AC-10 yardstick is still not a browser workload |

## 9. Relationship to existing tools

- **egraph-visualizer** (egraphs-good): a picture of one serialized
  e-graph, or of a list of them with a slider. Read from its source
  on 2026-09-26, version 2.3.0: React 18, React Flow and elkjs in a
  worker, not the Cytoscape of its first version; 3.3 MB minified
  and 968 KB gzipped plus 260 KB of CSS, against the 1.27 MB of the
  whole orrery page; `mount(el)` returns `render(jsons)` and
  `unmount()` and nothing else, no event out and no state in, so a
  click in it cannot open a class and the opened class cannot light
  in it; it ignores `cost` and `root_eclasses`. Its two features
  worth wanting, the filter to what a selection reaches and
  positions that hold across a history, the page's own picture has.
  Weighed and not taken (section 12, decision 9).
- **egraph-serialize**: the JSON format between e-graph libraries
  and their tools. cromulent emits it (`cromulent.export`), and the
  page copies or downloads the step on show, so any graph here opens
  in that visualizer's demo site, in egg's tools or in a notebook.
- **egglog-demo**: a text box and a run button over egglog. No
  timeline, no diff, no cost picker, no analysis data, no fork,
  because a mutable engine does not keep the states.
- **egg's dot output**: one graph to graphviz.

What orrery adds is exactly what the persistent value exports: the
timeline and its diffs, extraction under several costs from one
graph, analysis data per class, forks, and a real REPL, arranged as
lessons.

## 10. Honest utility assessment

Educational, and that is the stated purpose: the understanding should
belong to more people than the two of us. It is also the first
artifact that shows what a persistent e-graph can export that a
mutable one cannot, and the only place the polynomial analysis is
visible as a picture. It is not a performance demonstration and
proves nothing about speed.

## 11. Naming

**orrery** (decided 2026-09-25): a clockwork model of
the solar system with a crank; you turn it to watch the mechanism
run, which is what the page is. The rule applied: the name should say
what the thing is, be one word, be clear on Clojars and among Clojure
repositories on GitHub, and not collide with a known tool in the same
space. Checked 2026-09-25:

| candidate | Clojars | Clojure repos on GitHub | elsewhere on GitHub |
|---|---|---|---|
| **orrery** | clear | none | 841 repos, top a 156-star TypeScript project |
| zoetrope | clear | one, zero stars | a 947-star Rust project |
| diorama | clear | none | nothing notable |
| terrarium | clear | one, zero stars | nothing notable |
| flipbook | clear | one, 50 stars | 3 800 repos |
| vivarium | clear | one, one star | a 426-star C project |
| loupe | clear | one, zero stars | a 3 200-star JavaScript event-loop *visualizer*, the collision that retired it |

Not chosen: loupe (the first working name, retired by the
visualizer collision), zoetrope (frames into an illusion of
motion, where these states are real), diorama (a scene, not a
mechanism), terrarium (growth, not a mechanism), flipbook (generic),
vivarium.

**The notation, not "lay" (2026-09-25).** The mode that prints terms
as mathematics was the *lay* mode, for the layperson; it is now the
*notation*, `orrery.notation`, because a name should say what the
view is and not who it is for, and the reader it was named for is
the one the page most wants to welcome. The other mode stays
*native*.

## 12. Open questions

1. **The packed-key layout.** Decided 2026-09-25: one layout, under
   2^53, by the bench (section 8).
2. **Where the exporter lives.** Decided 2026-09-26: `cromulent.export`,
   `serialize` to the format's data with string keys and `json` to
   its text, with a JSON printer of its own, thirty lines, so the
   same text comes out on the JVM, on Jolt and in ClojureScript (one
   smoke fact pins it) and no runtime prints a ratio; a node is
   named `class.i` in `compare-nodes` order and a child is the first
   node of its class, as egg names them; the cost is the cheapest
   term the node heads, when a number. The bendix counterpart is
   `bendix.core/class-data` and `serialize`, each class's kind as its
   type and its polynomial as a term. The page composes its own over
   the cost in force and the notation (section 3, the export).
3. **Where lesson prose lives.** Decided: hiccup data in
   `orrery.lessons` (section 6).
4. **The picture at scale.** Decided: the class list is primary. The
   picture came 2026-09-26, drawn by the page and off by default
   (decision 9); at lesson 7's thirty-one classes it fits the width
   as a wall and zooms, which is the lesson.
5. **Notation display in phase 1.** Decided yes: `orrery.notation`.
6. **The runner as start/step/finish.** Done 2026-09-25:
   `cromulent.rewrite/start`, `step` and `finish`, with `embiggen` as
   the loop over them, so a run stepped by the page from a timer is
   exactly the run one call makes, bans and all (lesson 5).
7. **What a numeral is, in the notation.** Decided 2026-09-26.
   Mathematically `1/2` and `[:/ 1 2]` are the same number; in the
   e-graph they are not the same term. The ratio is one leaf in one
   class; the quotient is a node over two more classes; cromulent
   folds no constants unless a rule says so; a pattern `[:/ ?x 2]`
   matches the node and not the leaf; and the counts and costs the
   page shows differ. bendix's analysis folds `[:/ p k]` for a
   constant `k`, so there the two classes carry one polynomial and
   merge at the next rebuild. Neither engine is wrong either way;
   what matters is that the learner's text denotes one term,
   predictably, and that the page's own printing reads back. So the
   parser is a spelling, not an evaluator (`2·3` is a product node),
   and a numeral's spelling absorbs a leading minus and a slash
   between integers, as the EDN reader's does and as bendix writes
   its coefficients (`[:* 1/2 :x]`, `[:* -1 :x]`): `1/2` and `-3` are
   numbers, `1/(2)` and `-(3)` are nodes, `-3·x` is the coefficient
   −3 times x, `-3²` the negation of a power, `1/2³` one over a
   power. The printer prints the nodes apart from the numbers
   (`1/(2)`, `−(3)`, `(−2)²`) and parenthesizes whatever a negation is
   over, `−(a·b)`, so the round trip holds. `a + b + c` reads as the
   left-nested chain; bendix's n-ary sums print the same and read
   back nested, which its polynomial cannot tell apart. Unary minus
   reads as `:neg`, the generator's spelling; `[:- x]` prints the same
   and is accepted. Pattern variables are `?x` in both spellings,
   as `cromulent.pattern/variable?` already had them.
8. **The prose voice, and credit.** Decided 2026-09-26. Each
   paragraph says what is on the page now and hands the reader one
   thing to pull: a step, a class to open, a cost, an alternative,
   the print mode. Those links are data (section 6) and the JVM and
   Jolt suites check every one against the curated run, so a number
   or a claim in the prose is a fact of the run on show; a class link
   whose term the edited input no longer holds renders as plain text
   rather than a dead link. Every idea is cited where it appears, as
   a superscript author-year link to the paper (`[:cite key …]` over
   `lessons/reading`: the e-graph to Nelson's report, congruence
   closure to Nelson and Oppen and to Downey, Sethi and Tarjan, the
   hashcons to Ershov and Goto, e-matching to Detlefs, Nelson and
   Saxe and to de Moura and Bjørner, equality saturation and
   extraction to Tate et al. and to egg, the rebuild, the scheduler
   and the analyses to egg, AC congruence closure to Bachmair, Tiwari
   and Vigneron, the analysis that decides to Nelson and Oppen's
   cooperating procedures and Zucker's e-graphs modulo theories,
   persistence to Driscoll et al., Bagwell and Hickey), and the
   lesson's reading line lists the works it cites, linked. The prose
   itself stays on what is happening; it cites, it does not recount
   (2026-09-26: cite the authors, footnote the papers,
   no history lesson). Links are DOIs where one exists, each checked
   against CrossRef.
9. **The picture: the egraphs-good visualizer, or the page's own.**
   Decided 2026-09-26, the page's own (section 3). The question
   was whether the visualizer still made sense beside the tree panel and
   which of its interactions were worth having. The tree draws a
   term, not the graph, and only lessons 1 and 2 show it; the graph
   itself had no picture, a class with several nodes, a merge and a
   cycle being text in the list and the opened class. The visualizer
   was read from source (section 9): its two interactions worth
   wanting are the filter to what a selection reaches and positions
   that hold across a history, and both fall out of a layout the
   page owns, since the opened class already knows its children and
   the layers and the order are functions of the value. Against
   embedding it: React in the page, which is ruled out
   (section 6); three times the download for a panel the design
   already limits to small graphs; and no way in or out of it, so
   the hover, the opened class, the cost picker and the diff would
   all have stopped at its edge. The layout is a layered one of a
   hundred and fifty lines, `orrery.graph`, tested like everything
   else on the JVM and Jolt; dagre (29 KB gzipped) is the fallback
   if it ever falls short, elkjs (470 KB) the one after. The exporter
   was wanted either way and is independent of the picture.
10. **Where the page opens.** Decided 2026-09-26: on an
    introduction. It opened on lesson 7, the blowup, arbitrarily,
    and a reader wants a page that says succinctly what an
    e-graph is and why it is interesting, to ground the reader
    before the lessons. The introduction is
    lesson data, not a page of another kind: a static page would have
    been the one place where the picture is a cartoon, and as a
    lesson it gets the widgets, the checks of every suite and the
    instrument for nothing. It has no number (it was 0 for a few
    hours, until decision 11 put a page before it): `#intro` names
    it, by `lessons/address`, and `lessons/heading` prints its title
    alone. Its example is
    the phase-ordering one, because it shows the reason for an
    e-graph before the mechanism. Two things it does that a lesson
    does not. It draws the picture unasked: `:graph` in its panels,
    read by `derived/graph?` until the learner touches the switch
    (`:graph?` is nil until then), so decision 9's default stands
    everywhere else. And it offers no "surprise me": a reader being
    grounded wants one example, and the draws are per lesson. One
    widget came with it, `[:lesson key label]`, a link to a lesson.
    Its own example is also its first alternative, so the prose can
    bring the reader back from the dead end.
11. **A page more basic than the introduction, and the page opens
    there.** Decided 2026-09-26. The introduction was
    a good start, but it assumes a reader who knows what rewriting
    and simplifying are; there should be another for one who does
    not, who knows no identity off the top of their head, who may
    write code or may just like nerdy things, and the introduction
    stays. So the basics come first and the introduction second, and
    `lessons/start` is the basics: a reader who knows more is one
    click from the next page, and a reader who knows less has
    nowhere else to begin. What it may assume is that a letter can
    stand for a number. Its rules are the two anyone can check with
    a number in hand; its words are the reader's, a way of writing,
    a box, crossing out, with "class", "rule", "simplifying" and
    "saturating" each introduced where the page shows the thing; it
    says that `#3` points at a box, since every panel writes nodes
    that way, and why the opened box claims infinitely many, since
    the panel will say so. It does not say why collecting beats
    crossing out: that is the introduction's example, and its last
    link. The navigation calls it "Start here" (`:nav`), its heading
    says what it is about. Two pages without numbers wanted
    addresses, so a lesson's address is its number or, without one,
    its key (`lessons/address`, `by-address`), and a hash that names
    nothing, `#0` now among them, falls to the basics.
12. **What the panels are called.** Decided 2026-09-26.
    The panels want better labels, saying which algorithm or
    function they belong to, simplify as against saturate, so that
    the arguments have a context, where they said only "the term"
    and "the rules". The inputs were never a form; they are the
    arguments of a call, and the page now says which (section 3).
    The words come first and the function second, because the first
    reader is the basics' and the second is the one who will type
    the call at the REPL. The call is schematic, not text to paste:
    at the REPL `term` and `rules` are namespaces, and a rule is a
    map; what it promises is the function, the order of its
    arguments and the options in force. The page does not call
    `rw/saturate` or `bx/simplify`, it steps what they are made of,
    `start`, `step` and `finish`, and for bendix the e-graph, the
    runner, `materialize-all` and the extraction, so the label names
    the function whose run it is, which section 12's decision 6
    made the same run. The options shown are the lesson's under the
    alternative's, not the page's defaults: every lesson names its
    scheduler, the one default of `rw/saturate` the page does not
    share.
13. **The site says what it is, and how it was written.** Decided
    2026-09-26. Before the prose starts linking into
    the running widgets, the site has to say what it is, an
    interactive tool for exploration and education, the author's
    education included, that embeds an immutable persistent e-graph
    library and a nascent CAS with interactive widgets for
    understanding their behaviour; and it has to be clear that it is
    largely written using LLMs. So `orrery.lessons/about`, three
    short paragraphs with nothing in them to pull, stands over the
    heading of the page the site opens on (`views.lesson/about-view`,
    on `lessons/start` only), and `lessons/colophon` says the same
    in a line under every page, with a link back, because a reader
    who arrives at a lesson by its address would otherwise never be
    told. The sentence about LLMs is the author's, about orrery, and
    stands alone as its own paragraph; it claims nothing about how
    cromulent and bendix were written. The statement names the two
    libraries without linking them, since neither has a public
    address yet. Its last line hands the reader who knew none of its
    words to the basics under it.

14. **A page for the REPL, and the state as a value.** Decided
    2026-09-26. The REPL wants documentation, and
    perhaps a page of its own with every widget that applies to an
    e-graph, some atoms for the state, and the lesson's state behind
    a constructor if that would let it be used again. It would, in
    three places. The suites built their pages from a map written
    out by hand beside the atom's; the REPL's page needed the same
    state over a run no lesson made; and the REPL, given the atom,
    needed the steps the buttons take as functions it could swap in.
    So the state is `orrery.workbench` (section 6), and the atom is
    in scope as `state`. Two things make that safe to hand over. The
    atom refuses what is not a state and says why, since the page
    renders on every swap and a step past the timeline or a cost
    that does not exist would otherwise be an exception in the
    render, with the page gone. And stepping follows the value, so a
    run put on show with a swap runs like any other; the tick
    counter that the actions kept is gone, the run id doing its
    work. The page is lesson data, as decision 10 made the
    introduction, and for its reasons: it gets the navigation, the
    address, the suites and every panel for nothing. What it does
    that a lesson does not is say `:layout :beside` and have no
    inputs. Its documentation is prose in the explorable voice, and
    its figures are `[:eval code]`, a line the reader evaluates by
    clicking it; the JVM and Jolt suites check that every such line
    closes its brackets and names only namespaces in scope, and the
    browser suite clicks each in reading order and reads what came
    back, so a line in the documentation cannot rot. The lines are
    written to work in any order, which is why the union is a `let`
    over two `eg/add`s and not two lookups. The panel's element is
    `#repl-panel`, not `#repl`: that is the page's address now, and
    a browser scrolls to the element an address names. `show!` and
    `push!` are functions of `orrery.state`, compiled like the rest,
    which hands their vars to `orrery.repl` to intern with what they
    say of themselves; `orrery.repl` cannot require them, being
    required by the namespace that holds them. They were text for a
    few hours, evaluated by the REPL at load, which no editor could
    check, no build would refuse and no suite but the browser's
    could reach, and which said a second time what the button beside
    a value already did.
15. **What the editor is made of.** Decided 2026-09-27:
    `prism-code-editor`, after the survey below (2026-09-26), when
    the editor was a textarea. The question was what
    exists that would make it better to type in, light if possible,
    not built from CodeMirror by hand, with a preference for
    Clojure's own ergonomics of evaluation. Anything on React is out
    (section 6):
    Portal, Clerk's viewers, re-console, replete-web, Maria's editor
    and 4ever-clojure are applications on it, not libraries. Two
    candidates remain, both run in a scratch release build with SCI
    and no React. `nextjournal/clojure-mode` on CodeMirror 6 is the
    only one with structural editing, a syntax tree and the form at
    the cursor as shipped; it is a git dependency with nine npm
    packages installed by hand, its `eval-region` finds and
    highlights the form but evaluates nothing, the keys being ten
    lines of the host's, and it cost 481 KB minified, 158 KB
    gzipped, over a build that already had SCI, nearly all of it
    CodeMirror, which would take this page from 342 KB gzipped to
    about 500 unless it were a module loaded when the editor is
    first used. `prism-code-editor` lays highlighted code over a
    real textarea: highlighting, matched and coloured brackets,
    closing brackets, undo, and its bracket matcher hands the host
    every pair with its depth, from which the form at the caret and
    the top-level form are twenty-five lines; no structural editing,
    and its indentation knows brackets, not Clojure. It cost 40 KB
    minified, 16 KB gzipped, in the same build. Beside the editor:
    `me.flowthing/pp` would lay a printed value out over lines for 2
    KB gzipped; `sci.nrepl`'s completions run in process for 1.3 KB;
    and the same library connects an editor to the page's own SCI
    context through a babashka relay, as a development preload that
    a release leaves out. Dataspex renders with Replicant and can
    follow an atom, but in the page it takes the global dispatch and
    styles `html` and `body`; it is a tool for beside the page, not
    in it. The choice was prism-code-editor, to be built on in
    steps: first the swap with the page's behaviour kept, then the
    handling of lines and evaluation at the caret, then the REPL
    page's layout, then its documentation, each settling what the
    next describes. In the first step the page's state keeps the
    editor's text; `orrery.editor` builds the editor from a
    life-cycle hook on an empty element, which Replicant leaves
    alone, and is given callbacks by `orrery.dispatch`. Ctrl-Enter
    and Cmd-Enter evaluate; the arrows recall in the first and last
    lines; Tab indents the lines with spaces, not tab characters,
    and Ctrl-M lets Tab leave the editor; brackets and
    double quotes close, a single quote does not; the colours are
    the page's variables, so the editor follows the light and dark
    themes; line numbers are a checkbox beside the buttons, off by
    default and kept across pages (`[:ui :line-numbers?]`), since
    some who read an example may want them.
16. **What the place to type is called.** Decided 2026-09-26: the
    editor. It was the prompt, in the page's prose and in the code.
    To the general public that word now means what is
    typed to a language model, so the page wants one that is
    distinct. The editor is where a line is typed; what happens
    there happens "at the REPL"; the `user=>` that heads a line of
    the history stays, being the REPL's own mark and not the word.
    The textarea says so to a screen reader (`aria-label`), and the
    browser suite checks that the REPL's page does not say "prompt".
17. **A lesson's layout, and the REPL in a dock.** Decided
    2026-09-27. A lesson did not feel responsive,
    and the REPL should stay in view wherever the page is scrolled.
    Screenshots at 390, 800, 1280 and 1600 pixels showed why. On a
    wide screen a link in the prose opened a class in a table below
    the fold. The page stopped at 1100 pixels, and under the short
    class list of the right column the space stood empty. On a
    narrow screen the e-graph came after the whole form. The REPL
    was the last thing on the page. Now a lesson is three parts:
    the text, the e-graph and the controls. From 1100 pixels the
    text and the controls are the left column, which scrolls with
    the page. The e-graph is the right column, which sticks to the
    top of the viewport and scrolls inside itself, so what a link
    does happens in view. Under 1100 pixels the parts stack in the
    order text, e-graph, controls. The page is 1500 pixels at most.
    The fork's two class lists stand side by side when the column
    is 900 pixels wide or more, which a container query decides.
    The REPL is a dock along the bottom of every lesson. A dock at
    the top would fight the sticky replay bar, and the bottom
    won over a drawer at the side. It starts closed,
    as one bar that says what was evaluated last. Open, it holds
    the REPL panel under the bar and resizes from its top edge.
    Ctrl-` opens and closes it. Whether it is open is kept across
    lessons (`[:ui :repl-open?]`), as the graph switch is. An
    evaluation opens it, so code that opens a lesson keeps the REPL
    in view. Its height is the style variable `--dock-h`, which the
    page's padding and the e-graph column read, so nothing is hidden
    under it. The panels moved out of `orrery.views.lesson` into
    `orrery.views.panels`, public and reading no lesson, so a page
    that is not a lesson can arrange them. A later page will arrange
    them for playing with e-graphs rather than learning, once this
    layout has settled.
18. **One dock on every page, an editor of forms.** Decided
    2026-09-27. This is steps two to four of decision 15, done as one
    piece of work. The aim was an editor that is always there:
    one line when the dock is closed, a full editor when it is open,
    the docs in a popover rather than in prose, and no button that
    names the REPL. The dock is on every page now, the REPL's page
    included, and that page opens with it open (`:dock :open` in its
    lesson data, read by `workbench/open`). The side-by-side layout of
    the REPL's page is gone, and the page is laid out as a lesson is.
    The REPL has two editors, each with its own text. At first it had
    one text for both, which an evaluation in the closed dock emptied.
    Now it has two, so the buffer feels like an editor and
    cannot be emptied by accident. The line is the REPL's input.
    Closed, the dock is the line, what was evaluated last, a ? button
    and a button that opens the dock. Open, the dock is the buffer on
    the left, and the history over the line on the right, under a row
    of buttons. The history is on the right, where the
    page may later show traces of what its links and buttons run.
    Enter evaluates the line when its brackets are complete, as
    rebel-readline does, and empties it. When a bracket is open, Enter
    starts a new line and the line grows. Ctrl-Enter evaluates the
    line whatever it holds, and the arrows recall. Nothing the REPL
    does empties the buffer. In the buffer, Enter starts a new line
    indented as Clojure is, Ctrl-Enter evaluates the top-level form at
    the caret, and Ctrl-Shift-Enter evaluates every form in order and
    stops at the first error. Each form is its own entry in the
    history. Each entry has a "to buffer" button that adds its code to
    the end of the buffer (`workbench/to-buffer`). Opening the dock
    puts the caret in the buffer, and closing it puts the caret on the
    line. `orrery.forms` (.cljc) reads the text for its shape: the
    top-level forms, whether a bracket is left open, and the column of
    a new line. It knows strings, comments, character literals and the
    reader's prefixes, and is tested on the JVM and Jolt. The ? button
    shows the keys and the names in scope. A link in the prose no
    longer opens the dock. The closed dock shows the result in its
    line, and a click on that line opens the dock.
19. **Traces of the page in the history, and snippets for the
    buffer.** Decided 2026-09-27. It covers two things.
    The first was to show in the history, more quietly than an
    evaluation, what a widget, a link or a lesson does to what the
    REPL sees. The second was to have prewritten code for the buffer.
    What the REPL sees is `g`, `timeline` and now `sel`, the class
    that is open, bound before every evaluation as `g` is. Each
    button, link or page that changes one of them takes a step of
    `orrery.workbench`: `scrub`, `select`, `visit`, `alternative`,
    `submit` or `surprise`. The last four moved there from
    `orrery.state`, so that each one is a function of the state.
    `workbench/traced` takes the step and appends a trace:
    `{:trace what-was-done :in code :kind step :says what-changed}`.
    `workbench/code` writes the code from the step's var, as in
    `(swap! state wb/scrub 3)`, so the trace names the function that
    ran. A surprise is traced with its seed, and the same seed draws
    the same candidate again. A trace is left out when the run, the
    step and the open class are unchanged. A trace of a scrub or a
    selection replaces a trace of the same kind right before it, so
    playing a run leaves one trace. The first page the browser opens
    is not traced. The arrows recall only what was typed, the closed
    dock shows the last evaluation, and `*1` is untouched. Putting a
    history entry on show is traced without code, since no code names
    that value. Snippets come from three sources: a
    trace's code, which goes to the buffer as an entry's code does;
    the code behind three parts of the page, the run's counters, the
    opened class and the export; and a library of code for what no
    panel shows, listed by a "snippets" button in the dock.
    `orrery.snippets` holds both tables. The panel buttons read "to
    buffer", as the history's do. The proposal had been to make each
    panel's `.of` label the button, but the run panel's label names
    the function that made the run, not what its counters compute.
    The opened class and the export row have no label at all. The
    suites check that every snippet uses only names in scope. The
    browser suite evaluates every snippet on a lesson, and it checks
    that a trace's code, evaluated, gives the page the trace recorded.
    Writing that spec showed that a sorted map with number keys could
    not be printed at the REPL. A keyword lookup on such a map throws
    in ClojureScript, and `diff/egraph?`, `workbench/run?`,
    `runner-result?` and the printer's state check each looked one
    up. They now rule out a sorted map first.
## Status

Phase 1 delivered, 2026-09-25 (the port first and the compiled page
as the end state):

- cromulent ported (../cromulent/IDEA.md status), its runner split
  into start/step/finish; `npm run smoke` runs its 28 facts on node,
  all green, zero compiler warnings.
- orrery: `deps.edn`, `shadow-cljs.edn`, `package.json` (shadow-cljs
  only; no React); `orrery.notation`, `orrery.diff`, `orrery.costs`,
  `orrery.input`, `orrery.run`, `orrery.lessons`, `orrery.expect` and
  their tests, green on the JVM and Jolt (12 tests, 151 assertions:
  every live lesson and every alternative against the expectation
  table); `orrery.state`, `orrery.repl` (SCI as a library),
  `orrery.views.{common,scrubber,classes,tree,matches,stats,repl,lesson}`,
  `orrery.selftest`, `orrery.app`, `public/index.html`,
  `public/style.css` (light and dark).
- All eleven lessons live, each with a curated example, alternatives,
  editable inputs (terms, a pattern, rules as `[name lhs rhs]` data,
  exact numbers) and prose with inline widgets. bendix is `.cljc`
  behind `bendix.num`; `orrery.normal` renders each class's polynomial
  beside it; `orrery.run` takes the e-graph to start from and steps to
  append once the engine stops, which is how a bendix run ends with
  every normal form written in. The self-test tile runs cromulent's
  28 and bendix's 25 engine facts and every lesson's expectation in
  the browser on load, in about 150 ms;
  the Playwright suite (`npm run e2e`, 43 specs) drives every lesson,
  its alternatives and inputs, the transport, the cost picker and the
  REPL through the DOM. Writing it found one misreport: a class whose fresh
  root absorbed an old class was shown as new; `orrery.diff` now calls
  a class new only when it holds nothing of the earlier graph.

The generator and scorer, 2026-09-26 (section 4): `orrery.generate`,
`orrery.score`, a `:surprise` entry per lesson naming its draw and
its wants, and the "surprise me" button with the line that says what
was picked and why. Tests: the stream and a seed's terms pinned on
the JVM and Jolt, every lesson's draw a term the page takes over
thirty seeds, the features read off the curated runs (the blowup's
growth, the fix's seven proposals the analysis had already made,
pythagoras fired seven times, three answers under three costs), a
bank for every lesson, the pick's lean on the score; four Playwright
specs drive the button on lessons 3, 5, 6 and 9. The suites are 28
tests and 1435 assertions on the JVM and Jolt, 47 specs in the
browser.

Notation input, 2026-09-26 (phase 3, the first of its two
deliverables): `orrery.parse` and its test (the precedence table, the
numerals, functions and derivatives, the typed spellings, patterns,
the problems said in words, rules by line, and the round trip over the
printer's corpus, every lesson's values and alternatives, and thirty
seeds of every draw); `orrery.input` reading either spelling; the
fields showing the print mode in force and following the toggle while
unedited; the printer's faithfulness fixes (section 12, decision 7);
`notation.spec.js` driving the fields, the toggle and the problems in
the browser; `expect/notation-checks`, a round-trip fact per lesson
in the self-test tile and the node smoke, because a regex that
JavaScript's `re-matches` read differently from Java's slipped past
the JVM and Jolt suites and was caught only in the browser. The
suites are 41 tests and 2223 assertions on the JVM and Jolt, 64 facts
on node, 107 in the tile, 53 specs in the browser.

The prose pass and the opened class, 2026-09-26 (phase 3's second
deliverable, section 12 decision 8): every lesson's prose rewritten
in the explorable voice with the widgets as figures, four new widget
kinds (`:select`, `:cost`, `:alternative`, `:print`) beside `:step`,
citations as author-year links (`:cite`) and a reading line per
lesson derived from them;
`orrery.eclass` and the opened class above the list (section 3), a
`#id` inside any node and a node of the tree opening its class, the
selected class lighting the tree; `lessons_test` checking every
widget against the curated run and every credit against the reading
list, `eclass_test` pinning the counts (1680, 120, 37, 16, the
infinite class), the costs under each of lesson 6's costs, the
parents across lesson 3's steps and the histories; `selection.spec.js`
driving the panel and the prose links in the browser; lesson 5's
backoff run pinned at fourteen iterations; the transport moved into
a sticky replay bar above the class list (section 3). Then every
handler made data and the views made `.cljc` (section 6), with
`orrery.page-test` building the page for every lesson at every step
on the JVM and Jolt: 50 tests and 3073 assertions there. The suites are 47 tests
and 2416 assertions on the JVM and Jolt, 64 facts on node, 107 in
the tile, 59 specs in the browser.

The opened class moved into the replay bar, 2026-09-26 (section 3):
`orrery.views.detail` renders it under the transport, so it stays in
view while the list scrolls; `orrery.views.classes` keeps only the
rows and their marks. `selection.spec.js` now finds the detail in
`#replay`, checks it and the range are in the viewport at the foot
of lesson 7's list, and one opened class over lesson 10's two
panels: 60 specs in the browser.

The picture and the exporter, 2026-09-26 (phase 4, section 7):
`cromulent.export` (section 12, decision 2) with `export_test` and a
smoke fact, the JSON text of `2·x + y`, the same on the three
runtimes; `bendix.core/class-data` and `serialize`; `orrery.graph`,
the layered layout (section 3), and `orrery.views.graph`, the SVG,
with the tools row under the replay bar (`orrery.views.lesson/tools`)
holding the switch and the export's copy and download, the graph's
state (`:graph?`, `:graph-filter?`, `:graph-zoom`, `:export-status`)
in the atom, the layout memoized per step, filter, mode and cost in
`orrery.derived/graph-at`, and the clipboard and the download link
in `orrery.dispatch`, the only place the DOM is touched.
`graph_test` checks every lesson at every step, a box per class with
every node in it, an edge per child slot, every edge downward but
the ones that close a cycle, no overlap, all inside the picture, and
pins lesson 2 (four boxes, four edges, the product's two edges into
one class), lesson 7 (31 boxes, 185 nodes, 360 edges, five layers),
lesson 9's cycle and lesson 3's merged class with its two parents;
`export_test` reads the page's JSON for lessons 2, 8 and 9;
`page_test` builds every lesson at every step with the graph on,
filtered and zoomed, as well as off; `graph.spec.js` drives the
switch, the marks, the filter, the zoom, the print mode, the fork
and both exports in the browser, the download read back and parsed.
The suites are 55 tests and 25676 assertions on the JVM and Jolt, 65
facts on node, 108 in the tile, 69 specs in the browser; the release
bundle is 1.28 MB (319 KB gzipped), the picture and the export adding fifteen kilobytes.

The introduction, 2026-09-26 (section 2; section 12, decision 10):
`orrery.lessons/intro` with `intro-rules`, `start` and `heading`,
first in `all`; the `:lesson` widget; Panchekha et al. 2015 in the
reading list, for the sentence on where e-graphs are at work;
`orrery.derived/graph?`, with `:graph?` in the atom nil until the
switch is touched; its expectations in `orrery.expect`, the curated
run and four alternatives. `lessons_test` checks that the page opens
on it and that its prose links every lesson, `page_test` that the
picture is the lesson's until the switch is touched; the three tests
that draw skip a page with no "surprise me"; `intro.spec.js` drives
it in the browser, the dead end and back, the steps, the classes,
the picture here and not on a lesson, the links. Its prose is 341
words, the longest page by a little, the lessons running 155 to 280.
The suites are 58 tests and 26287 assertions on the JVM and Jolt, 66
facts on node, 114 in the tile, 76 specs in the browser; the release
bundle is 1.35 MB (328 KB gzipped), and the commit before it builds
to the same 328 KB.

The basics and the labels, 2026-09-26 (section 2; section 3;
section 12, decisions 11 and 12): `orrery.lessons/basics` with
`basics-rules`, first in `all` and the page's `start`; the
introduction without its number; `nav-label`, `address` and
`by-address`, with `orrery.app` reading the hash through them;
`operations`, `operation` and `call`, an `:operation` on every
lesson, and every input with its `:arg`, its `:label` for a problem
and its `:says`; `orrery.views.common/title`, the operation over the
fields in `orrery.views.lesson`, and a run adopted from the REPL
marked `:from :repl`. `lessons_test` checks the two pages and the
addresses, that every lesson names what it runs, that every field's
argument is in the call and that a line of the call fits the panel;
`page_test` that every page in either print mode heads its inputs
with the operation, its fields with their arguments and its panels
with what they show; `basics.spec.js` and `labels.spec.js` drive
both in the browser. The basics are 316 words. The suites are 60
tests and 26953 assertions on the JVM and Jolt, 67 facts on node,
120 in the tile, 86 specs in the browser; the release bundle is
1.36 MB (332 KB gzipped).

What the site is, 2026-09-26 (section 12, decision 13):
`orrery.lessons/about` and `colophon`, `about-view` and
`colophon-view` in `orrery.views.lesson`. `lessons_test` checks that
the statement says what it was written to say, the sentence
about LLMs among it, and that nothing in it links into the widgets;
`page_test` that it stands over the heading and the heading over the
first link that does something, on the opening page only, and that
every page carries the line; `about.spec.js` checks the same in the
browser by where the elements are. The suites are 62 tests and 27009
assertions on the JVM and Jolt, 67 facts on node, 120 in the tile,
88 specs in the browser.

The REPL's page and the state as a value, 2026-09-26 (section 2;
section 3; section 6; section 12, decision 14): `orrery.workbench`,
with `orrery.state` swapping its steps in, a validator on the atom
and the run loop behind `state/changed!`; `orrery.names`, the table
of what is in scope; `orrery.printed`; `orrery.repl` printing what
an evaluation prints, keeping `*1` to `*3` and `*e`, referring
`clojure.repl`, and holding the page's atom with `show!` and `push!`
over it; `orrery.lessons/repl`, last in `all`, and the `:eval`
widget; the `beside` layout in `orrery.views.lesson`, the names and
a run's summary in `orrery.views.repl`, the stats table naming its
columns from the iterations when the run names no rules; the
editor's recall and the history's scroll in `orrery.dispatch`.
`workbench_test` checks the constructor over every lesson, the
steps, the values of the REPL as runs, a run made by hand, the
recall and everything the atom refuses; `names_test` that every row
is a namespace that loads and names something in it; `page_test`
builds the REPL's page, and the page over each kind of value the
REPL can put on show, and reads what a value prints as;
`lessons_test` checks the lines the prose evaluates;
`repl-page.spec.js` drives the page in the browser, every line of
the documentation among it. The suites are 78 tests and 27864
assertions on the JVM and Jolt, 68 facts on node, 122 in the tile,
97 specs in the browser; the release bundle is 1.41 MB (342 KB
gzipped), ten kilobytes of it this.

The editor, 2026-09-27 (section 12, decision 15, its first step):
prism-code-editor 5.4.0, pinned in `package.json`, its `layout.css`
copied into `public/prism-layout.css`; `orrery.editor`; the hook
`[:repl/editor opts]` and `[:repl/line-numbers]` in
`orrery.actions`; `editor.spec.js` checks the colours, the closing
of brackets, Enter and Tab, undo, the caret after a recall, the
prose leaving the editor's text alone, and the line numbers. The
suites are 78 tests and 27865 assertions on the JVM and Jolt, 68
facts on node, 104 specs in the browser; the release bundle is 1.47
MB (360 KB gzipped), 16 KB gzipped of it the editor.

A lesson's layout and the REPL's dock, 2026-09-27 (section 12,
decision 17): `orrery.views.panels`; the grid of `.lesson-grid` and
the dock in `public/style.css`; `[:repl/dock]` and `[:repl/resize]`
in `orrery.actions`, and Ctrl-` in `orrery.dispatch`;
`layout.spec.js` checks the columns at both widths, the dock's
shortcut and its memory across lessons, its last line, its resizing,
and that nothing hides under it. The suites are 79 tests and 27890
assertions on the JVM and Jolt, 68 facts on node, 110 specs in the
browser.

One dock on every page, 2026-09-27 (section 12, decision 18):
`orrery.forms` and `forms_test.cljc`; the dock in
`orrery.views.repl`, its styles in `public/style.css`; the keys of
the line and the buffer in `orrery.editor`; `state/eval-line!` and
`state/eval-buffer!`; `[:repl :buffer]` and `workbench/to-buffer`;
`[:repl/help]` and `[:repl/to-buffer]` in `orrery.actions`.
`editor.spec.js` checks the keys of the line and the buffer and the
copy into the buffer, `layout.spec.js` the dock and
its popover, and `repl-page.spec.js` the page under the open dock.
The suites are 85 tests and 27953 assertions on the JVM and Jolt, 68
facts on node, 117 specs in the browser; the release bundle is 1.49
MB (370 KB gzipped).

Next: a review of the dock and of the layout at 1100
pixels; the page for playing with e-graphs, routed before the
lessons; then the later items of section 7, per-rule timings at
scale and static deployment.
