// The introduction: where the page opens, what an e-graph is over one
// small run, the picture drawn unasked, and the prose linking every
// lesson. Counts and costs, never ids.
import { test, expect } from '@playwright/test';
import { openLesson, expectSnapshot, tryAnother } from './orrery.js';

const detail = (page) => page.locator('#replay .class-detail');
const act = (page, kind, text) => page.locator(`.prose a.act[data-act=${kind}]`, { hasText: text });

test.describe('What is an e-graph?', () => {
  test('the page opens on it, with no hash and with #0: (a·2)/2 saturates to a', async ({ page }) => {
    await page.goto('/');
    await expectSnapshot(page, { lesson: 'intro', status: 'done', iterations: 4, stopReason: 'saturated',
                                 step: 4, classes: 4, nodes: 8, bestCost: 1 });
    await expect(page.locator('.lesson h2')).toHaveText('What is an e-graph?');
    await expect(page.locator('#best-notation')).toHaveText('a');
    await expect(page.locator('#input-term')).toHaveValue('(a·2)/2');
    await expect(page.locator('#surprise')).toHaveCount(0);
    await openLesson(page, 0);
    await expectSnapshot(page, { classes: 4, nodes: 8 });
  });

  test('the prose walks the run: the dead end, back again, the steps and the classes', async ({ page }) => {
    await openLesson(page, 0);
    await act(page, 'alternative', 'start from there').click();
    await expectSnapshot(page, { status: 'done', iterations: 1, stopReason: 'saturated', classes: 5, nodes: 5, bestCost: 5 });
    await expect(page.locator('#best-notation')).toHaveText('(a << 1)/2');
    // the class links name terms this e-graph does not hold: plain text
    await expect(act(page, 'select', 'class of a·2')).toHaveCount(0);

    await act(page, 'alternative', 'Give it (a·2)/2').click();
    await expectSnapshot(page, { status: 'done', iterations: 4, classes: 4, nodes: 8, bestCost: 1 });
    await act(page, 'step', 'At the input').click();
    await expectSnapshot(page, { step: 0, classes: 4, nodes: 4 });
    await act(page, 'select', 'class of a·2').click();
    await expectSnapshot(page, { step: 1, classes: 6, nodes: 8 });
    await expect(detail(page).locator('.nodes .dnode')).toHaveCount(2);
    await expect(detail(page).locator('.terms')).toContainText('2 terms');
    await act(page, 'step', 'The twos cancel').click();
    await expectSnapshot(page, { step: 2, classes: 5, nodes: 8 });
    await act(page, 'step', 'a times one is a').click();
    await expectSnapshot(page, { step: 3, classes: 4, nodes: 8 });
    await act(page, 'select', "The input's class").click();
    await expectSnapshot(page, { step: 4 });
    await expect(detail(page).locator('.detail-title')).toContainText("the input's class");
    await expect(detail(page).locator('.nodes .dnode')).toHaveCount(3);
    await expect(detail(page).locator('.terms')).toContainText('infinitely many terms');
  });

  test('try another: other numbers, and twice over', async ({ page }) => {
    await openLesson(page, 0);
    await tryAnother(page, 'other numbers: (x·3)/3');
    await expectSnapshot(page, { status: 'done', iterations: 4, stopReason: 'saturated', classes: 4, nodes: 7, bestCost: 1 });
    await expect(page.locator('#best-notation')).toHaveText('x');
    await tryAnother(page, 'twice over: ((a·2)/2·2)/2');
    await expectSnapshot(page, { status: 'done', iterations: 4, stopReason: 'saturated', classes: 4, nodes: 8, bestCost: 1 });
    await expect(page.locator('#best-notation')).toHaveText('a');
  });

  test('the picture is drawn unasked here and nowhere else, until the switch is touched', async ({ page }) => {
    await openLesson(page, 0);
    await expect(page.locator('#graph svg')).toBeVisible();
    await expect(page.locator('#graph-toggle')).toHaveText('hide the graph');
    await expect(page.locator('#graph .eclass')).toHaveCount(4);
    await expect(page.locator('#graph .enode')).toHaveCount(8);
    await expect(page.locator('#graph .edge.back').first()).toBeVisible();
    await act(page, 'step', 'At the input').click();
    await expect(page.locator('#graph .eclass')).toHaveCount(4);
    await expect(page.locator('#graph .enode')).toHaveCount(4);
    await expect(page.locator('#graph .edge.back')).toHaveCount(0);

    // a lesson leaves it off
    await page.locator('.lesson-nav a', { hasText: '2. Sharing' }).click();
    await expectSnapshot(page, { lesson: 'sharing', status: 'done' });
    await expect(page.locator('#graph')).toHaveCount(0);
    await expect(page.locator('#graph-toggle')).toHaveText('draw the graph');

    // hidden here by the learner, it stays hidden here
    await page.locator('.lesson-nav a', { hasText: 'What is an e-graph?' }).click();
    await expectSnapshot(page, { lesson: 'intro', status: 'done' });
    await expect(page.locator('#graph svg')).toBeVisible();
    await page.locator('#graph-toggle').click();
    await expect(page.locator('#graph')).toHaveCount(0);
    await page.locator('.lesson-nav a', { hasText: '2. Sharing' }).click();
    await expectSnapshot(page, { lesson: 'sharing', status: 'done' });
    await page.locator('.lesson-nav a', { hasText: 'What is an e-graph?' }).click();
    await expectSnapshot(page, { lesson: 'intro', status: 'done' });
    await expect(page.locator('#graph')).toHaveCount(0);
  });

  test('the prose links every lesson, and a link goes there', async ({ page }) => {
    await openLesson(page, 0);
    const hrefs = await page.locator('.prose a[href^="#"]').evaluateAll(as => as.map(a => a.getAttribute('href')));
    expect(hrefs).toEqual(['#1', '#2', '#3', '#4', '#5', '#6', '#7', '#8', '#9', '#10', '#11']);
    await page.locator('.prose a[href="#8"]').click();
    await expect(page).toHaveURL(/#8$/);
    await expectSnapshot(page, { lesson: 'fix', status: 'done' });
    await expect(page.locator('.lesson h2')).toHaveText('8. The fix');
    await page.goBack();
    await expectSnapshot(page, { lesson: 'intro', status: 'done' });
  });

  test('its works are cited and listed', async ({ page }) => {
    await openLesson(page, 0);
    await expect(page.locator('.prose sup.cite a', { hasText: 'Panchekha et al. 2015' }))
      .toHaveAttribute('href', 'https://doi.org/10.1145/2737924.2737959');
    await expect(page.locator('.reading .work')).toHaveCount(6);
  });
});
