// The REPL's two editors, prism-code-editor over a textarea: the line,
// which is the REPL's input, and the buffer, which nothing empties.
// The colours, the brackets, the keys of each, the copy from the
// history into the buffer, the undo and the line numbers.
import { test, expect } from '@playwright/test';
import { openLesson, evalRepl, openRepl, expectSnapshot } from './orrery.js';

const line = (page) => page.locator('#repl-input');
const buffer = (page) => page.locator('#repl-buffer');
const code = (page) => page.locator('#repl-dock .editor.buffer .prism-code-editor');
const dock = (page) => page.locator('#repl-dock');
const entries = (page) => page.locator('#repl-dock .entry');

test.describe('the REPL\'s editors', () => {
  test('colour the code and its brackets by depth', async ({ page }) => {
    await openLesson(page, 'repl');
    await buffer(page).fill('(eg/class-count (first [g])) ; a comment');
    await expect(code(page).locator('.token.comment')).toHaveText('; a comment');
    await expect(code(page).locator('.token.bracket-level-0')).toHaveCount(2);
    await expect(code(page).locator('.token.bracket-level-1')).toHaveCount(2);
    await expect(code(page).locator('.token.bracket-level-2')).toHaveCount(2);
  });

  test('close brackets and double quotes, but not a single quote', async ({ page }) => {
    await openLesson(page, 'repl');
    await line(page).click();
    await line(page).pressSequentially('(+ 1 [2');
    await expect(line(page)).toHaveValue('(+ 1 [2])');
    await line(page).fill('');
    await line(page).pressSequentially('(str "a');
    await expect(line(page)).toHaveValue('(str "a")');
    await line(page).fill('');
    await line(page).pressSequentially("'");
    await expect(line(page)).toHaveValue("'");
  });

  test('the line: Enter evaluates it when its brackets are closed, and empties it', async ({ page }) => {
    await openLesson(page, 3);
    await expect(dock(page)).toHaveClass(/\bclosed\b/);
    await line(page).click();
    await line(page).pressSequentially('(eg/class-count g');
    await line(page).press('Enter');
    await expect(page.locator('#repl-last')).toHaveText(/^user=> \(eg\/class-count g\)\s+⇒\s+5$/);
    await expect(line(page)).toHaveValue('');
    await expect(dock(page)).toHaveClass(/\bclosed\b/);
    // two forms on the line are two entries
    await line(page).fill('(def n 2) (* n 3)');
    await line(page).press('Enter');
    await expect(page.locator('#repl-last')).toHaveText(/⇒\s+6$/);
    await openRepl(page);
    await expect(entries(page)).toHaveCount(3);
  });

  test('the line: Enter in an open bracket starts a new line, indented, and Ctrl-Enter evaluates what is there', async ({ page }) => {
    await openLesson(page, 3);
    await line(page).fill('(let [x 1]');
    await line(page).press('Enter');
    await expect(line(page)).toHaveValue('(let [x 1]\n  ');
    await expect(dock(page)).toHaveClass(/\bclosed\b/);
    await line(page).pressSequentially('(* x 2)');
    await line(page).press('Control+Enter');
    await expect(page.locator('#repl-last')).toHaveText(/⇒.*EOF while reading/);
    await expect(line(page)).toHaveValue('');
  });

  test('the buffer: Enter starts a new line indented as Clojure is, and Tab indents with spaces', async ({ page }) => {
    await openLesson(page, 'repl');
    await buffer(page).click();
    await buffer(page).pressSequentially('(let [x 1');
    await buffer(page).press('Enter');
    await expect(buffer(page)).toHaveValue('(let [x 1\n      ])');
    await buffer(page).fill('(let [x 1]');
    await buffer(page).press('Enter');
    await expect(buffer(page)).toHaveValue('(let [x 1]\n  ');
    await buffer(page).fill('(eg/add g');
    await buffer(page).press('Enter');
    await expect(buffer(page)).toHaveValue('(eg/add g\n        ');
    await buffer(page).fill('(+ 1 2)');
    await buffer(page).press('Enter');
    await expect(buffer(page)).toHaveValue('(+ 1 2)\n');
    await expect(entries(page)).toHaveCount(0);
    await buffer(page).fill('(let [x 1]\nx)');
    await buffer(page).press('Tab');
    await expect(buffer(page)).toHaveValue('(let [x 1]\n  x)');
    await buffer(page).press('Shift+Tab');
    await expect(buffer(page)).toHaveValue('(let [x 1]\nx)');
    await expect(buffer(page)).toBeFocused();
  });

  test('the buffer: Ctrl-Enter evaluates the form at the caret, and the buffer keeps its text', async ({ page }) => {
    await openLesson(page, 'repl');
    const text = '(def a 20)\n\n(+ a\n   1)\n(* a 2)';
    await buffer(page).fill(text);
    await buffer(page).evaluate(t => { t.setSelectionRange(0, 0); });
    await buffer(page).press('Control+Enter');
    await expect(entries(page).last().locator('pre.in')).toHaveText('user=> (def a 20)');
    await buffer(page).evaluate(t => { t.setSelectionRange(17, 17); });   // inside (+ a 1)
    await buffer(page).press('Control+Enter');
    await expect(entries(page).last().locator('pre.in')).toHaveText('user=> (+ a\n   1)');
    await expect(entries(page).last().locator('pre.result')).toHaveText('21');
    await expect(entries(page)).toHaveCount(2);
    await expect(buffer(page)).toHaveValue(text);
  });

  test('the buffer: Ctrl-Shift-Enter evaluates every form in order, and stops at the first error', async ({ page }) => {
    await openLesson(page, 'repl');
    await buffer(page).fill('(def b 1)\n(nope)\n(def b 2)');
    await buffer(page).press('Control+Shift+Enter');
    await expect(entries(page)).toHaveCount(2);
    await expect(entries(page).last().locator('pre.error')).toContainText('nope');
    await expect(await evalRepl(page, 'b')).toContainText('1');
    await page.locator('#repl-clear').click();
    await expect(entries(page)).toHaveCount(0);
    await expect(buffer(page)).toHaveValue('(def b 1)\n(nope)\n(def b 2)');
  });

  test('"clear buffer" empties the buffer and leaves the line and the history alone', async ({ page }) => {
    await openLesson(page, 'repl');
    await evalRepl(page, '(+ 1 2)');
    await buffer(page).fill('(def c 3)');
    await line(page).fill('(+ 2 2)');
    await page.locator('#repl-clear-buffer').click();
    await expect(buffer(page)).toHaveValue('');
    await expect(buffer(page)).toBeFocused();
    await expect(entries(page)).toHaveCount(1);
    await expect(line(page)).toHaveValue('(+ 2 2)');
  });

  test('"to buffer" adds the code of an entry to the end of the buffer, which outlives the dock and the page', async ({ page }) => {
    await openLesson(page, 3);
    await evalRepl(page, '(eg/class-count g)');
    await evalRepl(page, '(let [n (eg/node-count g)]\n  (* 2 n))');
    await entries(page).first().getByRole('button', { name: 'to buffer' }).click();
    await expect(buffer(page)).toHaveValue('(eg/class-count g)');
    await expect(buffer(page)).toBeFocused();
    await entries(page).last().getByRole('button', { name: 'to buffer' }).click();
    await expect(buffer(page)).toHaveValue('(eg/class-count g)\n\n(let [n (eg/node-count g)]\n  (* 2 n))');
    // closing the dock, evaluating on the line and changing page leave the buffer alone
    await page.locator('#repl-toggle').click();
    await expect(buffer(page)).toHaveCount(0);
    await line(page).fill('(+ 1 1)');
    await line(page).press('Enter');
    await page.locator('.lesson-nav a', { hasText: '4. A rule' }).click();
    await expectSnapshot(page, { lesson: 'rule', status: 'done' });
    await openRepl(page);
    await expect(buffer(page)).toHaveValue('(eg/class-count g)\n\n(let [n (eg/node-count g)]\n  (* 2 n))');
  });

  test('Escape closes the dock and leaves the caret on the line; opening puts it in the buffer', async ({ page }) => {
    await openLesson(page, 'repl');
    await buffer(page).click();
    await buffer(page).press('Escape');
    await expect(dock(page)).toHaveClass(/\bclosed\b/);
    await expect(line(page)).toBeFocused();
    await page.keyboard.press('Control+Backquote');
    await expect(dock(page)).toHaveClass(/\bopen\b/);
    await expect(buffer(page)).toBeFocused();
  });

  test('undo what was typed', async ({ page }) => {
    await openLesson(page, 'repl');
    await buffer(page).click();
    await buffer(page).pressSequentially('abc');
    await expect(buffer(page)).toHaveValue('abc');
    await buffer(page).press('ControlOrMeta+z');
    await expect(buffer(page)).not.toHaveValue('abc');
  });

  test('the line: a recalled input puts the caret at its end, and the buffer\'s arrows only move the caret', async ({ page }) => {
    await openLesson(page, 'repl');
    await evalRepl(page, '(+ 1 1)');
    await line(page).press('ArrowUp');
    await expect(line(page)).toHaveValue('(+ 1 1)');
    await line(page).pressSequentially(' ;x');
    await expect(line(page)).toHaveValue('(+ 1 1) ;x');
    await buffer(page).fill('(+ 2 2)');
    await buffer(page).press('ArrowUp');
    await expect(buffer(page)).toHaveValue('(+ 2 2)');
  });

  test('a line of the prose leaves the line and the buffer alone', async ({ page }) => {
    await openLesson(page, 'repl');
    await line(page).fill('(+ 1 2)');
    await buffer(page).fill('(+ 3 4)');
    await page.locator('.prose a.act[data-act=eval]').first().click();
    await expect(entries(page)).toHaveCount(1);
    await expect(line(page)).toHaveValue('(+ 1 2)');
    await expect(buffer(page)).toHaveValue('(+ 3 4)');
  });

  test('the buffer numbers its lines when asked, on every page after', async ({ page }) => {
    await openLesson(page, 'repl');
    const box = page.locator('#repl-line-numbers');
    await expect(box).not.toBeChecked();
    await expect(code(page)).not.toHaveClass(/show-line-numbers/);
    await buffer(page).fill('(+ 1\n 2)');
    await box.check();
    await expect(code(page)).toHaveClass(/show-line-numbers/);
    await expect(buffer(page)).toHaveValue('(+ 1\n 2)');
    await page.goto('/#1');
    await openRepl(page);
    await expect(page.locator('#repl-line-numbers')).toBeChecked();
    await expect(code(page)).toHaveClass(/show-line-numbers/);
    await page.locator('#repl-line-numbers').uncheck();
    await expect(code(page)).not.toHaveClass(/show-line-numbers/);
  });
});
