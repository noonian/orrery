import { test, expect } from '@playwright/test';
import { openLesson, expectSnapshot, tryAnother, showTab, evalRepl } from './orrery.js';

const steps = (page) => page.locator('#derivation li.step');
const verdict = (page) => page.locator('#derivation-verdict');
const act = (page, kind, text) => page.locator(`.prose a.act[data-act=${kind}]`, { hasText: text }).first();

test.describe('13. Why are they equal?', () => {
  test('the derivation of (a·2)/2 = a: regroup, cancel inside the product, mul-1, all checked', async ({ page }) => {
    await openLesson(page, 13);
    await expectSnapshot(page, { iterations: 4, stopReason: 'saturated', classes: 4, nodes: 8, bestCost: 1 });
    await expect(page.locator('#explanation')).toBeVisible();
    await expect(steps(page)).toHaveCount(3);
    await expect(steps(page).locator('.rule-name')).toHaveText(['regroup', 'cancel', 'mul-1']);
    await expect(steps(page).nth(1).locator('.local')).toBeVisible();
    await expect(steps(page).last().locator('.term')).toHaveText('a');
    await expect(verdict(page)).toHaveAttribute('data-ok', 'true');
    await expect(verdict(page)).toHaveAttribute('data-trusted', '0');
  });

  test('scrubbing grows the derivation a step per iteration', async ({ page }) => {
    await openLesson(page, 13);
    await page.locator('#step-first').click();
    await expect(page.locator('#explanation-none')).toBeVisible();
    for (const [k, n] of [[1, 1], [2, 2], [3, 3]]) {
      await act(page, 'step', ['After one iteration', 'After two', 'after three'][k - 1]).click();
      await expectSnapshot(page, { step: k });
      await expect(steps(page)).toHaveCount(n);
    }
  });

  test('a wrong rule: the checker accepts what follows from it', async ({ page }) => {
    await openLesson(page, 13);
    await tryAnother(page, 'a wrong rule: ?x·2 → ?x');
    await expectSnapshot(page, { status: 'done', iterations: 2 });
    await showTab(page, 'egraph');
    await expect(steps(page)).toHaveCount(1);
    await expect(steps(page).locator('.rule-name')).toHaveText('wrong');
    await expect(verdict(page)).toHaveAttribute('data-ok', 'true');
  });

  test('twice over takes the long way round', async ({ page }) => {
    await openLesson(page, 13);
    await tryAnother(page, 'twice over: ((a·2)/2·2)/2');
    await expectSnapshot(page, { status: 'done', iterations: 4 });
    await showTab(page, 'egraph');
    await expect(steps(page)).toHaveCount(16);
    await expect(steps(page).locator('.backwards').first()).toBeVisible();
    await expect(verdict(page)).toHaveAttribute('data-ok', 'true');
  });

  test('an asserted union is a step the checker takes on trust', async ({ page }) => {
    await openLesson(page, 13);
    const entry = await evalRepl(page,
      '(derivation/check (let [g (eg/egraph {:explain? true}) [g a] (eg/add g :a) [g b] (eg/add g :b) ' +
      '[g sa] (eg/add g [:sin :a]) [g sb] (eg/add g [:sin :b]) ' +
      'g (eg/rebuild (first (eg/union g a b "I say so")))] (explain/explain g sa sb)) [])');
    await expect(entry.locator('pre.error')).toHaveCount(0);
    await expect(entry.locator('pre.result')).toContainText(':ok? true');
    await expect(entry.locator('pre.result')).toContainText('I say so');
  });
});
