import { test, expect } from '@playwright/test';
import { expectSelfTest, expectSnapshot } from './orrery.js';

test('the engine self-test tile is green: 122 facts hold in this browser', async ({ page }) => {
  await page.goto('/');
  await expectSelfTest(page, 122);
});

test('no hash opens the basics; the navigation lists the two pages before the lessons, all eleven lessons and the REPL', async ({ page }) => {
  await page.goto('/');
  await expectSnapshot(page, { lesson: 'basics' });
  await expect(page.locator('.lesson-nav a')).toHaveCount(14);
  await expect(page.locator('.lesson-nav a').nth(13)).toHaveText('The REPL');
  await expect(page.locator('.lesson-nav .coming')).toHaveCount(0);
  await expect(page.locator('.lesson-nav a').nth(0)).toHaveText('Start here');
  await expect(page.locator('.lesson-nav a').nth(1)).toHaveText('What is an e-graph?');
  await expect(page.locator('.lesson-nav a').nth(2)).toHaveText('1. A term is a tree');
  await expect(page.locator('.lesson-nav a.current')).toHaveText('Start here');
  await page.locator('.lesson-nav a', { hasText: '3. Equality' }).click();
  await expect(page).toHaveURL(/#3$/);
  await expectSnapshot(page, { lesson: 'congruence', status: 'done' });
  await expect(page.locator('.lesson-nav a.current')).toHaveText('3. Equality and congruence');
});

test('a hash that names no lesson opens the basics', async ({ page }) => {
  for (const hash of ['#99', '#0', '#nowhere']) {
    await page.goto('about:blank');
    await page.goto(`/${hash}`);
    await expectSnapshot(page, { lesson: 'basics', status: 'done' });
  }
});
