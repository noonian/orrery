import { test, expect } from '@playwright/test';
import { openLesson, expectSnapshot, tryAnother, column } from './orrery.js';

test.describe('7. The blowup', () => {
  test('five atoms saturate at 185 nodes in seven iterations', async ({ page }) => {
    await openLesson(page, 7);
    await expectSnapshot(page, { iterations: 7, stopReason: 'saturated', steps: 8, step: 7,
                                 classes: 31, nodes: 185, bestCost: 9 });
    await expect(page.locator('#tile-classes .tile-value')).toHaveText('31');
    await expect(page.locator('#tile-nodes .tile-value')).toHaveText('185');
    await expect(page.locator('#tile-iteration .tile-value')).toHaveText('7 / 7');
    await expect(page.locator('#tile-iteration .tile-note')).toHaveText('saturated: no rule can add anything');
    const stats = page.locator('table.stats');
    expect(await column(stats, 'nodes')).toEqual(['19', '45', '98', '162', '187', '185', '185']);
    expect(await column(stats, 'classes')).toEqual(['12', '22', '35', '39', '33', '31', '31']);

    await page.locator('#step-first').click();
    await expectSnapshot(page, { step: 0, classes: 9, nodes: 9 });
    await expect(page.locator('#tile-iteration .tile-value')).toHaveText('0 / 7');
  });

  test('the scrubber range moves the step', async ({ page }) => {
    await openLesson(page, 7);
    await page.locator('#scrubber-range').fill('3');
    await expectSnapshot(page, { step: 3, nodes: 98, classes: 35 });
    await expect(page.locator('table.stats tbody tr.current td').first()).toHaveText('3');
    await expect(page.locator('.scrubber-label')).toHaveText('iteration 3 · 3 of 7');
  });

  test('play walks to the end and pauses there', async ({ page }) => {
    await openLesson(page, 7);
    await page.locator('#step-first').click();
    await page.locator('#step-play').click();
    await expect(page.locator('#step-pause')).toBeVisible();
    await expectSnapshot(page, { step: 7 });
    await expect(page.locator('#step-play')).toBeVisible();
    await expect(page.locator('#step-play')).toBeDisabled();
  });

  test('try another: four atoms', async ({ page }) => {
    await openLesson(page, 7);
    await tryAnother(page, 'four atoms');
    await expectSnapshot(page, { status: 'done', iterations: 6, stopReason: 'saturated',
                                 classes: 15, nodes: 54, bestCost: 7 });
    expect(await column(page.locator('table.stats'), 'nodes')).toEqual(['14', '28', '44', '54', '54', '54']);
  });

  test('try another: six atoms under a node limit of 500', async ({ page }) => {
    await openLesson(page, 7);
    await tryAnother(page, 'six atoms under a node limit of 500');
    await expectSnapshot(page, { status: 'done', iterations: 5, stopReason: 'node-limit',
                                 classes: 92, nodes: 599, bestCost: 11 });
    await expect(page.locator('#tile-iteration .tile-note')).toHaveText('stopped at the node limit');
  });
});
