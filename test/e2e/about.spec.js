// What the site is: said at length over the page it opens on, before
// any prose that links into the running widgets, and in a line under
// every page. It says what the site is for, what it embeds, and that
// it is largely written using LLMs.
import { test, expect } from '@playwright/test';
import { openLesson, expectSnapshot } from './orrery.js';

const disclosure = 'largely written using LLMs';

test.describe('what this is', () => {
  test('the page opens under it: the statement, then the heading, then the first link into the widgets', async ({ page }) => {
    await page.goto('/');
    await expectSnapshot(page, { lesson: 'basics', status: 'done' });
    const about = page.locator('#about');
    await expect(about).toBeVisible();
    await expect(about.locator('.label')).toHaveText('what this is');
    await expect(about).toContainText('an interactive tool for exploring e-graphs and learning how they work');
    await expect(about).toContainText("its author's learning included");
    await expect(about).toContainText('cromulent, an e-graph that is an immutable, persistent value');
    await expect(about).toContainText('bendix, a nascent computer algebra system built on it');
    await expect(about).toContainText('widgets over what those libraries really compute');
    await expect(about).toContainText(`It is ${disclosure}.`);
    await expect(about.locator('a')).toHaveCount(0);

    const y = async (locator) => (await locator.boundingBox()).y;
    const [a, h, first] = [await y(about), await y(page.locator('.lesson h2')), await y(page.locator('.prose a.act').first())];
    expect(a).toBeLessThan(h);
    expect(h).toBeLessThan(first);
    await expect(about).toBeInViewport();
  });

  test('only the page the site opens on says it at length; every page says it in a line', async ({ page }) => {
    for (const n of ['basics', 'intro', 1, 7, 11]) {
      await openLesson(page, n);
      await expect(page.locator('#about')).toHaveCount(n === 'basics' ? 1 : 0);
      await expect(page.locator('#colophon')).toContainText(disclosure);
      await expect(page.locator('#colophon')).toContainText('cromulent and bendix');
    }
    await page.locator('#colophon a', { hasText: 'What this is' }).click();
    await expect(page).toHaveURL(/#basics$/);
    await expectSnapshot(page, { lesson: 'basics', status: 'done' });
    await expect(page.locator('#about')).toBeVisible();
  });
});
