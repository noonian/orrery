import { test, expect } from '@playwright/test';
import { openLesson, expectSnapshot, tryAnother } from './orrery.js';

test.describe('2. Sharing', () => {
  test('seven tree nodes, four graph nodes', async ({ page }) => {
    await openLesson(page, 2);
    await expectSnapshot(page, { steps: 1, classes: 4, nodes: 4 });
    await expect(page.locator('.tnode')).toHaveCount(7);
    await expect(page.locator('#tree-counts')).toContainText('tree nodes 7');
    await expect(page.locator('#tree-counts')).toContainText('graph nodes 4');
  });

  test('hovering a node lights every node of its class, in the tree and in the class list', async ({ page }) => {
    await openLesson(page, 2);
    const label = (top) => page.locator('.tlabel', { has: page.locator('.top', { hasText: new RegExp(`^\\${top}$`) }) });
    await label('x').first().hover();
    await expect(page.locator('.tnode.hl')).toHaveCount(2);
    await expect(page.locator('tbody tr.hovered')).toHaveCount(1);
    await label('+').first().hover();
    await expect(page.locator('.tnode.hl')).toHaveCount(2);
    await label('*').hover();
    await expect(page.locator('.tnode.hl')).toHaveCount(1);
    await expect(page.locator('tbody tr.hovered')).toHaveClass(/root/);
  });

  test('try another: (a + a) + (a + a) is three classes', async ({ page }) => {
    await openLesson(page, 2);
    await tryAnother(page, '(a + a) + (a + a)');
    await expectSnapshot(page, { status: 'done', classes: 3, nodes: 3 });
    await expect(page.locator('.tnode')).toHaveCount(7);
  });
});
