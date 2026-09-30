import { test, expect } from '@playwright/test';
import { openLesson, expectSnapshot, tryAnother, showTab } from './orrery.js';

// the cost picker is in the results tab
const pick = async (page, cost) => {
  await showTab(page, 'results');
  await page.locator(`input[type=radio][value=${cost}]`).check();
};

test.describe('6. Extraction is taste', () => {
  test('three costs, three answers from one class', async ({ page }) => {
    await openLesson(page, 6);
    await expectSnapshot(page, { iterations: 3, stopReason: 'saturated', classes: 4, nodes: 6 });
    await expect(page.locator('.cost-picker input[type=radio]')).toHaveCount(4);

    await pick(page, 'prefer-add');
    await expect(page.locator('#best-term')).toHaveText('[:+ :a :a]');
    await expect(page.locator('#best-notation')).toHaveText('a + a');
    await expect(page.locator('.best .changed')).toHaveText('cost 3 under prefer additions');

    await pick(page, 'prefer-mul');
    await expect(page.locator('#best-term')).toHaveText('[:* :a 2]');
    await expect(page.locator('#best-notation')).toHaveText('a·2');

    await pick(page, 'prefer-shift');
    await expect(page.locator('#best-term')).toHaveText('[:<< :a 1]');
    await expect(page.locator('#best-notation')).toHaveText('a << 1');
    await expect(page.locator('.best .changed')).toHaveText('cost 3 under prefer shifts');
  });

  test('the opened class labels its costs under the cost in force', async ({ page }) => {
    await openLesson(page, 6);
    await page.locator('.prose a.act[data-act=select]', { hasText: 'Open the class' }).click();
    const note = page.locator('#replay .class-detail #detail-cost-note');
    await expect(note).toHaveText('costs under prefer additions, cheapest outlined');
    await page.locator('.prose a.act[data-act=cost]', { hasText: 'Charge additions and shifts' }).click();
    await expect(note).toHaveText('costs under prefer multiplications, cheapest outlined');
    await expect(page.locator('#replay .class-detail .nodes .dnode.best')).toContainText('#2');
  });

  test('try another: (b·2) + (b·2) nests the answer', async ({ page }) => {
    await openLesson(page, 6);
    await tryAnother(page, '(b·2) + (b·2)');
    await expectSnapshot(page, { status: 'done', stopReason: 'saturated' });
    await pick(page, 'prefer-shift');
    await expect(page.locator('#best-term')).toHaveText('[:<< [:<< :b 1] 1]');
    await pick(page, 'prefer-add');
    await expect(page.locator('#best-term')).toHaveText('[:+ [:+ :b :b] [:+ :b :b]]');
    await pick(page, 'prefer-mul');
    await expect(page.locator('#best-term')).toHaveText('[:* [:* :b 2] 2]');
  });
});
