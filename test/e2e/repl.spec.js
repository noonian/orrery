import { test, expect } from '@playwright/test';
import { openLesson, expectSnapshot, evalRepl } from './orrery.js';

test.describe('the REPL', () => {
  test('g is the e-graph on show and timeline the whole run', async ({ page }) => {
    await openLesson(page, 7);
    await expect((await evalRepl(page, '(eg/class-count g)')).locator('pre.result')).toHaveText('31');
    await expect((await evalRepl(page, '(count timeline)')).locator('pre.result')).toHaveText('8');
    await page.locator('#step-first').click();
    await expect((await evalRepl(page, '(eg/class-count g)')).locator('pre.result')).toHaveText('9');
    await expect((await evalRepl(page, '(second (eg/add g [:+ :a0 :a1]))')).locator('pre.result')).toHaveText(/^\d+$/);
    await expect(page.locator('#repl-input')).toHaveValue('');
  });

  test('an e-graph result renders as a class list, and "show it" scrubs to it', async ({ page }) => {
    await openLesson(page, 7);
    const pair = await evalRepl(page, '(eg/add g [:+ :a0 :a1])');
    await expect(pair.locator('.summary')).toHaveText(/^\[g \d+\]: 31 classes, 185 nodes$/);
    await expect(pair.locator('tbody tr.selected')).toHaveCount(1);
    const graph = await evalRepl(page, '(first (eg/add g [:* :a0 2]))');
    await expect(graph.locator('.summary')).toHaveText('33 classes, 187 nodes');
    await graph.getByRole('button', { name: 'show it' }).click();
    await expectSnapshot(page, { steps: 1, step: 0, classes: 33, nodes: 187 });
    await expect(page.locator('.scrubber-label')).toHaveText('from the REPL · 0 of 0');
  });

  test('a runner result offers "scrub it"', async ({ page }) => {
    await openLesson(page, 4);
    const run = await evalRepl(page,
      '(rw/embiggen (first (eg/add (eg/egraph) [:+ :a :b])) (lessons/rules-of lessons/ac-rules) {:iter-limit 5 :timeline? true})');
    await expect(run.locator('.summary')).toContainText('saturated');
    await run.getByRole('button', { name: 'scrub it' }).click();
    await expectSnapshot(page, { stopReason: 'saturated', classes: 3, nodes: 4 });
    await expect(page.locator('.scrubber-label')).toContainText('iteration');
  });

  test('an error is shown, not thrown; clear empties the history; Ctrl-Enter evaluates', async ({ page }) => {
    await openLesson(page, 3);
    await expect((await evalRepl(page, '(nope g)')).locator('pre.error')).toContainText('nope');
    await page.locator('#repl-clear').click();
    await expect(page.locator('#repl-dock .entry')).toHaveCount(0);
    await page.locator('#repl-buffer').fill('(+ 1 2)');
    await page.locator('#repl-buffer').press('Control+Enter');
    await expect(page.locator('#repl-dock .entry pre.result')).toHaveText('3');
  });
});
