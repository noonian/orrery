# orrery

An e-graph explorer for understanding, named for the clockwork model
of the planets you turn by hand to see how the system moves: watch cromulent and bendix
work, step by step, in the browser. Eleven lessons from "a term is a
tree" to "differentiation is simplification", each a real execution
you can scrub forwards and backwards, with a REPL underneath.

**Not built yet.** [IDEA.md](IDEA.md) is the design; this file says
what running it will look like.

## Running it

The page loads the sibling libraries' source, so serve from the
**workspace root** (the parent of this folder), not from `orrery/`:

```sh
cd ..                           # symbolics/, the parent
python3 -m http.server 8379     # any static server; then open
open http://localhost:8379/orrery/index.html
```

No build step: the page loads scittle and reagent from a CDN. In
phase 1 it renders recorded traces; from phase 2 it runs cromulent
live; from phase 3 bendix.

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

Each has a curated example and a "surprise me" that draws from a bank
of generated terms scored for what they will show.

## Two modes

Native: the tagged-vector format and a Clojure REPL with the engine
loaded.

```clojure
(def g (eg/add (eg/egraph) [:+ [:* 2 :x] :y]))   ; the page renders the value
```

Lay: the same data printed as mathematics, `2·x + y`. Display first;
typing mathematics comes later.

## Recording a lesson

Traces are produced on the JVM or Jolt by the exporter and checked
into `lessons/` as EDN:

```sh
clojure -M:export lessons/07-blowup.edn      # planned
```

The same trace must come out identical on the JVM, on Jolt and in the
browser: class counts, node counts, extracted terms.
