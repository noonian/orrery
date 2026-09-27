// A lesson's layout: the e-graph in a column that stays in view on a
// wide screen, after the text on a narrow one, and the REPL in a dock
// along the bottom that hides nothing under it.
import { test, expect } from '@playwright/test';
import { openLesson, expectSnapshot, evalRepl } from './orrery.js';

const dock = (page) => page.locator('#repl-dock');
const toggle = (page) => page.locator('#repl-toggle');

test.describe('a lesson\'s layout', () => {
  test('wide: a link in the prose opens a class in view', async ({ page }) => {
    await page.setViewportSize({ width: 1600, height: 900 });
    await openLesson(page, 3);
    const prose = page.locator('.lesson-grid > .text');
    const egraph = page.locator('.lesson-grid > .egraph');
    expect((await egraph.boundingBox()).x).toBeGreaterThan((await prose.boundingBox()).x);
    await page.locator('.prose a.act[data-act=select]').first().click();
    await expect(page.locator('.class-detail')).toBeInViewport();
    // scrolled to the foot of the page, the e-graph is still in view
    await page.locator('#colophon').scrollIntoViewIfNeeded();
    await expect(page.locator('#scrubber-range')).toBeInViewport();
  });

  test('narrow: the text, then the e-graph, then the controls', async ({ page }) => {
    await page.setViewportSize({ width: 800, height: 1000 });
    await openLesson(page, 3);
    const y = async (sel) => (await page.locator(sel).boundingBox()).y;
    expect(await y('.lesson-grid > .text')).toBeLessThan(await y('.lesson-grid > .egraph'));
    expect(await y('.lesson-grid > .egraph')).toBeLessThan(await y('.lesson-grid > .controls'));
  });

  test('the dock starts closed, one line with the editor; it opens with the button or Ctrl-`, and stays open across lessons', async ({ page }) => {
    await openLesson(page, 3);
    await expect(toggle(page)).toHaveAttribute('aria-expanded', 'false');
    await expect(page.locator('#repl-input')).toBeVisible();
    await expect(page.locator('#repl-history')).toHaveCount(0);
    expect((await dock(page).boundingBox()).height).toBeLessThan(60);
    await page.keyboard.press('Control+Backquote');
    await expect(toggle(page)).toHaveAttribute('aria-expanded', 'true');
    await expect(page.locator('#repl-history')).toBeVisible();
    await expect(page.locator('#repl-input')).toBeFocused();
    await page.locator('.lesson-nav a', { hasText: '4. A rule' }).click();
    await expectSnapshot(page, { lesson: 'rule', status: 'done' });
    await expect(toggle(page)).toHaveAttribute('aria-expanded', 'true');
    await toggle(page).click();
    await expect(page.locator('#repl-history')).toHaveCount(0);
  });

  test('closed, the dock says what was evaluated last, and a click on it opens the dock', async ({ page }) => {
    await openLesson(page, 3);
    await evalRepl(page, '(eg/class-count g)');
    await toggle(page).click();
    await expect(page.locator('#repl-last')).toHaveText(/^user=> \(eg\/class-count g\)\s+⇒\s+5$/);
    await page.locator('#repl-last').click();
    await expect(toggle(page)).toHaveAttribute('aria-expanded', 'true');
  });

  test('the ? button shows the keys and the names in scope, and hides them', async ({ page }) => {
    await openLesson(page, 3);
    await expect(page.locator('#repl-help')).toHaveCount(0);
    await page.locator('#repl-help-toggle').click();
    await expect(page.locator('#repl-help')).toBeVisible();
    await expect(page.locator('#repl-help dt kbd')).toContainText(['Enter', 'Ctrl-Enter', 'Ctrl-Shift-Enter']);
    await expect(page.locator('#names dt', { hasText: /^g$/ })).toHaveCount(1);
    await page.locator('#repl-help-close').click();
    await expect(page.locator('#repl-help')).toHaveCount(0);
  });

  test('the dock resizes from its top edge, and nothing hides under it', async ({ page }) => {
    await page.setViewportSize({ width: 1280, height: 800 });
    await openLesson(page, 3);
    await toggle(page).click();
    const before = (await dock(page).boundingBox()).height;
    const handle = await page.locator('#repl-resize').boundingBox();
    await page.mouse.move(handle.x + 100, handle.y + 2);
    await page.mouse.down();
    await page.mouse.move(handle.x + 100, handle.y - 150, { steps: 5 });
    await page.mouse.up();
    const after = (await dock(page).boundingBox()).height;
    expect(after).toBeGreaterThan(before + 100);
    // the foot of the page scrolls clear of the dock
    await page.evaluate(() => window.scrollTo(0, document.documentElement.scrollHeight));
    const foot = await page.locator('#colophon').boundingBox();
    expect(foot.y + foot.height).toBeLessThanOrEqual((await dock(page).boundingBox()).y);
    // and so does the foot of the e-graph column
    const col = await page.locator('.lesson-grid > .egraph').boundingBox();
    expect(col.y + col.height).toBeLessThanOrEqual((await dock(page).boundingBox()).y + 1);
  });

  test('the REPL\'s page opens with the dock open', async ({ page }) => {
    await openLesson(page, 'repl');
    await expect(toggle(page)).toHaveAttribute('aria-expanded', 'true');
  });
});
