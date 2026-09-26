import { test, expect } from '@playwright/test';
import { openLesson, expectSnapshot, snapshot, tryAnother, submit, column } from './orrery.js';

test.describe('5. Saturation', () => {
  test('backoff bans the busy rule, and the run still saturates', async ({ page }) => {
    await openLesson(page, 5);
    await expectSnapshot(page, { stopReason: 'saturated', classes: 15, nodes: 54 });
    await expect(page.locator('#tile-iteration .tile-note')).toHaveText('saturated: no rule can add anything');
    const { iterations } = await snapshot(page);
    const stats = page.locator('table.stats');
    await expect(stats.locator('tbody tr')).toHaveCount(iterations);
    const banned = await column(stats, 'banned');
    expect(banned.filter(Boolean).length).toBeGreaterThan(0);
    await expect(stats.locator('tbody tr').last().locator('td.applied')).toHaveCount(0);
  });

  test('try another: the same, every match every time', async ({ page }) => {
    await openLesson(page, 5);
    await tryAnother(page, 'the same, every match every time');
    await expectSnapshot(page, { status: 'done', iterations: 6, stopReason: 'saturated', classes: 15, nodes: 54 });
    const banned = await column(page.locator('table.stats'), 'banned');
    expect(banned).toHaveLength(6);
    expect(banned.filter(Boolean)).toEqual([]);
  });

  test('a node limit stops the run', async ({ page }) => {
    await openLesson(page, 5);
    await tryAnother(page, 'five atoms under a node limit of 100');
    await expectSnapshot(page, { status: 'done', stopReason: 'node-limit' });
    await expect(page.locator('#tile-iteration .tile-note')).toHaveText('stopped at the node limit');
  });

  test('a term with more than ten leaves is refused under these rules', async ({ page }) => {
    await openLesson(page, 5);
    await submit(page, { term: '[:+ [:+ [:+ [:+ [:+ [:+ [:+ [:+ [:+ [:+ :a :b] :c] :d] :e] :f] :g] :h] :i] :j] :k]' });
    await expect(page.locator('.input-area .error')).toHaveText('that has 11 leaves; under these rules the page stops at 10');
    await expectSnapshot(page, { classes: 15, nodes: 54 });
  });
});
