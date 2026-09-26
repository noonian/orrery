import { test, expect } from '@playwright/test';
import { openLesson, expectSnapshot, tryAnother, submit, evalRepl } from './orrery.js';

const rootPoly = (page) => page.locator('tbody tr.root td.poly');

test.describe('8. The fix', () => {
  test('the same sum and rules as lesson 7, under the analysis: one iteration, nothing merged', async ({ page }) => {
    await openLesson(page, 8);
    await expectSnapshot(page, { iterations: 1, stopReason: 'saturated', steps: 3, step: 2, classes: 12, nodes: 22 });
    await expect(page.locator('#tile-iteration .tile-note')).toHaveText('saturated: the rules merge nothing more');
    await expect(page.locator('table.classes th')).toContainText(['class', 'nodes', 'polynomial', 'best', 'parents']);
    await expect(rootPoly(page)).toHaveText('a0 + a1 + a2 + a3 + a4');
    await expect(page.locator('#best-lay')).toHaveText('a0 + a1 + a2 + a3 + a4');
    await expect(page.locator('table.stats tbody tr')).toHaveCount(1);
    await expect(page.locator('table.stats td.applied')).toHaveCount(0);
    await expect(page.locator('.scrubber-label')).toHaveText('the normal forms, written in as terms · 2 of 2');

    await page.locator('#step-prev').click();
    await expectSnapshot(page, { step: 1, classes: 12, nodes: 19 });
    await page.locator('#step-first').click();
    await expectSnapshot(page, { step: 0, classes: 9, nodes: 9 });
    await expect(rootPoly(page)).toHaveText('a0 + a1 + a2 + a3 + a4');
  });

  test('any arrangement typed has the same polynomial and the same best term', async ({ page }) => {
    await openLesson(page, 8);
    await tryAnother(page, 'another arrangement of the same sum');
    await expectSnapshot(page, { status: 'done', classes: 9, nodes: 16 });
    await expect(rootPoly(page)).toHaveText('a0 + a1 + a2 + a3 + a4');
    await submit(page, { term: '[:+ [:+ :a0 :a4] [:+ :a1 [:+ :a2 :a3]]]' });
    await expectSnapshot(page, { status: 'done', iterations: 1 });
    await expect(page.locator('#best-lay')).toHaveText('a0 + a1 + a2 + a3 + a4');
    const same = await evalRepl(page,
      '(= (second (eg/add g [:+ :a4 [:+ :a3 [:+ :a2 [:+ :a1 :a0]]]])) (second (eg/add g (lessons/sum-of 5))))');
    await expect(same.locator('pre.result')).toHaveText('true');
  });

  test('try another: six atoms stay small', async ({ page }) => {
    await openLesson(page, 8);
    await tryAnother(page, 'six atoms');
    await expectSnapshot(page, { status: 'done', iterations: 1, classes: 15, nodes: 28 });
    await expect(page.locator('#best-lay')).toHaveText('a0 + a1 + a2 + a3 + a4 + a5');
  });
});
