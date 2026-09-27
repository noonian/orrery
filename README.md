# orrery

An e-graph explorer for understanding, named for the clockwork model
of the planets you turn by hand to see how the system moves: watch
cromulent and bendix work, step by step, in the browser. Two pages
to start from, the basics for a reader who has met none of this and
an introduction to what an e-graph is and why it is interesting, and
eleven lessons from "a term is a tree" to "differentiation is
simplification", each a real execution you can scrub forwards and
backwards, with a REPL underneath.

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

## Checking it

```sh
clojure -M:test     jolt -M:test     # the pure namespaces, either runtime
npm run smoke                        # cromulent's and bendix's cross-runtime facts on node
npm run e2e                          # build the release, drive every lesson in a browser
```

The browser suite is Playwright, in `test/e2e/`: one spec per live
lesson, plus the REPL and the self-test tile. `npx playwright test`
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
9. A rule over the polynomial
10. What if
11. Differentiation is simplification

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
