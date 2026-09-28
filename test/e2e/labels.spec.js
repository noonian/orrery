// What each panel is the work of: the operation over the fields that
// are its arguments, the call under the options in force, and the
// function beside each panel's heading, as the REPL under the page
// names it.
import { test, expect } from '@playwright/test';
import { openLesson, expectSnapshot, tryAnother, evalRepl } from './orrery.js';

const operation = (page) => page.locator('#operation');
const args = (page) => page.locator('.input-area .field h3 .arg');
const of = (page, title) => page.locator('.panel-head', { has: page.locator('h3', { hasText: title }) }).locator('.of');

test.describe('what the panels are the work of', () => {
  test('the basics: saturate, over a term and rules', async ({ page }) => {
    await openLesson(page, 'basics');
    await expect(operation(page).locator('h3')).toHaveText('saturate');
    await expect(operation(page).locator('.of')).toHaveText('rw/saturate');
    await expect(operation(page).locator('.says')).toContainText('run every rule over it');
    await expect(page.locator('#call')).toHaveText('(eg/add (eg/egraph) term)\n(rw/saturate g rules\n  {:scheduler :simple})',
                                                   { useInnerText: true });
    await expect(args(page)).toHaveText(['term', 'rules']);
    await expect(page.locator('.input-area .field h3 .says').first()).toHaveText('the expression to start from');
    await expect(of(page, 'the run')).toHaveText('rw/saturate');
    await expect(of(page, 'best so far')).toHaveText('ex/extract');
    await expect(page.locator('#extract-says')).toContainText('(ex/extract g root cost)');
    await expect(of(page, 'the classes')).toHaveText('g at this step');
  });

  test('the words beside rules follow the print mode; a problem names the field in words', async ({ page }) => {
    await openLesson(page, 4);
    const says = page.locator('.input-area .field h3 .says').nth(1);
    await expect(says).toHaveText('what may be written as what, one per line as name: pattern → replacement');
    await page.locator('.print-toggle button', { hasText: 'native' }).click();
    await expect(says).toHaveText('what may be written as what, as [name pattern replacement]');
    await page.locator('#input-rules').fill('[["comm" [:+ ?a ?b] [:+ ?b ?c]]]');
    await page.locator('#run').click();
    await expect(page.locator('.input-area .error')).toContainText('the rules: ');
    await expect(of(page, 'matches in this step')).toHaveText('pat/ematch');
  });

  test('lesson 5: the options in force are in the call, the alternative\'s over the lesson\'s', async ({ page }) => {
    await openLesson(page, 5);
    await expect(page.locator('#call')).toContainText(':scheduler :backoff');
    await expect(page.locator('#call')).toContainText(':match-limit 4');
    await expect(page.locator('#call')).not.toContainText(':node-limit');
    await expect(of(page, 'the iterations')).toHaveText(':stats');
    await tryAnother(page, 'five atoms under a node limit of 100');
    await expectSnapshot(page, { status: 'done', stopReason: 'node-limit' });
    await expect(page.locator('#call')).toContainText(':scheduler :simple');
    await expect(page.locator('#call')).toContainText(':node-limit 100');
  });

  test('each lesson names its own operation', async ({ page }) => {
    for (const [n, name, fn, fields] of [
      [1, 'add', 'eg/add', ['term']],
      [3, 'union, then rebuild', 'eg/union · eg/rebuild', ['lhs', 'rhs', 'wrapper']],
      [8, 'simplify', 'bx/simplify', ['term', 'rules']],
      [9, 'simplify', 'bx/simplify', ['term']],
      [10, 'simplify', 'bx/simplify', ['term']],
      [11, 'union, in a copy', 'eg/union · eg/rebuild', ['term', 'lhs', 'rhs']],
      [12, 'differentiate', 'bx/differentiate', ['term', 'x']],
    ]) {
      await openLesson(page, n);
      await expect(operation(page).locator('h3')).toHaveText(name);
      await expect(operation(page).locator('.of')).toHaveText(fn);
      await expect(args(page)).toHaveText(fields);
      await expect(of(page, 'the run')).toHaveText(fn);
    }
    await expect(page.locator('#call')).toContainText('(bx/differentiate term x');
    // lesson 12 has costs to choose from: the picker is the cost argument
    await expect(page.locator('.cost-picker .arg')).toHaveText('cost');
  });

  test('lesson 1: the tree is the term; lesson 11: the original is the first of the timeline', async ({ page }) => {
    await openLesson(page, 1);
    await expect(of(page, 'the tree')).toHaveText('term');
    await openLesson(page, 11);
    await expect(page.locator('.fork .panel-head .of')).toHaveText(['(first timeline)', 'g at this step']);
  });

  test('a run adopted from the REPL says where it came from', async ({ page }) => {
    await openLesson(page, 1);
    const entry = await evalRepl(page, '(first (eg/add g [:* :x :x]))');
    await entry.getByRole('button', { name: 'show it' }).click();
    await expect(of(page, 'the run')).toHaveText('from the REPL');
  });
});
