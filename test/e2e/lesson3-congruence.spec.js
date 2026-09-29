import { test, expect } from '@playwright/test';
import { openLesson, expectSnapshot, tryAnother, submit , showTab } from './orrery.js';

test.describe('3. Equality and congruence', () => {
  test('three steps: the terms, the assertion, the rebuild', async ({ page }) => {
    await openLesson(page, 3);
    await expectSnapshot(page, { steps: 3, step: 2, classes: 5, nodes: 6, dirty: false });
    await expect(page.locator('.scrubber-label')).toHaveText('after rebuild · 2 of 2');
    await expect(page.locator('.badge.dirty')).toHaveCount(0);

    await page.locator('#step-prev').click();
    await expectSnapshot(page, { step: 1, classes: 6, dirty: true });
    await expect(page.locator('.scrubber-label')).toContainText('a·2 = a << 1 asserted; rebuild pending');
    await showTab(page, 'results');
    await expect(page.locator('.badge.dirty')).toBeVisible();

    await page.locator('#step-first').click();
    await expectSnapshot(page, { step: 0, classes: 7, dirty: false });
    await expect(page.locator('.scrubber-label')).toHaveText('the two terms · 0 of 2');

    await page.locator('#step-last').click();
    await expectSnapshot(page, { step: 2, classes: 5 });
  });

  test('the transport disables at the ends', async ({ page }) => {
    await openLesson(page, 3);
    await expect(page.locator('#step-last')).toBeDisabled();
    await expect(page.locator('#step-next')).toBeDisabled();
    await expect(page.locator('#step-first')).toBeEnabled();
    await page.locator('#step-first').click();
    await expect(page.locator('#step-first')).toBeDisabled();
    await expect(page.locator('#step-prev')).toBeDisabled();
    await expect(page.locator('#step-next')).toBeEnabled();
    await page.locator('#step-next').click();
    await expectSnapshot(page, { step: 1 });
  });

  test('try another: x + 0 = x, under a sine', async ({ page }) => {
    await openLesson(page, 3);
    await tryAnother(page, 'x + 0 = x, under a sine');
    await expectSnapshot(page, { status: 'done', steps: 3, classes: 3, nodes: 4 });
    await page.locator('#step-first').click();
    await expectSnapshot(page, { classes: 5 });
  });

  test('an edited wrapper pattern runs; one that does not read shows the problem', async ({ page }) => {
    await openLesson(page, 3);
    await submit(page, { wrapper: '[:* [:+ ?x 1] [:+ ?x 1]]' });
    await expectSnapshot(page, { status: 'done', classes: 6, nodes: 7 });
    await submit(page, { wrapper: '[:sin' });
    await expect(page.locator('.input-area .error')).toContainText('the term over each side: could not read that');
    await expectSnapshot(page, { classes: 6, nodes: 7 });
  });
});
