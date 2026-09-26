import { test, expect } from '@playwright/test';
import { openLesson, expectSnapshot, tryAnother, submit } from './orrery.js';

test.describe('1. A term is a tree', () => {
  test('the curated term: five tree nodes, five classes', async ({ page }) => {
    await openLesson(page, 1);
    await expectSnapshot(page, { steps: 1, classes: 5, nodes: 5 });
    await expect(page.locator('.tnode')).toHaveCount(5);
    await expect(page.locator('.tnode > .tlabel > .top')).toHaveText(['+', '*', '2', 'x', 'y']);
    await expect(page.locator('#tree-counts')).toContainText('tree nodes 5');
    await expect(page.locator('#tree-counts')).toContainText('graph nodes 5');
  });

  test('try another: (x + 1)·(y − 2)', async ({ page }) => {
    await openLesson(page, 1);
    await tryAnother(page, '(x + 1)·(y − 2)');
    await expectSnapshot(page, { status: 'done', classes: 7, nodes: 7 });
    await expect(page.locator('.tnode')).toHaveCount(7);
    await expect(page.locator('.alternatives button.current')).toHaveText('(x + 1)·(y − 2)');
  });

  test('an edited term runs, and the alternative is no longer current', async ({ page }) => {
    await openLesson(page, 1);
    await tryAnother(page, 'sin(2·x)');
    await expectSnapshot(page, { status: 'done', classes: 4 });
    await submit(page, { term: '[:+ [:* :a :b] :c]' });
    await expectSnapshot(page, { status: 'done', classes: 5, nodes: 5 });
    await expect(page.locator('.tnode > .tlabel > .top')).toHaveText(['+', '*', 'a', 'b', 'c']);
    await expect(page.locator('.alternatives button.current')).toHaveCount(0);
  });

  test('a term that does not read shows the problem and keeps the run', async ({ page }) => {
    await openLesson(page, 1);
    await submit(page, { term: '[:+ :a' });
    await expect(page.locator('.input-area .error')).toContainText('the term: could not read that');
    await submit(page, { term: '["+" :a :b]' });
    await expect(page.locator('.input-area .error')).toContainText('an operator is a keyword, not "+"');
    await expectSnapshot(page, { classes: 5, nodes: 5 });
    await expect(page.locator('.tnode')).toHaveCount(5);
  });
});
