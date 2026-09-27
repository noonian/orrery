// What the page's buttons and links do to what the REPL sees is
// traced in the REPL's history: quieter than an evaluation, with the
// code that does the same. Counts, steps and labels, never ids.
import { test, expect } from '@playwright/test';
import { openLesson, expectSnapshot, snapshot, openRepl, evalRepl, tryAnother } from './orrery.js';

const traces = (page) => page.locator('#repl-dock .trace');

test.describe('traces in the history', () => {
  test('a scrub is traced once, with code that does the same', async ({ page }) => {
    await openLesson(page, 'basics');
    await page.locator('#step-first').click();
    await page.locator('#step-next').click();
    await openRepl(page);
    await expect(traces(page)).toHaveCount(1);
    await expect(traces(page).locator('.what')).toHaveText('scrubbed to step 1');
    await expect(traces(page).locator('code.in')).toHaveText('(swap! state wb/scrub 1)');
    await expect(traces(page).locator('.says')).toHaveText(/^g: step 1 of 2, \d+ classes, \d+ nodes$/);
    await expect(page.locator('#repl-dock .entry')).toHaveCount(0);

    const there = await snapshot(page);
    const code = await traces(page).last().locator('code.in').textContent();
    await page.locator('#step-last').click();
    await expect(traces(page)).toHaveCount(1);
    await expect(traces(page).locator('.what')).toHaveText('scrubbed to step 2');
    await evalRepl(page, code);
    await expectSnapshot(page, there);
  });

  test('an alternative is traced, and its code runs the same run', async ({ page }) => {
    await openLesson(page, 'basics');
    await tryAnother(page, 'nothing to do: x·y');
    await expectSnapshot(page, { status: 'done', classes: 3 });
    const there = await snapshot(page);
    await openRepl(page);
    const trace = traces(page).last();
    await expect(trace.locator('.what')).toHaveText('tried nothing to do: x·y');
    const code = await trace.locator('code.in').textContent();
    expect(code).toBe('(swap! state wb/alternative "nothing to do: x·y")');

    await tryAnother(page, '(x + 0)·1');
    await expectSnapshot(page, { status: 'done', classes: 3, nodes: 5 });
    await evalRepl(page, code);
    await expectSnapshot(page, there);
  });

  test('opening a class is traced, and sel is its id', async ({ page }) => {
    await openLesson(page, 6);
    await page.locator('tbody tr.root .class-id').click();
    await openRepl(page);
    await expect(traces(page).last().locator('.what')).toHaveText(/^opened class \d+$/);
    const id = (await traces(page).last().locator('.what').textContent()).match(/\d+/)[0];
    await expect(traces(page).last().locator('.says')).toHaveText(`sel: ${id}`);
    await expect((await evalRepl(page, 'sel')).locator('pre.result')).toHaveText(id);
    await page.locator('.class-detail button.close').click();
    await expect(traces(page).last().locator('.what')).toHaveText('closed the class');
    await expect((await evalRepl(page, 'sel')).locator('pre.result')).toHaveText('nil');
  });

  test('another page is traced; recall and the closed dock pass over traces', async ({ page }) => {
    await openLesson(page, 'basics');
    await evalRepl(page, '(+ 1 2)');
    await page.goto('/#intro');
    await expectSnapshot(page, { lesson: 'intro', status: 'done' });
    await expect(traces(page).last().locator('.what')).toHaveText('opened What is an e-graph?');
    await expect(traces(page).last().locator('code.in')).toHaveText('(swap! state wb/visit :intro)');

    await page.locator('#repl-input').press('ArrowUp');
    await expect(page.locator('#repl-input')).toHaveValue('(+ 1 2)');

    await page.locator('#repl-toggle').click();
    await expect(page.locator('#repl-last')).toContainText('(+ 1 2)');
  });

  test('a trace goes to the buffer', async ({ page }) => {
    await openLesson(page, 'basics');
    await page.locator('#step-first').click();
    await openRepl(page);
    await traces(page).last().getByRole('button', { name: 'to buffer' }).click();
    await expect(page.locator('#repl-buffer')).toHaveValue('(swap! state wb/scrub 0)');
  });
});
