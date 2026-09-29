import { test, expect } from '@playwright/test';
import { openLesson, expectSnapshot, tryAnother, submit, showTab } from './orrery.js';

test.describe('4. A rule', () => {
  test('one iteration: two matches, then three new nodes in one new class', async ({ page }) => {
    await openLesson(page, 4);
    await expectSnapshot(page, { iterations: 1, stopReason: 'iter-limit', step: 1, classes: 7, nodes: 9 });
    await expect(page.locator('#tile-iteration .tile-note')).toHaveText('stopped at the iteration limit');
    await expect(page.locator('tbody tr.new-class')).toHaveCount(1);
    await expect(page.locator('.node.added')).toHaveCount(3);

    await page.locator('#step-first').click();
    await expectSnapshot(page, { step: 0, classes: 6, nodes: 6 });
    await expect(page.locator('#matches .rule-matches')).toHaveCount(1);
    await expect(page.locator('#matches .count')).toContainText('2 matches');
    await expect(page.locator('#matches li')).toHaveCount(2);
    await expect(page.locator('tbody tr.match')).toHaveCount(2);
    await expect(page.locator('tbody tr.new-class')).toHaveCount(0);
  });

  test('the best term under prefer shifts uses the rule twice', async ({ page }) => {
    await openLesson(page, 4);
    await expect(page.locator('.cost-picker input[type=radio]')).toHaveCount(2);
    await showTab(page, 'results');
    await page.locator('input[type=radio][value=prefer-shift]').check();
    await expect(page.locator('#best-term')).toHaveText('[:+ [:<< :a 1] [:<< :b 1]]');
    await expect(page.locator('#best-notation')).toHaveText('(a << 1) + (b << 1)');
  });

  test('edited rules: commutativity on a + b', async ({ page }) => {
    await openLesson(page, 4);
    await submit(page, { term: '[:+ :a :b]', rules: '[["comm" [:+ ?a ?b] [:+ ?b ?a]]]' });
    await expectSnapshot(page, { status: 'done', iterations: 1, classes: 3, nodes: 4 });
    await page.locator('#step-first').click();
    await expect(page.locator('#matches .count')).toContainText('1 match');
  });

  test('rules that do not hold together show the problem', async ({ page }) => {
    await openLesson(page, 4);
    await submit(page, { rules: '[["comm" [:+ ?a ?b] [:+ ?b ?c]]]' });
    await expect(page.locator('.input-area .error')).toContainText('the rules');
    await expect(page.locator('.input-area .error')).toContainText('?c');
    await submit(page, { rules: '[["comm" [:+ ?a ?b]]]' });
    await expect(page.locator('.input-area .error')).toContainText('a rule is ["name" lhs rhs]');
    await expectSnapshot(page, { classes: 7, nodes: 9 });
  });
});
