// The REPL's editor, prism-code-editor over a textarea: the colours,
// the brackets, the keys of the closed and the open dock, the undo
// and the line numbers.
import { test, expect } from '@playwright/test';
import { openLesson, evalRepl, openRepl } from './orrery.js';

const editor = (page) => page.locator('#repl-input');
const code = (page) => page.locator('#repl-dock .prism-code-editor');
const dock = (page) => page.locator('#repl-dock');
const entries = (page) => page.locator('#repl-dock .entry');

test.describe('the REPL\'s editor', () => {
  test('colours the code and its brackets by depth', async ({ page }) => {
    await openLesson(page, 'repl');
    await editor(page).fill('(eg/class-count (first [g])) ; a comment');
    await expect(code(page).locator('.token.comment')).toHaveText('; a comment');
    await expect(code(page).locator('.token.bracket-level-0')).toHaveCount(2);
    await expect(code(page).locator('.token.bracket-level-1')).toHaveCount(2);
    await expect(code(page).locator('.token.bracket-level-2')).toHaveCount(2);
  });

  test('closes brackets and double quotes, but not a single quote', async ({ page }) => {
    await openLesson(page, 'repl');
    await editor(page).click();
    await editor(page).pressSequentially('(+ 1 [2');
    await expect(editor(page)).toHaveValue('(+ 1 [2])');
    await editor(page).fill('');
    await editor(page).pressSequentially('(str "a');
    await expect(editor(page)).toHaveValue('(str "a")');
    await editor(page).fill('');
    await editor(page).pressSequentially("'");
    await expect(editor(page)).toHaveValue("'");
  });

  test('open, Enter starts a new line indented as Clojure is, and Tab indents with spaces', async ({ page }) => {
    await openLesson(page, 'repl');
    await editor(page).click();
    await editor(page).pressSequentially('(let [x 1');
    await editor(page).press('Enter');
    await expect(editor(page)).toHaveValue('(let [x 1\n      ])');
    await editor(page).fill('(let [x 1]');
    await editor(page).press('Enter');
    await expect(editor(page)).toHaveValue('(let [x 1]\n  ');
    await editor(page).fill('(eg/add g');
    await editor(page).press('Enter');
    await expect(editor(page)).toHaveValue('(eg/add g\n        ');
    await editor(page).fill('(let [x 1]\nx)');
    await editor(page).press('Tab');
    await expect(editor(page)).toHaveValue('(let [x 1]\n  x)');
    await editor(page).press('Shift+Tab');
    await expect(editor(page)).toHaveValue('(let [x 1]\nx)');
    await expect(editor(page)).toBeFocused();
  });

  test('closed, Enter evaluates the line and empties the editor', async ({ page }) => {
    await openLesson(page, 3);
    await expect(dock(page)).toHaveClass(/\bclosed\b/);
    await editor(page).click();
    await editor(page).pressSequentially('(eg/class-count g');
    await editor(page).press('Enter');
    await expect(page.locator('#repl-last')).toHaveText(/^user=> \(eg\/class-count g\)\s+⇒\s+5$/);
    await expect(editor(page)).toHaveValue('');
    await expect(dock(page)).toHaveClass(/\bclosed\b/);
    // two forms on the line are two entries
    await editor(page).fill('(def n 2) (* n 3)');
    await editor(page).press('Enter');
    await expect(page.locator('#repl-last')).toHaveText(/⇒\s+6$/);
    await openRepl(page);
    await expect(entries(page)).toHaveCount(3);
  });

  test('closed, Enter in an open bracket and Shift-Enter open the dock on a new line', async ({ page }) => {
    await openLesson(page, 3);
    await editor(page).fill('(let [x 1]');
    await editor(page).press('Enter');
    await expect(dock(page)).toHaveClass(/\bopen\b/);
    await expect(editor(page)).toHaveValue('(let [x 1]\n  ');
    await expect(entries(page)).toHaveCount(0);
    await editor(page).press('Escape');
    await expect(dock(page)).toHaveClass(/\bclosed\b/);
    await expect(editor(page)).toBeFocused();
    await editor(page).fill('(+ 1 2)');
    await editor(page).press('Shift+Enter');
    await expect(dock(page)).toHaveClass(/\bopen\b/);
    await expect(editor(page)).toHaveValue('(+ 1 2)\n');
  });

  test('closed over several lines, the dock says how many, and Enter opens it without evaluating', async ({ page }) => {
    await openLesson(page, 3);
    await editor(page).fill('(+ 1\n   2)\n(* 3 4)');
    await expect(page.locator('#repl-more')).toHaveText('+2 lines');
    await editor(page).press('Enter');
    await expect(dock(page)).toHaveClass(/\bopen\b/);
    await expect(editor(page)).toHaveValue('(+ 1\n   2)\n(* 3 4)');
    await expect(entries(page)).toHaveCount(0);
    await expect(page.locator('#repl-more')).toHaveCount(0);
  });

  test('Ctrl-Enter evaluates the form at the caret, and the editor keeps its text', async ({ page }) => {
    await openLesson(page, 'repl');
    const text = '(def a 20)\n\n(+ a\n   1)\n(* a 2)';
    await editor(page).fill(text);
    await editor(page).evaluate(t => { t.setSelectionRange(0, 0); });
    await editor(page).press('Control+Enter');
    await expect(entries(page).last().locator('pre.in')).toHaveText('user=> (def a 20)');
    await editor(page).evaluate(t => { t.setSelectionRange(17, 17); });   // inside (+ a 1)
    await editor(page).press('Control+Enter');
    await expect(entries(page).last().locator('pre.in')).toHaveText('user=> (+ a\n   1)');
    await expect(entries(page).last().locator('pre.result')).toHaveText('21');
    await expect(entries(page)).toHaveCount(2);
    await expect(editor(page)).toHaveValue(text);
  });

  test('Ctrl-Shift-Enter evaluates every form in order, and stops at the first error', async ({ page }) => {
    await openLesson(page, 'repl');
    await editor(page).fill('(def b 1)\n(nope)\n(def b 2)');
    await editor(page).press('Control+Shift+Enter');
    await expect(entries(page)).toHaveCount(2);
    await expect(entries(page).last().locator('pre.error')).toContainText('nope');
    await editor(page).fill('b');
    await editor(page).press('Control+Enter');
    await expect(entries(page).last().locator('pre.result')).toHaveText('1');
    await page.locator('#repl-clear').click();
    await expect(entries(page)).toHaveCount(0);
    await expect(editor(page)).toHaveValue('b');
  });

  test('undoes what was typed', async ({ page }) => {
    await openLesson(page, 'repl');
    await editor(page).click();
    await editor(page).pressSequentially('abc');
    await expect(editor(page)).toHaveValue('abc');
    await editor(page).press('ControlOrMeta+z');
    await expect(editor(page)).not.toHaveValue('abc');
  });

  test('closed, a recalled input puts the caret at its end', async ({ page }) => {
    await openLesson(page, 3);
    await editor(page).fill('(+ 1 1)');
    await editor(page).press('Enter');
    await expect(editor(page)).toHaveValue('');
    await editor(page).press('ArrowUp');
    await expect(editor(page)).toHaveValue('(+ 1 1)');
    await editor(page).pressSequentially(' ;x');
    await expect(editor(page)).toHaveValue('(+ 1 1) ;x');
  });

  test('a line of the prose leaves the editor\'s text alone', async ({ page }) => {
    await openLesson(page, 'repl');
    await editor(page).fill('(+ 1 2)');
    await page.locator('.prose a.act[data-act=eval]').first().click();
    await expect(entries(page)).toHaveCount(1);
    await expect(editor(page)).toHaveValue('(+ 1 2)');
  });

  test('numbers its lines when asked, on every page after', async ({ page }) => {
    await openLesson(page, 'repl');
    const box = page.locator('#repl-line-numbers');
    await expect(box).not.toBeChecked();
    await expect(code(page)).not.toHaveClass(/show-line-numbers/);
    await editor(page).fill('(+ 1\n 2)');
    await box.check();
    await expect(code(page)).toHaveClass(/show-line-numbers/);
    await expect(editor(page)).toHaveValue('(+ 1\n 2)');
    await page.goto('/#1');
    await openRepl(page);
    await expect(page.locator('#repl-line-numbers')).toBeChecked();
    await expect(code(page)).toHaveClass(/show-line-numbers/);
    await page.locator('#repl-line-numbers').uncheck();
    await expect(code(page)).not.toHaveClass(/show-line-numbers/);
  });
});
