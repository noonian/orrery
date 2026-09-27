// The REPL's editor, prism-code-editor over a textarea: the colours,
// the brackets, the keys, the undo and the line numbers.
import { test, expect } from '@playwright/test';
import { openLesson, evalRepl, openRepl } from './orrery.js';

const editor = (page) => page.locator('#repl-input');
const code = (page) => page.locator('#repl-panel .prism-code-editor');

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

  // Enter indents only between a pair of brackets for now; the
  // indentation of Clojure is for later.
  test('Enter between brackets indents, and Tab indents with spaces', async ({ page }) => {
    await openLesson(page, 'repl');
    await editor(page).click();
    await editor(page).pressSequentially('(let [');
    await editor(page).press('Enter');
    await expect(editor(page)).toHaveValue('(let [\n  \n])');
    await editor(page).fill('(let [x 1]\nx)');
    await editor(page).press('Tab');
    await expect(editor(page)).toHaveValue('(let [x 1]\n  x)');
    await editor(page).press('Shift+Tab');
    await expect(editor(page)).toHaveValue('(let [x 1]\nx)');
    await expect(editor(page)).toBeFocused();
  });

  test('undoes what was typed', async ({ page }) => {
    await openLesson(page, 'repl');
    await editor(page).click();
    await editor(page).pressSequentially('abc');
    await expect(editor(page)).toHaveValue('abc');
    await editor(page).press('ControlOrMeta+z');
    await expect(editor(page)).not.toHaveValue('abc');
  });

  test('a recalled input puts the caret at its end', async ({ page }) => {
    await openLesson(page, 'repl');
    await evalRepl(page, '(+ 1 1)');
    await editor(page).press('ArrowUp');
    await expect(editor(page)).toHaveValue('(+ 1 1)');
    await editor(page).pressSequentially(' ;x');
    await expect(editor(page)).toHaveValue('(+ 1 1) ;x');
  });

  test('a line of the prose leaves the editor\'s text alone', async ({ page }) => {
    await openLesson(page, 'repl');
    await editor(page).fill('(+ 1 2)');
    await page.locator('.prose a.act[data-act=eval]').first().click();
    await expect(page.locator('#repl-panel .entry')).toHaveCount(1);
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
