import { test, expect } from '@playwright/test';
import { expectSelfTest, expectSnapshot } from './orrery.js';

test('the engine self-test tile is green: 114 facts hold in this browser', async ({ page }) => {
  await page.goto('/');
  await expectSelfTest(page, 114);
});

test('no hash opens the introduction; the navigation lists it and all eleven lessons', async ({ page }) => {
  await page.goto('/');
  await expectSnapshot(page, { lesson: 'intro' });
  await expect(page.locator('.lesson-nav a')).toHaveCount(12);
  await expect(page.locator('.lesson-nav .coming')).toHaveCount(0);
  await expect(page.locator('.lesson-nav a').first()).toHaveText('What is an e-graph?');
  await expect(page.locator('.lesson-nav a.current')).toHaveText('What is an e-graph?');
  await page.locator('.lesson-nav a', { hasText: '3. Equality' }).click();
  await expect(page).toHaveURL(/#3$/);
  await expectSnapshot(page, { lesson: 'congruence', status: 'done' });
  await expect(page.locator('.lesson-nav a.current')).toHaveText('3. Equality and congruence');
});

test('a hash that names no lesson opens the introduction', async ({ page }) => {
  await page.goto('/#99');
  await expectSnapshot(page, { lesson: 'intro', status: 'done' });
});
