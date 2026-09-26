# orrery

An e-graph explorer for understanding, named for the clockwork model
of the planets you turn by hand to see how the system moves: watch
cromulent and bendix work, step by step, in the browser. Eleven
lessons from "a term is a tree" to "differentiation is
simplification", each a real execution you can scrub forwards and
backwards, with a REPL underneath.

[IDEA.md](IDEA.md) is the design and status. The engine runs live in
the page: cromulent's own `.cljc` compiled by shadow-cljs, no React
(the page is Replicant), SCI embedded only for the REPL.

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
