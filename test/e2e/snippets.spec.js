// Snippets for the REPL's buffer: the code behind the run's counters,
// the opened class and the export, and the library in the dock. Every
// snippet evaluates without an error.
import { test, expect } from '@playwright/test';
import { openLesson, openRepl } from './orrery.js';

const evalBuffer = async (page) => {
  await page.locator('#repl-buffer').press('Control+Shift+Enter');
};

test.describe('snippets', () => {
  test('the panels put their code in the buffer, and it evaluates', async ({ page }) => {
    await openLesson(page, 6);
    await page.locator('tbody tr.root .class-id').click();
    await page.locator('.panel-head').filter({ hasText: 'the run' }).getByRole('button', { name: 'to buffer' }).click();
    await expect(page.locator('#repl-dock')).toHaveClass(/\bopen\b/);
    await page.locator('#class-code').click();
    await page.locator('#export-code').click();
    const buffer = await page.locator('#repl-buffer').inputValue();
    expect(buffer).toContain('(eg/class-count g)');
    expect(buffer).toContain('(eclass/term-count g c)');
    expect(buffer).toContain('(export/json g');
    await evalBuffer(page);
    await expect(page.locator('#repl-dock .entry')).toHaveCount(3);
    await expect(page.locator('#repl-dock .entry pre.error')).toHaveCount(0);
    await expect(page.locator('#repl-dock .entry').first().locator('pre.result')).toContainText(':classes');
    await expect(page.locator('#repl-dock .entry').nth(1).locator('pre.result')).toContainText(':terms 3');
  });

  test('every snippet of the library evaluates on a lesson', async ({ page }) => {
    await openLesson(page, 'basics');
    await page.locator('tbody tr.root .class-id').click();
    await openRepl(page);
    await page.locator('#repl-snippets-toggle').click();
    const buttons = page.locator('#repl-snippets li .to-buffer');
    const n = await buttons.count();
    expect(n).toBeGreaterThan(5);
    for (let i = 0; i < n; i++) await buttons.nth(i).click();
    await page.locator('#repl-snippets-close').click();
    await expect(page.locator('#repl-snippets')).toHaveCount(0);
    await evalBuffer(page);
    await expect(page.locator('#repl-dock .entry').first()).toBeVisible();
    expect(await page.locator('#repl-dock .entry pre.error').allTextContents()).toEqual([]);
    await expect(page.locator('#repl-dock .entry')).toHaveCount(n);
  });

  test('the snippets and the keys take turns', async ({ page }) => {
    await openLesson(page, 'basics');
    await page.locator('#repl-snippets-toggle').click();
    await expect(page.locator('#repl-snippets')).toBeVisible();
    await page.locator('#repl-help-toggle').click();
    await expect(page.locator('#repl-help')).toBeVisible();
    await expect(page.locator('#repl-snippets')).toHaveCount(0);
  });
});
