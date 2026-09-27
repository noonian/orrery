// The basics, where the page opens: many ways to write one thing, for
// a reader who has met none of this, over (x + 0)·1 and two rules
// anyone can check. Counts and costs, never ids.
import { test, expect } from '@playwright/test';
import { openLesson, expectSnapshot, tryAnother, submit } from './orrery.js';

const detail = (page) => page.locator('#replay .class-detail');
const act = (page, kind, text) => page.locator(`.prose a.act[data-act=${kind}]`, { hasText: text });

test.describe('Many ways to write one thing', () => {
  test('the page opens on it, with no hash and at #basics: (x + 0)·1 is x', async ({ page }) => {
    await page.goto('/');
    await expectSnapshot(page, { lesson: 'basics', status: 'done', iterations: 2, stopReason: 'saturated',
                                 step: 2, classes: 3, nodes: 5, bestCost: 1 });
    await expect(page.locator('.lesson h2')).toHaveText('Many ways to write one thing');
    await expect(page.locator('#best-notation')).toHaveText('x');
    await expect(page.locator('#input-term')).toHaveValue('(x + 0)·1');
    await expect(page.locator('#input-rules')).toHaveValue('add-0: ?a + 0 → ?a\nmul-1: ?a·1 → ?a');
    await expect(page.locator('#surprise')).toHaveCount(0);
    await expect(page.locator('#graph svg')).toBeVisible();
    await openLesson(page, 'basics');
    await expectSnapshot(page, { classes: 3, nodes: 5 });
  });

  test('the prose walks the run: five boxes, then three, and the box that holds three ways', async ({ page }) => {
    await openLesson(page, 'basics');
    await act(page, 'step', 'At the start').click();
    await expectSnapshot(page, { step: 0, classes: 5, nodes: 5 });
    await expect(page.locator('#graph .eclass')).toHaveCount(5);
    await act(page, 'step', 'Run the rules once').click();
    await expectSnapshot(page, { step: 1, classes: 3, nodes: 5 });
    await expect(page.locator('#graph .eclass')).toHaveCount(3);
    await act(page, 'select', 'Open it').click();
    await expectSnapshot(page, { step: 1 });
    await expect(detail(page).locator('.detail-title')).toContainText("the input's class");
    await expect(detail(page).locator('.nodes .dnode')).toHaveCount(3);
    await expect(detail(page).locator('.terms')).toContainText('infinitely many terms');
    await expect(detail(page).locator('.terms .costed .notation').first()).toHaveText('x');
    await act(page, 'step', 'the second pass').click();
    await expectSnapshot(page, { step: 2, classes: 3, nodes: 5 });
    await expect(page.locator('#tile-iteration .tile-note')).toHaveText('saturated: the rules merge nothing more');
  });

  test('nothing to do, two letters, and back; then the next page', async ({ page }) => {
    await openLesson(page, 'basics');
    await act(page, 'alternative', 'Give it x·y').click();
    await expectSnapshot(page, { status: 'done', iterations: 1, stopReason: 'saturated', classes: 3, nodes: 3, bestCost: 3 });
    await expect(page.locator('#best-notation')).toHaveText('x·y');
    await act(page, 'alternative', 'give it two letters').click();
    await expectSnapshot(page, { status: 'done', iterations: 2, classes: 5, nodes: 7, bestCost: 3 });
    await expect(page.locator('#best-notation')).toHaveText('x·y');
    await act(page, 'alternative', 'put (x + 0)·1 back').click();
    await expectSnapshot(page, { status: 'done', iterations: 2, classes: 3, nodes: 5, bestCost: 1 });
    await tryAnother(page, 'twice as long: ((x + 0)·1 + 0)·1');
    await expectSnapshot(page, { status: 'done', iterations: 2, classes: 3, nodes: 5, bestCost: 1 });

    await page.locator('.prose a', { hasText: 'The next page' }).click();
    await expect(page).toHaveURL(/#intro$/);
    await expectSnapshot(page, { lesson: 'intro', status: 'done' });
    await expect(page.locator('.lesson h2')).toHaveText('What is an e-graph?');
  });

  test('an expression of your own', async ({ page }) => {
    await openLesson(page, 'basics');
    await submit(page, { term: '(y·1 + 0)·1' });
    await expectSnapshot(page, { status: 'done', stopReason: 'saturated', bestCost: 1 });
    await expect(page.locator('#best-notation')).toHaveText('y');
  });
});
