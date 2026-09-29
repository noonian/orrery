// The cheat sheet: links that load a term and its rules, costs, and
// lines of code, with little prose.
import { test, expect } from '@playwright/test';
import { openLesson, expectSnapshot, openRepl } from './orrery.js';

const act = (page, kind, text) => page.locator(`.prose a.act[data-act=${kind}]`, { hasText: text }).first();

test.describe('the cheat sheet', () => {
  test('is last in the nav, after the REPL', async ({ page }) => {
    await openLesson(page, 'cheat-sheet');
    await expect(page.locator('.lesson-nav li').last()).toHaveText('Cheat sheet');
    await expect(page.locator('h2')).toHaveText('Cheat sheet');
  });

  test('a link loads a term and its rules and runs them', async ({ page }) => {
    await openLesson(page, 'cheat-sheet');
    await act(page, 'alternative', 'a·b + a·c').click();
    await expectSnapshot(page, { lesson: 'cheat-sheet', status: 'done', iterations: 2, classes: 7, nodes: 8, stopReason: 'saturated' });
    await expect(page.locator('#input-term')).toHaveValue('a·b + a·c');
    await expect(page.locator('#best-notation')).toHaveText('a·(b + c)');
  });

  test('a cost link shows the best term under it', async ({ page }) => {
    await openLesson(page, 'cheat-sheet');
    await act(page, 'alternative', 'a + a').click();
    await expectSnapshot(page, { lesson: 'cheat-sheet', status: 'done', nodes: 6 });
    await act(page, 'cost', 'prefer shifts').click();
    await expect(page.locator('#pane-results')).toBeVisible();
    await expect(page.locator('#best-notation')).toHaveText('a << 1');
  });

  test('every line of code evaluates without an error', async ({ page }) => {
    await openLesson(page, 'cheat-sheet');
    await openRepl(page);
    const lines = page.locator('.prose a.act[data-act=eval]');
    const n = await lines.count();
    expect(n).toBeGreaterThan(10);
    for (let i = 0; i < n; i++) {
      await lines.nth(i).click();
      await expect(page.locator('#repl-dock .entry')).toHaveCount(i + 1);
    }
    await expect(page.locator('#repl-dock .entry pre.error')).toHaveCount(0);
  });
});
