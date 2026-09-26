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
  over a real execution. It never got past its first example (the
  Captain, 2026-09-25), so orrery keeps the insight and not the
  construction: the engine is compiled, not interpreted, and the page
  is built to carry eleven lessons, a REPL and forks (section 6).

The thesis of bendix is *simplification is equality saturation plus
taste*. orrery's job is to make a person see both halves: the graph
that holds every form, and the cost that picks one.

## 1. Audience (decided 2026-09-25)

**Learners first.** People who want to understand what an e-graph is
and how it works, who may not know much mathematics, and who should
be able to watch interesting behaviour without typing any. The
Captain's direction: education on what e-graphs are and how they
work, a REPL, and examples the page offers on its own.

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

## 3. What it shows

The panels, each mapped to what exists:

- **The graph.** Classes as boxes, nodes inside, edges to child
  classes. Rendered by the egraphs-good `egraph-visualizer` component
  (Cytoscape and the Eclipse Layout Kernel) fed the `egraph-serialize`
  JSON that cromulent will emit. For small graphs. Past a few dozen
  classes the picture is spaghetti and the class list is primary.
- **The class list.** Every root, its nodes, its parents, its analysis
  data rendered. `eg/roots`, `eg/nodes`, `eg/eclass`, `eg/data`.
- **The scrubber.** Iteration k of the timeline, forwards and
  backwards; the diff to k−1 highlighted: nodes added, classes
  merged. The diff is a set difference on `memo-entries`.
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
  context: `(eg/add g [:+ :x 1])` at a prompt, the page rendering the
  value returned. This is what the native mode is.

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
  through `:local/root`) and orrery's own namespaces into one bundle
  that `public/index.html` loads. The first draft loaded the source
  through scittle with no build step; the Captain named the compiled
  page as the end state, and the port is the same either way, so the
  plan went there directly. An interpreter is two orders of magnitude
  off the JVM where the compiler is a small multiple: the self-test's
  29 facts run in about 40 ms in Chromium and 54 ms on node.
- **SCI as a library, for the REPL only.** The one thing an
  interpreter is needed for. `orrery.repl` will hold the SCI context
  with the engine namespaces copied in; nothing else may know it is
  there.
- **No React.** The page is Replicant: one hiccup tree computed from
  one atom (`orrery.state`), rendered on every change. Views are
  functions of values (`orrery.views.*`); only `orrery.views.lesson`
  reads the state, so the same class list serves the fork.
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
- **Pure namespaces on three runtimes.** `orrery.notation` (the printer),
  `orrery.diff`, `orrery.costs`, `orrery.input` (the EDN reader),
  `orrery.run`, `orrery.lessons` (prose as hiccup data, decided over
  markdown: no renderer to ship, inline widgets `[:notation t]` and
  `[:step k label]`, and the tests can walk it), `orrery.generate`,
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

**Phase 3, notation input.** The parser (delivered 2026-09-26:
`orrery.parse`, the inputs reading either spelling and showing the
mode in force, the printer made faithful to it, the round trip over
every lesson's values and thirty seeds of every draw); then a pass on
the prose in the explorable-explanation voice, with the widgets as
its figures.

**Later.** The egraph-visualizer picture, which is when
`cromulent.export` and the `egraph-serialize` JSON are needed;
per-rule timings at scale; static deployment.

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

- **egraph-visualizer** (egraphs-good; Cytoscape + ELK; an npm
  package and a GitHub Pages demo): the static picture of one
  serialized e-graph. Reused, not rebuilt.
- **egraph-serialize**: the JSON format between e-graph libraries and
  the visualizer. cromulent emits it.
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

**orrery** (decided by the Captain 2026-09-25): a clockwork model of
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

Not chosen: loupe (the first working name; rejected by the Captain,
and the visualizer collision), zoetrope (frames into an illusion of
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
2. **Where the exporter lives.** Deferred until the visualizer needs
   it; `cromulent.export` with a bendix counterpart remains the
   recommendation.
3. **Where lesson prose lives.** Decided: hiccup data in
   `orrery.lessons` (section 6).
4. **The picture at scale.** Decided: the class list is primary; the
   visualizer comes later, with the exporter.
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

## Status

Phase 1 delivered, 2026-09-25 (the Captain's "make it so", with the
port first and the compiled page as the end state):

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
on node, 107 in the tile, 53 specs in the browser. The prose pass
remains.

Next: phase 3, notation input.
