import { test, expect } from '@playwright/test';
import { openLesson, expectSnapshot, tryAnother } from './orrery.js';

const rootPoly = (page) => page.locator('tbody tr.root td.poly');
const detail = (page) => page.locator('#replay .class-detail');
const act = (page, kind, text) => page.locator(`.prose a.act[data-act=${kind}]`, { hasText: text });

test.describe('9. Inside the polynomial', () => {
  test('two subterms worth x² merge when the term is added, and the whole is worth 0', async ({ page }) => {
    await openLesson(page, 9);
    await expectSnapshot(page, { iterations: 1, stopReason: 'saturated', steps: 3, step: 2, classes: 9, nodes: 14 });
    await expect(page.locator('#best-notation')).toHaveText('0');
    await expect(rootPoly(page)).toHaveText('0');

    await act(page, 'select', 'Open the class of x·x').click();
    await expectSnapshot(page, { step: 0, classes: 7, nodes: 8 });
    await expect(detail(page).locator('.nodes .dnode')).toHaveCount(2);
    await expect(page.locator('tbody tr.selected td.poly')).toHaveText('x²');
    // how the class got its polynomial: each node read over its children's polynomials
    const workings = detail(page).locator('.workings');
    await expect(workings.locator('.working')).toHaveCount(2);
    await expect(workings.locator('.working', { hasText: 'x·x' })).toContainText('= x²');
    await expect(workings.locator('.verdict')).toHaveText('Every node makes the same polynomial, so the nodes agree.');
  });

  test('the product worked through: four cells, two that cancel', async ({ page }) => {
    await openLesson(page, 9);
    const w = page.locator('.prose .working-out[data-op=product]');
    await expect(w.locator('td')).toHaveCount(4);
    await expect(w.locator('td.cancels')).toHaveCount(2);
    await expect(w.locator('.like')).toContainText('like terms that cancel');
    await expect(w.locator('.working-result')).toHaveText('= x² − 1');
  });

  test('try another: an atom, too big, x/x, and the two spellings of a square', async ({ page }) => {
    await openLesson(page, 9);
    await tryAnother(page, 'sin(x + y) − sin(y + x)');
    await expectSnapshot(page, { status: 'done', classes: 5, nodes: 7 });
    await expect(page.locator('#best-notation')).toHaveText('0');
    await expect(page.locator('table.classes td.poly', { hasText: 'its own atom' })).toHaveCount(1);

    await tryAnother(page, '(a + b + c + d)²⁰');
    await expectSnapshot(page, { status: 'done', classes: 7, nodes: 7 });
    await expect(rootPoly(page)).toHaveText('too big: the analysis gave up');

    await tryAnother(page, 'x/x');
    await expectSnapshot(page, { status: 'done', classes: 2, nodes: 2 });
    await expect(rootPoly(page)).toContainText('its own atom');
    await expect(page.locator('#best-notation')).toHaveText('x/x');

    await tryAnother(page, '(x + y)²');
    await expectSnapshot(page, { status: 'done', classes: 8, nodes: 9 });
    await expect(page.locator('#best-notation')).toHaveText('(x + y)²');

    await tryAnother(page, 'x² + 2·x·y + y²');
    await expectSnapshot(page, { status: 'done', classes: 7, nodes: 8 });
    await expect(page.locator('#best-notation')).not.toContainText('(');
  });
});
