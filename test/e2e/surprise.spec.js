// "surprise me": a bank of random terms drawn in the browser over the
// rules in force, run, scored, one picked and run on the page, with a
// line saying what was picked and why. The pick is random, so the
// assertions are about the mechanism: the run finishes, the inputs
// changed, the line names the bank and the lesson's wants.
import { test, expect } from '@playwright/test';
import { openLesson, expectSnapshot, snapshot } from './orrery.js';

const why = /^drawn from \d+ candidates, \d+ shapes of result; score \d\.\d\d: /;

async function surprise(page) {
  await page.locator('#surprise').click();
  await expect(page.locator('#drawn')).toBeVisible();
  await expectSnapshot(page, { status: 'done' });
  return page.locator('#drawn').textContent();
}

test.describe('surprise me', () => {
  test('lesson 5: a term drawn under the rules in force, run to the end', async ({ page }) => {
    await openLesson(page, 5);
    const before = await page.locator('#input-term').inputValue();
    const line = await surprise(page);
    expect(line).toMatch(why);
    expect(line).toContain('iterations');
    expect(line).toContain('stop reason');
    expect(await page.locator('#input-term').inputValue()).not.toBe(before);
    expect(await page.locator('#input-rules').inputValue()).toContain('comm');
    const s = await snapshot(page);
    expect(s.iterations).toBeGreaterThanOrEqual(1);
  });

  test('lesson 3: all three inputs drawn, the rebuild step reached', async ({ page }) => {
    await openLesson(page, 3);
    const line = await surprise(page);
    expect(line).toMatch(why);
    expect(line).toContain('classes merged by congruence');
    expect(await page.locator('#input-wrapper').inputValue()).toContain('?x');
    await expectSnapshot(page, { steps: 3, step: 2, dirty: false });
  });

  test('lesson 10: a planted pythagorean pair on bendix, the polynomial rule counted', async ({ page }) => {
    await openLesson(page, 10);
    const line = await surprise(page);
    expect(line).toMatch(why);
    expect(line).toContain('normal-form rule applications');
    expect(await page.locator('#input-term').inputValue()).toMatch(/sin.*cos|cos.*sin/);
    await expectSnapshot(page, { stopReason: 'saturated' });
  });

  test('editing and running clears the line; try another too', async ({ page }) => {
    await openLesson(page, 6);
    await surprise(page);
    await page.locator('#input-term').fill('[:+ :a :a]');
    await page.locator('#run').click();
    await expectSnapshot(page, { status: 'done', classes: 4, nodes: 6 });
    await expect(page.locator('#drawn')).toHaveCount(0);
    await surprise(page);
    await page.locator('.alternatives').getByRole('button', { name: 'with commutativity', exact: true }).click();
    await expectSnapshot(page, { status: 'done' });
    await expect(page.locator('#drawn')).toHaveCount(0);
  });
});
