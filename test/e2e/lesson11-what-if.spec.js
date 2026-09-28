import { test, expect } from '@playwright/test';
import { openLesson, expectSnapshot, tryAnother } from './orrery.js';

test.describe('11. What if', () => {
  test('the original stays at step 0 beside the copy', async ({ page }) => {
    await openLesson(page, 11);
    await expectSnapshot(page, { steps: 3, step: 2, classes: 3, nodes: 4, dirty: false });
    const panels = page.locator('.fork > .panel');
    await expect(panels).toHaveCount(2);
    await expect(panels.locator('h3')).toHaveText(['the original, step 0', 'this step: the copy, after rebuild']);
    await expect(panels.nth(0).locator('tbody tr')).toHaveCount(5);
    await expect(panels.nth(1).locator('tbody tr')).toHaveCount(3);

    await page.locator('#step-prev').click();
    await expectSnapshot(page, { step: 1, classes: 4, dirty: true });
    await expect(panels.nth(1).locator('h3')).toContainText('x = 2 asserted; rebuild pending');
    await expect(panels.nth(0).locator('tbody tr')).toHaveCount(5);
    await expect(panels.nth(1).locator('tbody tr')).toHaveCount(4);
    await expect(page.locator('.badge.dirty')).toBeVisible();

    await page.locator('#step-first').click();
    await expectSnapshot(page, { step: 0, classes: 5 });
    await expect(panels.nth(1).locator('h3')).toHaveText('this step: the original');
  });

  test('try another: sin x + sin y, what if x = y', async ({ page }) => {
    await openLesson(page, 11);
    await tryAnother(page, 'sin x + sin y, what if x = y');
    await expectSnapshot(page, { status: 'done', steps: 3, classes: 3, nodes: 4 });
    await expect(page.locator('.fork > .panel').nth(0).locator('tbody tr')).toHaveCount(5);
  });
});
