import { test, expect } from '@playwright/test';
import { openLesson, expectSnapshot, tryAnother, submit } from './orrery.js';

const pick = async (page, cost) => page.locator(`input[type=radio][value=${cost}]`).check();

test.describe('11. Differentiation is simplification', () => {
  test('d/dx sin 2x under the derivative rules', async ({ page }) => {
    await openLesson(page, 11);
    await expectSnapshot(page, { iterations: 3, stopReason: 'saturated', steps: 5, step: 4, classes: 6, nodes: 9 });
    await expect(page.locator('.cost-picker input[type=radio]')).toHaveCount(3);
    await expect(page.locator('input[type=radio][value=no-D]')).toBeChecked();
    await expect(page.locator('#best-term')).toHaveText('[:* 2 [:cos [:* 2 :x]]]');
    await expect(page.locator('#best-notation')).toHaveText('2·cos(2·x)');
    await expect(page.locator('.best .changed')).toHaveText('cost [0 385/64] under no D');
    await pick(page, 'bendix');
    await expect(page.locator('#best-term')).toHaveText('[:* 2 [:cos [:* 2 :x]]]');
    await expect(page.locator('.best .changed')).toHaveText("cost 385/64 under bendix's default");
    await page.locator('#step-first').click();
    await expectSnapshot(page, { step: 0, classes: 5, nodes: 5 });
    await expect(page.locator('#best-notation')).toHaveText('d/dx sin(2·x)');
  });

  test('the cost decides: bendix\'s default keeps the derivative, no-D pushes it through', async ({ page }) => {
    await openLesson(page, 11);
    await tryAnother(page, 'x·sin x');
    await expectSnapshot(page, { status: 'done', iterations: 4, classes: 7, nodes: 11 });
    await expect(page.locator('#best-term')).toHaveText('[:+ [:* :x [:cos :x]] [:sin :x]]');
    await pick(page, 'bendix');
    await expect(page.locator('#best-term')).toHaveText('[:D [:* :x [:sin :x]] :x]');
    await expect(page.locator('#best-notation')).toHaveText('d/dx (x·(sin x))');
    await pick(page, 'no-D');
    await tryAnother(page, 'x·|x|: no rule for abs');
    await expectSnapshot(page, { status: 'done', classes: 6, nodes: 7 });
    await expect(page.locator('#best-term')).toHaveText('[:+ [:* :x [:D [:abs :x] :x]] [:abs :x]]');
  });

  test('an edited function and variable', async ({ page }) => {
    await openLesson(page, 11);
    await submit(page, { term: '[:expt :x 3]' });
    await expectSnapshot(page, { status: 'done' });
    await expect(page.locator('#best-term')).toHaveText('[:* 3 [:expt :x 2]]');
    await submit(page, { var: ':y' });
    await expectSnapshot(page, { status: 'done' });
    await expect(page.locator('#best-term')).toHaveText('0');
  });
});
