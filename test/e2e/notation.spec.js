import { test, expect } from '@playwright/test';
import { openLesson, expectSnapshot, submit } from './orrery.js';

// Phase 3: the inputs read the notation, and show it in notation mode.
test.describe('notation input', () => {
  test('the term field shows the notation and reads it back', async ({ page }) => {
    await openLesson(page, 1);
    await expect(page.locator('#input-term')).toHaveValue('2·x + y');
    await expect(page.locator('#input-help')).toContainText('notation as the page prints it');
    await submit(page, { term: '(x + 1)·(y − 2)' });
    await expectSnapshot(page, { status: 'done', classes: 7, nodes: 7 });
    await expect(page.locator('#best-term')).toHaveText('[:* [:+ :x 1] [:- :y 2]]');
    await expect(page.locator('.alternatives button.current')).toHaveCount(0);
  });

  test('typed spellings; a ratio is one leaf, a parenthesized quotient is a node', async ({ page }) => {
    await openLesson(page, 1);
    await submit(page, { term: '1/2 * x' });
    await expectSnapshot(page, { status: 'done', classes: 3, nodes: 3 });
    await expect(page.locator('.tnode > .tlabel > .top')).toHaveText(['*', '1/2', 'x']);
    await submit(page, { term: '1/(2)·x' });
    await expectSnapshot(page, { status: 'done', classes: 5, nodes: 5 });
    await expect(page.locator('.tnode > .tlabel > .top')).toHaveText(['*', '/', '1', '2', 'x']);
    await submit(page, { term: 'sin^2 x + cos^2 x' });
    await expectSnapshot(page, { status: 'done', classes: 7, nodes: 7 });
    await expect(page.locator('#best-notation')).toHaveText('sin²x + cos²x');
  });

  test('rules one per line, and their problems by line', async ({ page }) => {
    await openLesson(page, 4);
    await expect(page.locator('#input-rules')).toHaveValue(/→/);
    await submit(page, { term: 'a + b', rules: 'comm: ?a + ?b -> ?b + ?a' });
    await expectSnapshot(page, { status: 'done', iterations: 1, classes: 3, nodes: 4 });
    await page.locator('#step-first').click();
    await expect(page.locator('#matches .count')).toContainText('1 match');
    await submit(page, { rules: 'comm ?a + ?b -> ?b + ?a' });
    await expect(page.locator('.input-area .error')).toContainText('line 1: a rule is written name: pattern -> replacement');
    await submit(page, { rules: 'comm: ?a + ?b -> ?b + ?c' });
    await expect(page.locator('.input-area .error')).toContainText('?c');
    await expectSnapshot(page, { step: 0, classes: 3, nodes: 3 });   // the run is kept, still scrubbed to its start
  });

  test('a pattern with ?x, and a derivative with respect to a typed variable', async ({ page }) => {
    await openLesson(page, 3);
    await expect(page.locator('#input-wrapper')).toHaveValue('?x/2');
    await submit(page, { wrapper: '(?x + 1)·(?x + 1)' });
    await expectSnapshot(page, { status: 'done', classes: 6, nodes: 7 });

    await openLesson(page, 11);
    await expect(page.locator('#input-var')).toHaveValue('x');
    await submit(page, { term: 'x·sin x', var: 'x' });
    await expectSnapshot(page, { status: 'done', stopReason: 'saturated' });
    await expect(page.locator('#best-term')).toHaveText('[:+ [:* :x [:cos :x]] [:sin :x]]');
  });

  test('problems are said in the notation, and keep the run', async ({ page }) => {
    await openLesson(page, 1);
    await submit(page, { term: '2x' });
    await expect(page.locator('.input-area .error')).toContainText('the term: missing an operator before x at character 2; a product is written 2·x or 2*x');
    await submit(page, { term: 'x +' });
    await expect(page.locator('.input-area .error')).toContainText('nothing after +');
    await submit(page, { term: '1/0' });
    await expect(page.locator('.input-area .error')).toContainText('1/0: division by zero');
    await submit(page, { term: '0.5·x' });
    await expect(page.locator('.input-area .error')).toContainText('0.5 is not exact; write a ratio such as 1/2');
    await submit(page, { term: '(x' });
    await expect(page.locator('.input-area .error')).toContainText('missing ) for the ( at character 1');
    await expectSnapshot(page, { classes: 5, nodes: 5 });
  });

  test('the mode toggle refills an unedited field and keeps an edited one; either mode reads either spelling', async ({ page }) => {
    await openLesson(page, 1);
    const toggle = (name) => page.locator('.print-toggle button', { hasText: name }).first().click();
    await toggle('native');
    await expect(page.locator('#input-term')).toHaveValue('[:+ [:* 2 :x] :y]');
    await expect(page.locator('#input-help')).toContainText('native format');
    await toggle('notation');
    await expect(page.locator('#input-term')).toHaveValue('2·x + y');
    await page.locator('#input-term').fill('x + 1');
    await toggle('native');
    await expect(page.locator('#input-term')).toHaveValue('x + 1');
    await submit(page, {});
    await expectSnapshot(page, { status: 'done', classes: 3, nodes: 3 });
    await toggle('notation');
    await submit(page, { term: '[:+ :a :b]' });
    await expectSnapshot(page, { status: 'done', classes: 3, nodes: 3 });
    await expect(page.locator('#best-notation')).toHaveText('a + b');
  });
});
