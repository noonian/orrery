#!/bin/sh
# End to end: build the page, serve it, drive lesson 7 in a real
# browser with playwright-cli, and assert the numbers the JVM and Jolt
# suites assert. Exits non-zero on any miss.
set -eu
cd "$(dirname "$0")/.."
S="-s=orrery-e2e"
PORT="${PORT:-8391}"
npx shadow-cljs release app >/dev/null 2>&1
python3 -m http.server "$PORT" --directory public >/dev/null 2>&1 &
SERVER=$!
cleanup() { playwright-cli $S close >/dev/null 2>&1 || true; kill $SERVER 2>/dev/null || true; }
trap cleanup EXIT
sleep 1
playwright-cli $S open "http://localhost:$PORT/#7" >/dev/null
playwright-cli $S run-code "async page => { await page.waitForSelector('#selftest-ok', {timeout: 60000}); await page.waitForSelector('#run-status[data-status=done]', {state: 'attached', timeout: 120000}); }" >/dev/null
snap() { playwright-cli $S --raw eval "JSON.stringify(window.orreryPage.snapshot())"; }
expect() {
  actual=$(snap | tr -d '\\')
  if echo "$actual" | grep -q "$2"; then echo "ok    $1"; else echo "FAIL  $1: $actual"; exit 1; fi
}
expect "self-test tile is green" '"status":"done"'
playwright-cli $S click "#step-last" >/dev/null
expect "last step: 31 classes" '"classes":31'
expect "last step: 185 nodes" '"nodes":185'
expect "stopped because saturated" '"stopReason":"saturated"'
expect "seven iterations" '"iterations":7'
expect "best cost 9 under AST size" '"bestCost":9'
playwright-cli $S click "#step-first" >/dev/null
expect "first step: 9 classes" '"classes":9'
expect "first step: 9 nodes" '"nodes":9'
playwright-cli $S click "#step-last" >/dev/null
playwright-cli $S fill "#repl-input" "(eg/class-count g)" >/dev/null
playwright-cli $S click "#repl-eval" >/dev/null
last=$(playwright-cli $S --raw eval "Array.from(document.querySelectorAll('#repl .entry pre.result')).pop().textContent")
if echo "$last" | grep -q '31'; then echo "ok    REPL: (eg/class-count g) is 31"; else echo "FAIL  REPL: $last"; exit 1; fi
playwright-cli $S fill "#repl-input" "(second (eg/add g [:+ :a0 :a1]))" >/dev/null
playwright-cli $S click "#repl-eval" >/dev/null
last=$(playwright-cli $S --raw eval "Array.from(document.querySelectorAll('#repl .entry pre.result')).pop().textContent")
if echo "$last" | grep -Eq '^"?[0-9]+"?$'; then echo "ok    REPL: eg/add finds the existing class $last"; else echo "FAIL  REPL add: $last"; exit 1; fi
# lesson 3: the congruence merge on rebuild
playwright-cli $S goto "http://localhost:$PORT/#3" >/dev/null
playwright-cli $S click "#step-last" >/dev/null
expect "lesson 3: three steps" '"steps":3'
expect "lesson 3: five classes after rebuild" '"classes":5'
expect "lesson 3: six nodes after rebuild" '"nodes":6'
playwright-cli $S click "#step-prev" >/dev/null
expect "lesson 3: dirty after the union" '"dirty":true'
# lesson 6: the cost picker changes the answer
playwright-cli $S goto "http://localhost:$PORT/#6" >/dev/null
playwright-cli $S run-code "async page => { await page.waitForSelector('#run-status[data-status=done]', {state: 'attached', timeout: 120000}); }" >/dev/null
playwright-cli $S click "input[type=radio][value=prefer-shift]" >/dev/null
best=$(playwright-cli $S --raw eval "document.querySelector('#best-term').textContent")
if echo "$best" | grep -q '\[:<< :a 1\]'; then echo "ok    lesson 6: prefer shifts extracts a<<1"; else echo "FAIL  lesson 6: $best"; exit 1; fi
playwright-cli $S click "input[type=radio][value=prefer-add]" >/dev/null
best=$(playwright-cli $S --raw eval "document.querySelector('#best-term').textContent")
if echo "$best" | grep -q '\[:+ :a :a\]'; then echo "ok    lesson 6: prefer additions extracts a + a"; else echo "FAIL  lesson 6: $best"; exit 1; fi
# lesson 2: the tree has seven nodes, the graph four
playwright-cli $S goto "http://localhost:$PORT/#2" >/dev/null
tn=$(playwright-cli $S --raw eval "document.querySelectorAll('.tnode').length")
if echo "$tn" | grep -q '^7$'; then echo "ok    lesson 2: seven tree nodes"; else echo "FAIL  lesson 2 tree: $tn"; exit 1; fi
expect "lesson 2: four graph nodes" '"nodes":4'
echo "e2e: lessons 2, 3, 6, 7 and the REPL pass"
