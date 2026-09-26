import { test, expect } from '@playwright/test';
import { expectSelfTest, expectSnapshot } from './orrery.js';

test('the engine self-test tile is green: 96 facts hold in this browser', async ({ page }) => {
  await page.goto('/');
  await expectSelfTest(page, 96);
});

test('no hash opens the blowup; the navigation lists all eleven lessons', async ({ page }) => {
  await page.goto('/');
  await expectSnapshot(page, { lesson: 'blowup' });
  await expect(page.locator('.lesson-nav a')).toHaveCount(11);
  await expect(page.locator('.lesson-nav .coming')).toHaveCount(0);
  await expect(page.locator('.lesson-nav a.current')).toHaveText('7. The blowup');
  await page.locator('.lesson-nav a', { hasText: '3. Equality' }).click();
  await expect(page).toHaveURL(/#3$/);
  await expectSnapshot(page, { lesson: 'congruence', status: 'done' });
  await expect(page.locator('.lesson-nav a.current')).toHaveText('3. Equality and congruence');
});
