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
- **The fleet already knows how to build this.** ../../time-and-space/webapp
  loads the catalytic libraries' `.cljc` source verbatim through
  scittle, with no build step, and its one architectural insight is
  the one above: a widget is a scrubber over a real execution. orrery
  is that page for symbolics.

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
| 2 | Sharing | `(x + 1)·(x + 1)`: the two `x + 1` are one node, the tree has 7 nodes and the graph 5 | `eg/add`, `eg/node-count` |
| 3 | Equality and congruence | assert `a·2 = a<<1`; the classes merge; then `(a·2)/2` and `(a<<1)/2` merge on rebuild without being told (the egg README example) | `eg/union`, `eg/rebuild`, `:dirty?` before and after |
| 4 | A rule | one rule, one iteration: matches highlighted, then the nodes it adds and the classes it joins | `pat/ematch`, `embiggen {:iter-limit 1}` |
| 5 | Saturation | scrub the iterations; what each added, in colour; why it stopped | `:timeline? true`, `:stats`, `:stop-reason` |
| 6 | Extraction is taste | the same saturated graph under three costs gives three answers | `ex/extract` with `ast-size`, a shift-preferring cost, bendix `default-cost`, `no-D` |
| 7 | The blowup | a sum of five atoms under commutativity and associativity: 31 classes, 180 nodes, and a counter that climbs as 3ⁿ; six atoms hit the node limit | `embiggen`, `:node-limit` (../design/ac-problem.md section 1) |
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
- **The REPL.** A Clojure REPL with the engine loaded, which scittle
  gives for free: `(eg/add g [:+ :x 1])` at a prompt, the page
  rendering the value returned. This is what the native mode is.

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
deeper; it is tunable and it is small code. In phase 1 the bank is
built offline on the JVM and shipped as EDN; from phase 3 it can be
drawn live in the browser.

## 5. Two modes over one canonical form

The canonical data is bendix's: tagged vectors, keyword operators,
keyword variables, exact numbers (../bendix/IDEA.md section 1), read
by the Clojure reader, which scittle has. The **native mode** is that
format at a REPL and in every panel.

The **lay mode** prints the same data as mathematics: `2·x + y`,
`sin²x`, `x^(n+1)`, `d/dx`. bendix's design already lists infix
printers and parsers as compilers to and from the canonical form,
none on its critical path. Here the printer is on the critical path,
because a learner meets lesson 1 in the lay syntax; it is small
(precedence, parentheses, a few special forms) and belongs in phase
1. The **parser**, lay input, is the future addition the Captain
named; it is a Pratt parser of similar size and comes after the
lessons are live (phase 4). Until then the lay mode is display-only
and input is native, or by choosing from the bank.

## 6. Architecture

- The shape of ../../time-and-space/webapp: one `index.html`, scittle
  and reagent from a CDN, sources loaded as `.cljc` with no build
  step, served from the workspace root so sibling sources are
  reachable. Graduation to shadow-cljs when a bundle is wanted;
  nothing may assume the interpreter.
- **The trace** is the data model. A lesson or a bank entry is
  `{:term :rules :opts :timeline [g0 g1 …] :stats :extractions {cost [term …]}
  :stop-reason}`, produced by an exporter and consumed by the page.
  An e-graph value holds functions (analyses, the scheduler's state,
  rules' guards), so export is a `->data` that keeps the union-find,
  the classes with their nodes and parents, the memo entries, the
  operator table and every analysis's data *rendered* (`poly/->term`
  for bendix), and drops the rest. The same function emits the
  `egraph-serialize` JSON for the visualizer. It belongs in cromulent
  (`cromulent.export`) with a bendix counterpart for its analysis
  data, since orrery is real code that uses it; orrery is a consumer.
- **The page** is reagent components over a trace: graph, class
  list, scrubber, best-so-far, cost picker, stats, polynomial panel,
  fork, REPL, and the lesson prose around them.
- **Verification** as the fleet does it: playwright drives the page
  end to end after every phase, and the cross-runtime standard applies
  to traces: the same lesson produces identical class counts, node
  counts and extracted terms on the JVM, on Jolt and in the browser.

## 7. Plan, in phases, each with a deliverable

**Phase 1, no port.** The exporter on the JVM; lesson traces and the
example bank as EDN; the page renders all eleven lessons from traces
with the scrubber, the diff, the cost picker (extractions recorded per
cost), the polynomial panel, the stats and the bank; the visualizer
for the picture; the lay printer. First deliverable inside the phase:
**lesson 7 beside lesson 8**, the blowup and the fix, because it is
the whole argument of the workspace in one picture and needs nothing
ported. Nothing in cromulent or bendix changes but the exporter.

**Phase 2, cromulent live.** cromulent as `.cljc` behind one
conditional file (section 8), a live REPL, and lessons 1 to 7 and 10
interactive over the learner's own terms and rules.

**Phase 3, bendix live.** `bendix.num`, the numeric layer (section 8);
lessons 8, 9 and 11 live; the generator and scorer live.

**Phase 4, lay input.** The parser; then a pass on the prose in the
explorable-explanation voice, with the widgets as its figures.

## 8. Portability ledger

Verified against the source on 2026-09-25, not against the design
documents. ClojureScript, not WebAssembly: there is no mature path
from Chez or the JVM to wasm, and the fleet has proven the `.cljc`
route.

| item | in the source | on ClojureScript |
|---|---|---|
| packed hashcons keys | `op·2^48 + a·2^24 + b`, under 2^60 (`cromulent.core`, `op-scale`, `id-scale`) | exceeds the 2^53 exact range of a JS number; a narrower layout (`2^40`, `2^20`: ids below about a million, operators below 4096, keys under 2^52) or a per-platform one; **decided by measurement on Jolt and the JVM**, since a change there is a change to the engine's hot path (open question 1) |
| register and match arrays | `long-array`, `aset`, `aget`, `long`, `^longs` hints in `cromulent.pattern` and `cromulent.rewrite` | all four functions exist in ClojureScript 1.12 core (verified in the jar); hints are ignored; whether scittle's SCI exposes them is the first smoke test of phase 2, with shadow-cljs as the fallback |
| the clock | `now-ms` over `System/nanoTime`, one function in the runner | one line behind the conditional |
| backoff arithmetic | `bit-shift-left` of the match limit and ban length by the times banned | 32-bit in JS; multiply by a power of two instead, on all runtimes |
| bendix numerics | exact ratios and bignums throughout `bendix.poly` (`+'`, `*'`, `ratio?`, `numerator`, `denominator`) and the predicates of `bendix.term` | no ratio or bignum type exists; `bendix.num` (`.cljc`, the library's one conditional, as `catalytic.defaults` is catalytic-buffer's) is Clojure's numbers on the JVM and Jolt and a rational over JS `BigInt` in the browser; the exact surface is the first task of phase 3 and is about eight functions and the predicates |
| determinism | `compare-nodes` is a total order the same on both runtimes | must hold on JS too (compare over keywords, numbers, vectors); the cross-runtime trace test of section 6 is the check |
| performance | Jolt is the primary runtime for the engine | scittle interprets, two orders of magnitude off the JVM; a twenty-node term under a few rules is well under a second, which is a lesson; the AC-10 yardstick is not a browser workload and the page does not pretend it is |

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

1. **The packed-key layout.** One layout for all three runtimes,
   `2^40`/`2^20`, caps class ids at about a million where the AC-10
   run allocates about sixty thousand; or two layouts behind the
   conditional, which keeps the engine as measured. Decide by a
   `bench/` row on Jolt and the JVM, per ../cromulent/IDEA.md section
   5: multiplication constants are the only difference.
2. **Where the exporter lives**: `cromulent.export` and a bendix
   counterpart (recommended: orrery is real code that uses them), or
   inside orrery.
3. **Where lesson prose lives**: EDN beside each trace, or markdown
   the page renders.
4. **The picture at scale**: whether the visualizer is enough or the
   class list should be primary from the start.
5. **Lay display in phase 1**: recommended yes (section 5); the
   Captain's call.

## Status

Design only, 2026-09-25. Nothing built; no `deps.edn`, no
`index.html`. The Captain does `git init`. Next: the Captain's
"make it so" on phase 1, beginning with the exporter and the lesson 7
and 8 traces.
