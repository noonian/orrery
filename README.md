# orrery

An e-graph explorer for understanding, named for the clockwork model
of the planets you turn by hand to see how the system moves: watch
[cromulent](https://github.com/noonian/cromulent) and
[bendix](https://github.com/noonian/bendix) work, step by step, in the
browser. Two pages to start from, the basics for a reader who has met
none of this and an introduction to what an e-graph is and why it is
interesting, and twelve lessons from "a term is a tree" to
"differentiation is simplification", each a real execution you can
scrub forwards and backwards, with a REPL underneath; and a page for
the REPL itself, its editor beside every panel.

What it is: an interactive tool for exploring e-graphs and learning
how they work, its author's learning included. It embeds cromulent,
an e-graph that is an immutable, persistent value, and bendix, a
nascent computer algebra system built on it, runs them live in the
page, and puts widgets over what they really compute. It is largely
written using LLMs. The page says so itself, at length over the page
it opens on, before any prose that links into the widgets, and in a
line under every page.

[IDEA.md](IDEA.md) is the design and status. The engine runs live in
the page: cromulent's own `.cljc` compiled by shadow-cljs, no React
(the page is Replicant, every event handler in it data routed
through one dispatch table, so the views are `.cljc` and the JVM
suite builds the whole page), SCI embedded only for the REPL.

## Running it

```sh
npm install                    # shadow-cljs
npm run watch                  # build, serve on http://localhost:8379, rebuild on change
```

or, for the built page without the watcher:

```sh
npm run release && bin/serve.sh   # http://localhost:8379
```

cromulent and bendix come from GitHub at the shas pinned in
`deps.edn`. The `:local` alias uses the checkouts at `../cromulent`
and `../bendix` instead: `clojure -M:local:test`, `jolt
-M:local:test`, and `npm run watch:local`, `release:local`,
`smoke:local` and `e2e:local` for the builds.

## Checking it

```sh
clojure -M:test     jolt -M:test     # the pure namespaces, either runtime
npm run smoke                        # cromulent's and bendix's cross-runtime facts on node
npm run e2e                          # build the release, drive every lesson in a browser
```

The browser suite is Playwright, in `test/e2e/`: one spec per live
lesson, plus the REPL, its page and the self-test tile. `npx playwright test`
reruns it over the last build, and with `ORRERY_URL=http://localhost:8379`
it drives the watcher's page instead of starting a server.

The same facts must hold on the JVM, on Jolt, on node and in the
browser: class counts, node counts, iterations, stop reasons, costs.
The page's self-test tile runs them on load.

## Lessons

Two pages come before the lessons, each a live run like them, with
the picture of the graph drawn.

The page opens on the basics, *Many ways to write one thing* ("Start
here" in the navigation, `#basics` or no hash at all), written for a
reader who does not know what rewriting or simplifying is and has no
identity by heart: `(x + 0)·1` is a long way to write `x`; a rule is
a shape and another shape that always means the same; an e-graph
crosses nothing out, collects every way of writing a thing and keeps
the ways that mean the same in one box; then it chooses the shortest.

The introduction, *What is an e-graph?* (`#intro`), is the next page:
the rewriter's dilemma over `(a·2)/2`, where shifting first loses the
cancellation; the e-graph that keeps both forms; saturation, and the
choice of `a` at the end; why that is interesting and where it is
used; and a link to every lesson.

1. A term is a tree
2. Sharing
3. Equality and congruence
4. A rule
5. Saturation
6. Extraction is taste
7. The blowup
8. The fix
9. Inside the polynomial
10. A rule over the polynomial
11. What if
12. Differentiation is simplification

After them, the REPL's page (`#repl`, see below).

Every lesson is live. Each has a curated example, a "try another"
over alternatives, a "surprise me" that draws a dozen random terms
in the browser, runs and scores them by what the lesson wants to
show and picks one, and inputs you can edit in either spelling:
terms, a pattern, rules, exact numbers such as `1/2`. Every panel
shows the engine's real values, scrubbed forwards and backwards; for
the bendix lessons the class list carries each class's polynomial;
and a REPL underneath has `g` bound to the e-graph you are looking
at, with cromulent's and bendix's namespaces loaded.

The prose is written to be pulled on: each paragraph hands you a
step to scrub to, a class to open, a cost to switch, an alternative
to try, and cites the paper behind the idea as a link, with a
reading line under each lesson. Click a class, a `#id` inside any node, a node of
the tree, or a link in the prose to open the class: its nodes with
their costs under the cost in force and the cheapest marked, how
many terms it stands for and the cheapest few (lesson 7's input
class holds 1680 arrangements in thirty nodes; a class that reaches
itself holds infinitely many), what it points at and what points at
it, and where it has been along the run.

Every panel says what it is the work of. The inputs sit under the
operation they are the arguments of: its name (add, union then
rebuild, saturate, simplify, differentiate), the function that is it
as the REPL names it (`rw/saturate`, `bx/simplify`), what it does in
a sentence, and the call, with the runner options in force written
out. Each field is headed by its argument's name, `term`, `rules`,
`lhs`, `x`, and what that is. The other panels carry the function
or the value beside their heading: the run its operation, "best so
far" `ex/extract` and its three arguments, the cost picker `cost`,
the class list `g at this step`, the iterations `:stats`, the
matches `pat/ematch`.

"Draw the graph", in the row under the transport, adds the picture:
a box per class with its nodes inside and an edge from each node to
the class it points at, layered with the leaves at the bottom, the
same marks as the list, and the polynomial in the box on the bendix
lessons; hover a box to light its row, click it to open the class,
zoom or fit the width, or draw only what the opened class reaches.
It is off by default because it takes the room, except on the two
pages before the lessons, and once you touch the switch the choice
is yours on every page. The same row copies
or downloads the step as egraph-serialize JSON, the format egg's and
egglog's tools read, every node costed under the cost in force.

## The REPL

Every lesson has a REPL under it, and `#repl`, "The REPL" at the
end of the navigation, is a page for it: the editor on the left,
staying in view, and on the right every panel that reads an e-graph,
over whatever the REPL puts on show. The page's prose is the
REPL's documentation, and each line of code in it is a link that
evaluates it.

What is in scope, which the page lists from the same table the REPL
is made from (`orrery.names`):

| name | what it is |
| --- | --- |
| `g` | the e-graph on show: the run's, at the step on show |
| `timeline` | every step of the run on show, a vector of e-graphs |
| `state` | the page, an atom; a swap of it is a change of the page |
| `(show! v)` | puts `v` on show: an e-graph, a `[g id]` pair, a runner's result or a run |
| `(push! g label)` | makes `g` the next step of the run on show |
| `*1` `*2` `*3` `*e` | the last values and the last error |
| `doc` `dir` `apropos` `find-doc` | `clojure.repl`'s |
| `eg` `pat` `rw` `ex` `term` `check` `export` | cromulent's namespaces |
| `bx` `rules` `an` `poly` `bt` `num` | bendix's |
| `wb` `run` `lessons` `eclass` `costs` `diff` `notation` `input` | orrery's |

```clojure
(eg/class-count g)                        ; the e-graph on show is a value
(show! (eg/add g [:+ :b :b]))             ; a pair: the e-graph, and the class to extract for
(let [[g a] (eg/add g :a)
      [g b] (eg/add g :b)]
  (push! (first (eg/union g a b)) "a = b, rebuild pending"))
(push! (eg/rebuild g) "rebuilt")          ; a run made by hand, scrubbed like a lesson's
(show! (run/start [:+ [:+ :a :b] :c]      ; a run that has not run: the page steps it
                  (lessons/rules-of lessons/ac-rules) {}))
(swap! state wb/scrub 0)                  ; the page is a value in an atom
(swap! state assoc :cost :prefer-shift)
(swap! state #(-> % (wb/open lessons/blowup) wb/start))
```

A value is shown by what it is: an e-graph as its class list, a
`[g id]` pair with the class marked, a runner's result or a run in a
line with a button that puts its timeline on show, anything else
printed, abridged (an e-graph inside a value as its counts, a
collection cut at 48 elements, so `(range)` prints). What an
evaluation prints stands over its value. Ctrl-Enter or Cmd-Enter
evaluates; the up arrow in the editor's first line brings back what
was evaluated before, the down arrow in its last what was being
typed.

The state is a value (`orrery.workbench`, `.cljc`): `(wb/page
lesson)` is the page of a lesson, and `open`, `start`, `show`,
`scrub`, `put` and `push` are the steps its buttons take, the same
functions the tests build their pages with on the JVM and Jolt. The
atom refuses a value that is not a state of the page, and says what
is wrong with it (`wb/problem`), so a line at the REPL cannot leave
the page with nothing to draw. The run loop follows the value: a run
still running is stepped whoever swapped it in.

What is typed is interpreted, by SCI; every function it calls is the
compiled one the page runs. There is no interrupt: an evaluation
that never ends takes the tab with it.

To load another namespace into the REPL, copy it in `orrery.repl`
(`sci/copy-ns`) and give it a row in `orrery.names`; the prelude,
the page's list and the check on the documented lines follow from
the row.

## Two modes

Native: the tagged-vector format, typed into the page or at the
Clojure REPL with the engine loaded.

```clojure
[:+ [:* 2 :x] :y]
```

Notation: the same data as mathematics, `2·x + y`, printed by every
panel and read by every input: `sin²x` or `sin^2 x`, `x^(n + 1)`,
`d/dx sin(2·x)`, `?x` in a pattern, `1/2` the number and `1/(2)` the
quotient node, rules one per line as `comm: ?a + ?b -> ?b + ?a`. The
fields show whichever mode is in force and read both.
