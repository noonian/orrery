// What every spec shares: the page's snapshot hook, opening a lesson,
// "try another", the inputs, the self-test tile, tables and the REPL.
import { expect } from '@playwright/test';

// A lesson's address, what follows # → the key the page reports, so
// opening it also proves the hash landed on the right lesson. The two
// pages before the lessons and the REPL's after them have no number
// and go by their key.
export const keys = {
  basics: 'basics', intro: 'intro', 1: 'tree', 2: 'sharing', 3: 'congruence', 4: 'rule',
  5: 'saturation', 6: 'taste', 7: 'blowup', 8: 'fix',
  9: 'polynomial-rule', 10: 'what-if', 11: 'differentiation', repl: 'repl',
};

// window.orreryPage.snapshot(): {lesson step steps status iterations
// stopReason classes nodes dirty bestCost} for the e-graph on show.
export const snapshot = (page) => page.evaluate(() => window.orreryPage.snapshot());

// Poll the snapshot until it holds these entries. A run steps one
// iteration per tick, so waiting on status "done" waits for the run.
export async function expectSnapshot(page, expected, timeout = 60_000) {
  await expect.poll(() => snapshot(page), { timeout }).toMatchObject(expected);
}

// Open the lesson at address n on a fresh page and wait for its
// curated run.
export async function openLesson(page, n) {
  await page.goto(`/#${n}`);
  await expectSnapshot(page, { lesson: keys[n], status: 'done' });
}

// "try another": the alternative with exactly this label.
export async function tryAnother(page, label) {
  await page.locator('.alternatives').getByRole('button', { name: label, exact: true }).click();
}

// Type into the field of each input key, then run.
export async function submit(page, fields) {
  for (const [key, text] of Object.entries(fields)) {
    await page.locator(`#input-${key}`).fill(text);
  }
  await page.locator('#run').click();
}

// The self-test tile renders after mount: green with the count, or
// red with the first failing row, which the assertion then shows.
export async function expectSelfTest(page, facts) {
  await expect(page.locator('#selftest-ok, #selftest-bad')).toBeVisible();
  expect(await page.locator('#selftest-bad').allTextContents()).toEqual([]);
  await expect(page.locator('#selftest-ok')).toHaveText(new RegExp(`${facts} facts hold in this browser`));
}

// A table as {header, rows} of trimmed cell texts.
export async function table(locator) {
  const rows = await locator.locator('tr').evaluateAll(
    trs => trs.map(tr => [...tr.children].map(td => td.textContent.trim())));
  return { header: rows[0], rows: rows.slice(1) };
}

// One column of a table, by its header.
export async function column(locator, name) {
  const t = await table(locator);
  const i = t.header.indexOf(name);
  expect(i, `a column named ${name} in ${t.header}`).toBeGreaterThanOrEqual(0);
  return t.rows.map(r => r[i]);
}

// Open the REPL's dock along the bottom of a lesson, if it is closed.
// The REPL's page has no dock; its REPL is always open.
export async function openRepl(page) {
  const toggle = page.locator('#repl-toggle');
  if (await toggle.count() && (await toggle.getAttribute('aria-expanded')) === 'false') await toggle.click();
  await expect(page.locator('#repl-input')).toBeVisible();
}

// Evaluate in the REPL panel, opening the dock first; the entry it appends.
export async function evalRepl(page, code) {
  await openRepl(page);
  const before = await page.locator('#repl-panel .entry').count();
  await page.locator('#repl-input').fill(code);
  await page.locator('#repl-eval').click();
  await expect(page.locator('#repl-panel .entry')).toHaveCount(before + 1);
  return page.locator('#repl-panel .entry').last();
}
