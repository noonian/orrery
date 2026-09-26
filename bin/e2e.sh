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
echo "e2e: lesson 7 passes"
