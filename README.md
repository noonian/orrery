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
npm run smoke                        # cromulent's cross-runtime facts on node
bin/e2e.sh                           # build, serve, drive lesson 7 in a browser, assert
```

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
7. The blowup — live
8. The fix
9. A rule over the polynomial
10. What if
11. Differentiation is simplification

Each has a curated example and a "try another" over alternatives;
lessons 8, 9 and 11 wait for bendix.

## Two modes

Native: the tagged-vector format, typed into the page and, soon, at a
Clojure REPL with the engine loaded.

```clojure
[:+ [:* 2 :x] :y]
```

Lay: the same data printed as mathematics, `2·x + y`. Display first;
typing mathematics comes later.
