import { test, expect } from '@playwright/test';
import { openLesson, expectSnapshot, tryAnother, column } from './orrery.js';

const act = (page, kind, text) => page.locator(`.prose a.act[data-act=${kind}]`, { hasText: text });

test.describe('10. A rule over the polynomial', () => {
  test('sin²x + cos²x buried in a sum collapses to 1', async ({ page }) => {
    await openLesson(page, 10);
    await expectSnapshot(page, { iterations: 3, stopReason: 'saturated', steps: 5, step: 4, classes: 15, nodes: 23 });
    await expect(page.locator('#best-notation')).toHaveText('a + b + 1');
    await expect(page.locator('tbody tr.root td.poly')).toHaveText('a + b + 1');
    await expect(page.locator('.best .changed')).toHaveText("cost 4 under bendix's default");
    expect((await column(page.locator('table.stats'), 'pythagoras')).map(s => s.split(' / ')[0])).toEqual(['5', '2', '0']);
    await expect(page.locator('table.stats td.applied')).toHaveCount(2);

    await page.locator('#step-first').click();
    await expectSnapshot(page, { step: 0 });
    await expect(page.locator('tbody tr.root td.poly')).toHaveText('sin²x + cos²x + a + b');
  });

  test('try another: 1 − cos²x creates the sine; a cofactor; merged arguments', async ({ page }) => {
    await openLesson(page, 10);
    await tryAnother(page, '1 − cos²x');
    await expectSnapshot(page, { status: 'done', iterations: 4, classes: 10, nodes: 15 });
    await expect(page.locator('#best-notation')).toHaveText('sin²x');
    await tryAnother(page, 'with a cofactor');
    await expectSnapshot(page, { status: 'done', classes: 16, nodes: 25 });
    await expect(page.locator('#best-notation')).toHaveText('y');
    await tryAnother(page, 'sin(x + y) and cos(y + x)');
    await expectSnapshot(page, { status: 'done', classes: 12, nodes: 18 });
    await expect(page.locator('#best-notation')).toHaveText('1');
  });

  test('the reduction worked through: sin²x becomes 1 − cos²x, and the sum collapses', async ({ page }) => {
    await openLesson(page, 10);
    const w = page.locator('.prose .working-out[data-op=reduction]');
    await expect(w.locator('.step').first()).toHaveText('sin²x → 1 − cos²x');
    await expect(w.locator('.working-result')).toHaveText('= a + b + 1');
  });

  test('the opened class of sin²x shows two polynomials and the one it keeps', async ({ page }) => {
    await openLesson(page, 10);
    await act(page, 'select', 'Open the class of sin²x').click();
    const workings = page.locator('#replay .class-detail .workings');
    await expect(workings.locator('.working')).toHaveCount(2);
    await expect(workings.locator('.kept')).toHaveCount(1);
    await expect(workings.locator('.working', { has: page.locator('.kept') })).toContainText('= sin²x');
    await expect(workings).toContainText('sin x');
    await expect(workings.locator('.verdict')).toContainText('because it has fewer monomials');
  });
});
