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
  the native tagged vectors, and a lay mathematical syntax. The
  native mode comes first; lay *display* is early because a learner
  needs it from the first lesson; lay *input* is a later addition.

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

Lessons 1 to 7 and 10 need cromulent only. Lessons 8, 9 and 11 need
bendix, which fixes the phase in which they become live (section 7);
before that they run from recorded traces.

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
  class, rendered by `poly/->term` and printed in the lay syntax.
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

**The generator**: bendix's property tests already draw random terms
over a small signature (`term-gen`, `trig-term-gen`,
`test/bendix/*_test.clj`). A candidate is saturated and **scored** by
features the engine reports:

| feature | from | high means |
|---|---|---|
| merges per node | `:stats` `:applied`, class count | the rules did something |
| shrink ratio | input size over extracted size | simplification is visible |
| node growth ratio | node count per iteration | the blowup is visible |
| analysis-only merges | classes merged with no rule applied | the polynomial did the work |
| a normal-form rule fired | `:applied` by name | lesson 9 material |
| iterations to saturate | `:iterations` | a scrubbable story, neither one step nor a wall |
| stop reason | `:stop-reason` | `:node-limit` for lesson 7, `:saturated` elsewhere |

A lesson names which features it wants high; a "surprise me" button
draws from the bank at random, weighted by score; candidates are
deduplicated by the shape of their result so the bank is not fifty
spellings of one thing. Interestingness is our score and nothing
deeper; it is tunable and it is small code. The bank is drawn live
in the browser once bendix is ported (phase 2); until then "try
another" is a short curated list per lesson.

## 5. Two modes over one canonical form

The canonical data is bendix's: tagged vectors, keyword operators,
keyword variables, exact numbers (../bendix/IDEA.md section 1), read
by the EDN reader, which every runtime has (`orrery.input`). The
**native mode** is that format at a REPL and in every panel.

The **lay mode** prints the same data as mathematics: `2·x + y`,
`sin²x`, `x^(n+1)`, `d/dx`. bendix's design already lists infix
printers and parsers as compilers to and from the canonical form,
none on its critical path. Here the printer is on the critical path,
because a learner meets lesson 1 in the lay syntax; it is small
(precedence, parentheses, a few special forms: `orrery.lay`, built
2026-09-25) and prints nesting faithfully, `a + (b + c)` against
`a + b + c`, so an arrangement of a sum stays visible. The
**parser**, lay input, is the future addition the Captain named; it
is a Pratt parser of similar size and comes after the lessons are
live (phase 3). Until then the lay mode is display-only and input is
native, or by choosing from the bank.

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
- **Pure namespaces on three runtimes.** `orrery.lay` (the printer),
  `orrery.diff`, `orrery.costs`, `orrery.input` (the EDN reader),
  `orrery.run`, `orrery.lessons` (prose as hiccup data, decided over
  markdown: no renderer to ship, inline widgets `[:lay t]` and
  `[:step k label]`, and the tests can walk it) and `orrery.expect`
  are `.cljc`, tested by `clojure -M:test` and `jolt -M:test`, and
  compiled into the page. Runner code (`orrery.state`, the views,
  `orrery.app`) is `.cljs`.
- **Verification, one table four ways.** `cromulent.smoke` (28 facts)
  and `orrery.expect` (the lessons' counts, iterations, stop reasons
  and costs) are asserted by the JVM and Jolt suites, by `npm run
  smoke` on node, by the page's self-test tile in the browser, and by
  `bin/e2e.sh`, which builds a release, serves it, drives lesson 7
  with playwright-cli and asserts the counters through the DOM. Never
  ids, never a tied term (section 8).

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

**Phase 2, bendix live.** `bendix.num`, the numeric layer (section 8);
lessons 8, 9 and 11 live; the generator and scorer live.

**Phase 3, lay input.** The parser; then a pass on the prose in the
explorable-explanation voice, with the widgets as its figures.

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
| determinism | class and node counts, iterations, stop reasons, per-rule counts and costs are identical on the three runtimes (`cromulent.smoke`). Root ids and tied extractions are stable per runtime only: hash iteration order differs, and the tie falls to child ids. Every arrangement of the five-atom sum costs 9, so lesson 7 asserts the cost, not the spelling |
| bendix numerics | unchanged: `bendix.num` is the first task of phase 2. The EDN reader turns `1/2` into `0.5` in ClojureScript, so a ratio needs its own reader there |
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
5. **Lay display in phase 1.** Decided yes: `orrery.lay`.
6. **The runner as start/step/finish.** Done 2026-09-25:
   `cromulent.rewrite/start`, `step` and `finish`, with `embiggen` as
   the loop over them, so a run stepped by the page from a timer is
   exactly the run one call makes, bans and all (lesson 5).

## Status

Phase 1 delivered, 2026-09-25 (the Captain's "make it so", with the
port first and the compiled page as the end state):

- cromulent ported (../cromulent/IDEA.md status), its runner split
  into start/step/finish; `npm run smoke` runs its 28 facts on node,
  all green, zero compiler warnings.
- orrery: `deps.edn`, `shadow-cljs.edn`, `package.json` (shadow-cljs
  only; no React); `orrery.lay`, `orrery.diff`, `orrery.costs`,
  `orrery.input`, `orrery.run`, `orrery.lessons`, `orrery.expect` and
  their tests, green on the JVM and Jolt (12 tests, 151 assertions:
  every live lesson and every alternative against the expectation
  table); `orrery.state`, `orrery.repl` (SCI as a library),
  `orrery.views.{common,scrubber,classes,tree,matches,stats,repl,lesson}`,
  `orrery.selftest`, `orrery.app`, `public/index.html`,
  `public/style.css` (light and dark).
- Lessons 1 to 7 and 10 live, each with a curated example, two or
  three alternatives, editable inputs (terms, a pattern, rules as
  `[name lhs rhs]` data) and prose with inline widgets; 8, 9 and 11
  wait for bendix. The self-test tile runs the 28 engine facts and
  every lesson's expectation in the browser on load, in about 60 ms;
  `bin/e2e.sh` drives lessons 2, 3, 6, 7 and the REPL through the DOM.

Next: phase 2, `bendix.num` and lessons 8, 9 and 11.
