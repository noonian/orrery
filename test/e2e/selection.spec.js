// The opened class: click a class, a #id, a tree node, or a link in
// the prose, and the replay bar, stuck to the top of the viewport,
// says what the engine knows about it. Counts and costs, never ids.
import { test, expect } from '@playwright/test';
import { openLesson, expectSnapshot } from './orrery.js';

const detail = (page) => page.locator('#replay .class-detail');
const act = (page, kind, text) => page.locator(`.prose a.act[data-act=${kind}]`, { hasText: text });

test.describe('the opened class', () => {
  test('lesson 6: three nodes with costs; the cheapest moves with the cost; three terms', async ({ page }) => {
    await openLesson(page, 6);
    await page.locator('tbody tr.root .class-id').click();
    await expect(detail(page)).toBeVisible();
    await expect(detail(page).locator('.nodes .dnode')).toHaveCount(3);
    // the lesson opens under "prefer additions", its first cost
    await expect(detail(page).locator('.nodes .dnode .cost')).toHaveText(['3', '12', '12']);
    await expect(detail(page).locator('.nodes .dnode.best')).toContainText('+');
    await expect(detail(page).locator('.terms')).toContainText('3 terms');
    await expect(detail(page).locator('.terms .costed')).toHaveCount(3);
    await expect(detail(page).locator('.terms .costed .notation').first()).toHaveText('a + a');

    await page.locator('input[type=radio][value=ast-size]').check();
    await expect(detail(page).locator('.nodes .dnode .cost')).toHaveText(['3', '3', '3']);

    await page.locator('input[type=radio][value=prefer-shift]').check();
    await expect(detail(page).locator('.nodes .dnode .cost')).toHaveText(['3', '12', '12']);
    await expect(detail(page).locator('.nodes .dnode.best')).toContainText('<<');
    await expect(detail(page).locator('.terms .costed .notation').first()).toHaveText('a << 1');

    await act(page, 'cost', 'Charge multiplications and shifts').click();
    await expect(page.locator('#best-notation')).toHaveText('a + a');
    await expect(detail(page).locator('.terms .costed .notation').first()).toHaveText('a + a');
    await expect(detail(page).locator('.history')).toContainText('first at step 0; gained nodes at step 1, step 2');

    await page.locator('.class-detail button.close').click();
    await expect(page.locator('.class-detail')).toHaveCount(0);
    await expect(page.locator('tbody tr.selected')).toHaveCount(0);
  });

  test('lesson 7: the input class has thirty nodes and stands for 1680 arrangements', async ({ page }) => {
    await openLesson(page, 7);
    await act(page, 'select', "Open the input's class").click();
    await expect(detail(page).locator('.nodes .dnode')).toHaveCount(30);
    await expect(detail(page).locator('.terms')).toContainText('1680 terms');
    await expect(detail(page).locator('.terms .costed .cost')).toHaveText(['9', '9', '9', '9', '9', '9']);
    await expect(detail(page).locator('.parents')).toContainText('nothing');
    await expect(page.locator('tbody tr.selected')).toHaveCount(1);
    await expect(page.locator('tbody tr .rel')).toHaveCount(30);
    // the bar is sticky: scrolled to the foot of the list, the opened class is still in view
    await page.locator('table.classes tbody tr').last().scrollIntoViewIfNeeded();
    await expect(detail(page)).toBeInViewport();
    await expect(page.locator('#scrubber-range')).toBeInViewport();
  });

  test('lesson 10: one opened class in the bar over the two panels of the fork', async ({ page }) => {
    await openLesson(page, 10);
    const panels = page.locator('.fork > .panel');
    await panels.nth(1).locator('tbody tr.root .class-id').click();
    await expect(page.locator('.class-detail')).toHaveCount(1);
    await expect(detail(page)).toBeVisible();
    await expect(detail(page).locator('.detail-title')).toContainText("the input's class");
    await expect(panels.nth(1).locator('tbody tr.selected')).toHaveCount(1);
    await expect(panels.locator('.class-detail')).toHaveCount(0);
  });

  test('lesson 3: the merged class has two parents that read the same until the rebuild, then one', async ({ page }) => {
    await openLesson(page, 3);
    await act(page, 'select', 'open the merged class').click();
    await expectSnapshot(page, { step: 1, dirty: true });
    await expect(detail(page).locator('.nodes .dnode')).toHaveCount(2);
    await expect(detail(page).locator('.parents .dnode')).toHaveCount(2);
    await expect(detail(page).locator('.history')).toContainText('classes became one at step 1');
    await page.locator('#step-next').click();
    await expectSnapshot(page, { step: 2 });
    await expect(detail(page).locator('.parents .dnode')).toHaveCount(1);
    await page.locator('#step-first').click();
    await expectSnapshot(page, { step: 0 });
    await expect(detail(page).locator('.nodes .dnode')).toHaveCount(1);
  });

  test('lesson 1: a tree node opens its class, a #id inside the panel navigates', async ({ page }) => {
    await openLesson(page, 1);
    await page.locator('.tnode > .tlabel').first().click();
    await expect(detail(page)).toBeVisible();
    await expect(detail(page).locator('.detail-title')).toContainText("the input's class");
    await expect(detail(page).locator('.children .ref')).toHaveCount(2);
    await expect(detail(page).locator('.parents')).toContainText('nothing');
    await detail(page).locator('.children .ref').first().click();
    await expect(detail(page).locator('.parents .dnode')).toHaveCount(1);
    await expect(detail(page).locator('.history')).toHaveCount(0);
  });

  test('lesson 9: a class that reaches itself stands for infinitely many terms', async ({ page }) => {
    await openLesson(page, 9);
    await act(page, 'select', 'Open the class of sin²x').click();
    await expectSnapshot(page, { step: 4 });
    await expect(detail(page).locator('.terms')).toContainText('infinitely many');
    await expect(detail(page).locator('.terms .costed').first().locator('.notation')).toHaveText('sin²x');
  });

  test('a prose link whose term the edited input no longer holds is plain text', async ({ page }) => {
    await openLesson(page, 6);
    await expect(act(page, 'select', 'Open the class')).toHaveCount(1);
    await page.locator('#input-term').fill('[:+ :b :b]');
    await page.locator('#run').click();
    await expectSnapshot(page, { status: 'done' });
    await expect(act(page, 'select', 'Open the class')).toHaveCount(0);
    await expect(page.locator('.prose')).toContainText('Open the class');
  });
});
