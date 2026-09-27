// The REPL's page, after the lessons: the dock open under every panel
// that reads an e-graph, the documentation as lines to evaluate, the
// page's state as an atom the REPL holds. Counts, never ids.
import { test, expect } from '@playwright/test';
import { openLesson, expectSnapshot, evalRepl, openRepl } from './orrery.js';

const entries = (page) => page.locator('#repl-dock .entry');
const result = async (page, code) => (await evalRepl(page, code)).locator('pre.result');
const curated = { lesson: 'repl', status: 'done', iterations: 3, stopReason: 'saturated', step: 3, classes: 4, nodes: 6 };

test.describe('the REPL\'s page', () => {
  test('at #repl: the dock open under every panel, over the run of a + a', async ({ page }) => {
    await openLesson(page, 'repl');
    await expectSnapshot(page, curated);
    await expect(page.locator('.lesson h2')).toHaveText('The REPL');
    await expect(page.locator('.lesson-nav a.current')).toHaveText('The REPL');
    await expect(page.locator('.lesson h2')).toBeInViewport();   // the address names no element to scroll to
    await expect(page.locator('#repl-dock.open #repl-history')).toBeVisible();
    // no fields: the editor is the input
    await expect(page.locator('#run')).toHaveCount(0);
    await expect(page.locator('.field')).toHaveCount(0);
    await expect(page.locator('textarea')).toHaveCount(2);
    await expect(page.getByRole('textbox', { name: "the REPL's line" })).toHaveId('repl-input');
    await expect(page.getByRole('textbox', { name: "the REPL's buffer" })).toHaveId('repl-buffer');
    await expect(page.locator('.prose')).toContainText('type code on the REPL\'s line and press Enter');
    await expect(page.locator('.prose')).toContainText('Ctrl-Enter evaluates the form at the caret');
    await expect(page.locator('.lesson')).not.toContainText(/prompt/i);
    await expect(page.locator('.panel h3')).toHaveText(
      ['the tree', 'the classes', 'the run', 'best so far', 'matches in this step', 'the iterations']);
    await expect(page.locator('#graph svg')).toBeVisible();
    await expect(page.locator('.cost-picker input[type=radio]')).toHaveCount(7);
    await expect(page.locator('table.stats tbody tr')).toHaveCount(3);
    await page.locator('#repl-help-toggle').click();
    for (const name of ['g', 'timeline', 'state', 'show!', 'push!', 'eg', 'rw', 'ex', 'bx', 'wb']) {
      await expect(page.locator('#names dt', { hasText: new RegExp(`^${name.replace('!', '\\!')}$`) })).toHaveCount(1);
    }
  });

  test('every line of the documentation evaluates, in reading order', async ({ page }) => {
    await openLesson(page, 'repl');
    const lines = page.locator('.prose a.act[data-act=eval]');
    const n = await lines.count();
    expect(n).toBeGreaterThan(12);
    for (let i = 0; i < n; i++) {
      const code = await lines.nth(i).textContent();
      await lines.nth(i).click();
      await expect(entries(page)).toHaveCount(i + 1);
      await expect(entries(page).last().locator('pre.in')).toHaveText(`user=> ${code}`);
      await expectSnapshot(page, { status: 'done' });
      // one line is there to be refused
      const refused = code.includes(':step 99');
      await expect(entries(page).last().locator('pre.error'), code).toHaveCount(refused ? 1 : 0);
      if (refused) await expect(entries(page).last().locator('pre.error')).toContainText(':step is a step of the run');
    }
    // the editor keeps what was in it, and the last line read the notation
    await expect(page.locator('#repl-input')).toHaveValue('');
    await expect(entries(page).last().locator('pre.result')).toHaveText('{:term [:+ [:* 2 :x] :y]}');
  });

  test('what the documentation says happens, happens', async ({ page }) => {
    await openLesson(page, 'repl');
    const line = (text) => page.locator('.prose a.act[data-act=eval]', { hasText: text }).first();
    await line('(show! (eg/add g [:+ :b :b]))').click();
    await expectSnapshot(page, { steps: 1, step: 0, classes: 6, nodes: 8 });
    await expect(page.locator('.scrubber-label')).toHaveText('from the REPL · 0 of 0');
    await line('a = b, rebuild pending').click();
    await expectSnapshot(page, { steps: 2, step: 1, classes: 5, dirty: true });
    await line('(push! (eg/rebuild g) "rebuilt")').click();
    await expectSnapshot(page, { steps: 3, step: 2, classes: 4, nodes: 7, dirty: false });
    await expect(page.locator('.scrubber-label')).toHaveText('rebuilt · 2 of 2');
    await line('(swap! state wb/scrub 0)').click();
    await expectSnapshot(page, { steps: 3, step: 0, classes: 6 });
    await line('(show! (run/start').click();
    await expectSnapshot(page, { status: 'done', stopReason: 'saturated', iterations: 4, steps: 5, classes: 7, nodes: 15 });
    await expect(page.locator('#tree-counts')).toContainText('tree nodes 5');
    await line('(show! (bx/saturate').click();
    await expectSnapshot(page, { status: 'done', stopReason: 'saturated', iterations: 3, steps: 4, classes: 10, nodes: 15 });
    await expect(page.locator('table.classes').last().locator('thead th')).toHaveText(['class', 'nodes', 'polynomial', 'best', 'parents']);
    await expect(page.locator('table.stats thead th').nth(1)).toHaveText('pythagoras');
  });

  test('the page is an atom: a swap scrubs it, opens a class, changes the cost', async ({ page }) => {
    await openLesson(page, 'repl');
    await expect(await result(page, '(:step @state)')).toHaveText('3');
    await evalRepl(page, '(swap! state wb/scrub 1)');
    await expectSnapshot(page, { step: 1, classes: 3, nodes: 4 });
    await expect(await result(page, '(eg/class-count g)')).toHaveText('3');
    await evalRepl(page, '(swap! state wb/scrub 3)');
    await evalRepl(page, '(swap! state assoc :cost :prefer-shift)');
    await expect(page.locator('#best-notation')).toHaveText('a << 1');
    await expect(page.locator('.cost-picker input:checked')).toHaveValue('prefer-shift');
    await evalRepl(page, '(swap! state assoc-in [:ui :selected] (eclass/class-of g [:+ :a :a]))');
    await expect(page.locator('#replay .class-detail .nodes .dnode')).toHaveCount(3);
    await evalRepl(page, '(swap! state #(-> % (wb/open lessons/blowup) wb/start))');
    await expectSnapshot(page, { lesson: 'blowup', status: 'done', iterations: 7, classes: 31, nodes: 185 });
    // a swap returns the state, and the history prints it in a few lines
    const text = await entries(page).last().locator('pre.result').textContent();
    expect(text.length).toBeLessThan(2500);
    expect(text).toContain(':lesson :blowup');
  });

  test('the atom refuses what is not a state of the page, and the page stands', async ({ page }) => {
    await openLesson(page, 'repl');
    for (const [code, says] of [
      ['(reset! state nil)', 'not a state of the page: the state of the page is a map'],
      ['(swap! state assoc :step 99)', ':step is a step of the run, 0 to 3: 99'],
      ['(swap! state assoc :cost :cheapest)', ':cost names no cost: :cheapest'],
      ['(swap! state assoc :run g)', ':run is a run'],
      ['(swap! state assoc :lesson :nowhere)', ':lesson names no page'],
      ['(show! 42)', 'show! takes an e-graph'],
      ['(push! "g")', 'push! takes an e-graph'],
    ]) {
      await expect((await evalRepl(page, code)).locator('pre.error'), code).toContainText(says);
      await expectSnapshot(page, curated);
    }
    await expect(page.locator('table.classes tbody tr')).toHaveCount(4);
  });

  test('what is printed stands over the value; *1; doc; what cannot be printed is an error of its evaluation', async ({ page }) => {
    await openLesson(page, 'repl');
    const hello = await evalRepl(page, '(println "hello" (+ 1 2))');
    await expect(hello.locator('pre.out')).toHaveText('hello 3\n');
    await expect(hello.locator('pre.result')).toHaveText('nil');
    await expect(await result(page, '(+ 1 2)')).toHaveText('3');
    await expect(await result(page, '(* 2 *1)')).toHaveText('6');
    await expect(await result(page, '[*1 *2]')).toHaveText('[6 3]');
    const doc = await evalRepl(page, '(doc eg/union)');
    await expect(doc.locator('pre.out')).toContainText('cromulent.core/union');
    await expect(doc.locator('pre.out')).toContainText('Assert that the classes of a and b are equal');
    await expect((await evalRepl(page, '(dir ex)')).locator('pre.out')).toContainText('extract');
    // the page's own functions say what they are, like any other
    const show = await evalRepl(page, '(doc show!)');
    await expect(show.locator('pre.out')).toContainText('user/show!');
    await expect(show.locator('pre.out')).toContainText('([v])');
    await expect(show.locator('pre.out')).toContainText('Puts `v` on show. `v` is an e-graph, a [g id] pair');
    await expect((await evalRepl(page, '(doc push!)')).locator('pre.out')).toContainText('([g] [g label])');
    await expect(await result(page, '(range)')).toHaveText(/^\(0 1 2 .* 47 …\)$/);
    await expect(await result(page, 'timeline')).toHaveText(
      '[#egraph[2 classes, 2 nodes] #egraph[3 classes, 4 nodes] #egraph[4 classes, 6 nodes] #egraph[4 classes, 6 nodes]]');
    // a lazy value is walked while its evaluation can still catch what it throws
    const boom = await evalRepl(page, '(map (fn [x] (println "walking" x) (throw (ex-info "boom" {}))) [1 2])');
    await expect(boom.locator('pre.error')).toContainText('boom');
    await expect(boom.locator('pre.out')).toContainText('walking 1');
    await expect((await evalRepl(page, '(ex-message *e)')).locator('pre.result')).toHaveText('"boom"');
    await expect((await evalRepl(page, '(+ 1')).locator('pre.error')).toContainText('EOF while reading');
    await expectSnapshot(page, curated);
  });

  test('on the line, the up arrow brings back what was evaluated, the down arrow what was being typed', async ({ page }) => {
    await openLesson(page, 'repl');
    const editor = page.locator('#repl-input');
    for (const code of ['(+ 1 1)', '(eg/node-count g)']) {
      await editor.fill(code);
      await editor.press('Enter');
    }
    await editor.fill('(eg/cla');
    await editor.press('ArrowUp');
    await expect(editor).toHaveValue('(eg/node-count g)');
    await editor.press('ArrowUp');
    await expect(editor).toHaveValue('(+ 1 1)');
    await editor.press('ArrowUp');
    await expect(editor).toHaveValue('(+ 1 1)');
    await editor.press('ArrowDown');
    await expect(editor).toHaveValue('(eg/node-count g)');
    await editor.press('ArrowDown');
    await expect(editor).toHaveValue('(eg/cla');
    await editor.press('ArrowUp');
    await editor.press('Control+Enter');
    await expect(entries(page).last().locator('pre.result')).toHaveText('6');
    await expect(editor).toHaveValue('');
    // in text of several lines the arrows move the caret until it is in the first
    await editor.fill('(+ 1\n   2)');
    await editor.press('ArrowUp');
    await expect(editor).toHaveValue('(+ 1\n   2)');
    await editor.press('ArrowUp');
    await expect(editor).toHaveValue('(eg/node-count g)');
    await expect(entries(page)).toHaveCount(3);
  });

  test('the history scrolls inside the dock, the editor staying in view', async ({ page }) => {
    await page.setViewportSize({ width: 1440, height: 900 });
    await openLesson(page, 'repl');
    for (let i = 0; i < 6; i++) await evalRepl(page, '(eg/add g [:+ :b :b])');
    await expect(page.locator('#repl-input')).toBeInViewport();
    await expect(entries(page).last()).toBeInViewport();
    await expect(entries(page).first()).not.toBeInViewport();
    const box = await page.locator('#repl-dock').boundingBox();
    expect(box.height).toBeLessThanOrEqual(900);
  });

  test('a lesson\'s REPL links to the page, which opens at its top', async ({ page }) => {
    await openLesson(page, 3);
    await page.locator('#repl-help-toggle').click();
    await page.locator('#repl-help a[href="#repl"]').click();
    await expect(page).toHaveURL(/#repl$/);
    await expectSnapshot(page, curated);
    await expect(page.locator('.lesson h2')).toHaveText('The REPL');
    // what was evaluated under a lesson is in the page's history too
    await page.goto('/#6');
    await expectSnapshot(page, { lesson: 'taste', status: 'done' });
    await evalRepl(page, '(eg/class-count g)');
    await page.locator('.lesson-nav a', { hasText: 'The REPL' }).click();
    await expectSnapshot(page, curated);
    await expect(entries(page)).toHaveCount(1);
  });
});
