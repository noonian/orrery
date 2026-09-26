import { test, expect } from '@playwright/test';
import { openLesson, expectSnapshot, tryAnother, column } from './orrery.js';

test.describe('9. A rule over the polynomial', () => {
  test('sin²x + cos²x buried in a sum collapses to 1', async ({ page }) => {
    await openLesson(page, 9);
    await expectSnapshot(page, { iterations: 3, stopReason: 'saturated', steps: 5, step: 4, classes: 15, nodes: 23 });
    await expect(page.locator('#best-lay')).toHaveText('a + b + 1');
    await expect(page.locator('tbody tr.root td.poly')).toHaveText('a + b + 1');
    await expect(page.locator('.best .changed')).toHaveText("cost 4 under bendix's default");
    expect((await column(page.locator('table.stats'), 'pythagoras')).map(s => s.split(' / ')[0])).toEqual(['5', '2', '0']);
    await expect(page.locator('table.stats td.applied')).toHaveCount(2);

    await page.locator('#step-first').click();
    await expectSnapshot(page, { step: 0 });
    await expect(page.locator('tbody tr.root td.poly')).toHaveText('sin²x + cos²x + a + b');
  });

  test('try another: 1 − cos²x creates the sine; a cofactor; merged arguments', async ({ page }) => {
    await openLesson(page, 9);
    await tryAnother(page, '1 − cos²x');
    await expectSnapshot(page, { status: 'done', iterations: 4, classes: 10, nodes: 15 });
    await expect(page.locator('#best-lay')).toHaveText('sin²x');
    await tryAnother(page, 'with a cofactor');
    await expectSnapshot(page, { status: 'done', classes: 16, nodes: 25 });
    await expect(page.locator('#best-lay')).toHaveText('y');
    await tryAnother(page, 'sin(x + y) and cos(y + x)');
    await expectSnapshot(page, { status: 'done', classes: 12, nodes: 18 });
    await expect(page.locator('#best-lay')).toHaveText('1');
  });
});
