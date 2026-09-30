// The graph picture and the export: drawn by default, hidden on request
// as SVG with a box per class, following the hover, the opened class
// and the step; the filter to what the opened class reaches; zoom;
// and the e-graph on show as egraph-serialize JSON, downloaded or
// copied. Counts, never ids.
import fs from 'node:fs';
import { test, expect } from '@playwright/test';
import { openLesson, expectSnapshot, showTab } from './orrery.js';

const detail = (page) => page.locator('#replay .class-detail');
const act = (page, kind, text) => page.locator(`.prose a.act[data-act=${kind}]`, { hasText: text });

test.describe('the graph picture', () => {
  test('lesson 2: drawn by default; four boxes, four nodes, four edges; a box opens its class and lights its row', async ({ page }) => {
    await openLesson(page, 2);
    await expect(page.locator('#graph svg')).toBeVisible();
    await expect(page.locator('#graph-toggle')).toHaveText('hide the graph');
    await expect(page.locator('#graph .eclass')).toHaveCount(4);
    await expect(page.locator('#graph .enode')).toHaveCount(4);
    await expect(page.locator('#graph .edge')).toHaveCount(4);
    await expect(page.locator('#graph .eclass.root')).toHaveCount(1);
    await expect(page.locator('#graph-counts')).toHaveText('4 classes · 4 nodes drawn');
    await expect(page.locator('#graph .enode text').first()).toBeVisible();

    const root = page.locator('#graph .eclass.root');
    await root.hover();
    await expect(page.locator('tbody tr.hovered')).toHaveCount(1);
    await expect(page.locator('#graph .eclass.hovered')).toHaveCount(1);
    await expect(page.locator('#graph .edge.lit')).toHaveCount(2);
    await root.click();
    await expect(detail(page)).toBeVisible();
    await expect(detail(page).locator('.detail-title')).toContainText("the input's class");
    await expect(page.locator('#graph .eclass.selected')).toHaveCount(1);

    await page.locator('#graph-toggle').click();
    await expect(page.locator('#graph')).toHaveCount(0);
    await expect(page.locator('#graph-toggle')).toHaveText('draw the graph');
  });

  test('lesson 2: the filter draws what the opened class reaches; zoom changes the size', async ({ page }) => {
    await openLesson(page, 2);
    await expect(page.locator('#graph-filter')).toBeDisabled();
    await act(page, 'select', 'Open that class').click();
    await expect(page.locator('#graph-filter')).toBeEnabled();
    await page.locator('#graph-filter').check();
    await expect(page.locator('#graph .eclass')).toHaveCount(3);
    await expect(page.locator('#graph .edge')).toHaveCount(2);
    await page.locator('#graph-filter').uncheck();
    await expect(page.locator('#graph .eclass')).toHaveCount(4);

    const width = async () => Number(await page.locator('#graph svg').getAttribute('width'));
    const natural = await width();
    await expect(page.locator('#graph-fit')).toBeDisabled();
    await page.locator('#graph-zoom-in').click();
    expect(await width()).toBe(natural);
    await page.locator('#graph-zoom-in').click();
    expect(await width()).toBe(natural * 1.5);
    await page.locator('#graph-zoom-out').click();
    await page.locator('#graph-zoom-out').click();
    expect(await width()).toBe(natural * 0.75);
    await expect(page.locator('#graph-fit')).toBeEnabled();
    await page.locator('#graph-fit').click();
    expect(await width()).toBe(natural);
    await expect(page.locator('#graph-fit')).toBeDisabled();
  });

  test('the zoomed picture moves when dragged, and a drag that ends on a box does not open it', async ({ page }) => {
    await openLesson(page, 8);
    for (let i = 0; i < 4; i++) await page.locator('#graph-zoom-in').click();
    const frame = page.locator('#graph .graph-scroll');
    await frame.scrollIntoViewIfNeeded();
    // a box with room around it, scrolled to the middle of the frame
    const [left, top] = await frame.evaluate((el) => {
      const f = el.getBoundingClientRect();
      const half = [el.clientWidth / 2, el.clientHeight / 2];
      for (const r of [...el.querySelectorAll('.eclass .box')].map((b) => b.getBoundingClientRect())) {
        const cx = r.x - f.x + el.scrollLeft + r.width / 2, cy = r.y - f.y + el.scrollTop + r.height / 2;
        if (cx > half[0] + 100 && cx < el.scrollWidth - half[0] - 100 && cy > half[1] + 100 && cy < el.scrollHeight - half[1] - 100) {
          el.scrollLeft = Math.round(cx - half[0]);
          el.scrollTop = Math.round(cy - half[1]);
          return [el.scrollLeft, el.scrollTop];
        }
      }
    });
    const f = await frame.boundingBox();
    const x = f.x + f.width / 2, y = f.y + f.height / 2;
    await page.mouse.move(x, y);
    await page.mouse.down();
    await page.mouse.move(x - 60, y - 40, { steps: 5 });
    expect(await frame.evaluate((el) => [el.scrollLeft, el.scrollTop])).toEqual([left + 60, top + 40]);
    // the picture moved with the pointer, so the press and the release are on the same box
    await page.mouse.up();
    await expect(page.locator('#graph .eclass.selected')).toHaveCount(0);
    await page.mouse.click(x - 60, y - 40);
    await expect(page.locator('#graph .eclass.selected')).toHaveCount(1);
  });

  test('lesson 3: the merged class is marked, its two parents point at it, and the rebuild folds them', async ({ page }) => {
    await openLesson(page, 3);
    await expect(page.locator('#graph .eclass')).toHaveCount(5);
    await page.locator('#step-prev').click();
    await expectSnapshot(page, { step: 1 });
    await expect(page.locator('#graph .eclass')).toHaveCount(6);
    await expect(page.locator('#graph .eclass.absorbing')).toHaveCount(1);
    await expect(page.locator('#graph .eclass.absorbing .enode')).toHaveCount(2);
    await page.locator('#step-first').click();
    await expectSnapshot(page, { step: 0 });
    await expect(page.locator('#graph .eclass')).toHaveCount(7);
    await expect(page.locator('#graph .eclass.absorbing')).toHaveCount(0);
  });

  test('lesson 4: the nodes an iteration adds are marked, and the print mode changes the labels', async ({ page }) => {
    await openLesson(page, 4);
    await expectSnapshot(page, { step: 1 });
    await expect(page.locator('#graph .enode.added')).toHaveCount(3);
    await expect(page.locator('#graph .enode text', { hasText: '<<' })).toHaveCount(2);
    await page.locator('.print-toggle button', { hasText: 'native' }).click();
    await expect(page.locator('#graph .enode text', { hasText: '[:<<' })).toHaveCount(2);
  });

  test('lesson 10: a dashed edge closes the cycle, and the polynomial sits in the box', async ({ page }) => {
    await openLesson(page, 10);
    await expect(page.locator('#graph .edge.back').first()).toBeVisible();
    await expect(page.locator('#graph .eclass .sub', { hasText: 'a + b + 1' })).toHaveCount(1);
  });

  test('lesson 7: the blowup is wide, fitted to the panel by default', async ({ page }) => {
    await openLesson(page, 7);
    await expect(page.locator('#graph .eclass')).toHaveCount(31);
    await expect(page.locator('#graph .enode')).toHaveCount(185);
    await expect(page.locator('#graph .edge')).toHaveCount(360);
    const box = await page.locator('#graph svg').boundingBox();
    const panel = await page.locator('#graph .graph-scroll').boundingBox();
    expect(box.width).toBeLessThanOrEqual(panel.width);
    expect(Number(await page.locator('#graph svg').getAttribute('width'))).toBeGreaterThan(panel.width);
  });

  test('lesson 11: the fork draws the step on show', async ({ page }) => {
    await openLesson(page, 11);
    await expectSnapshot(page, { step: 2 });
    const shown = await page.locator('#graph .eclass').count();
    await page.locator('#step-first').click();
    await expectSnapshot(page, { step: 0 });
    expect(await page.locator('#graph .eclass').count()).toBeGreaterThan(shown);
  });
});

test.describe('the export', () => {
  test.use({ permissions: ['clipboard-read', 'clipboard-write'] });

  test('lesson 2: the download is egraph-serialize JSON of the step on show', async ({ page }) => {
    await openLesson(page, 2);
    const [download] = await Promise.all([
      page.waitForEvent('download'),
      page.locator('#export-download').click(),
    ]);
    expect(download.suggestedFilename()).toBe('orrery-sharing-step-0.json');
    const json = JSON.parse(fs.readFileSync(await download.path(), 'utf8'));
    expect(Object.keys(json.nodes)).toHaveLength(4);
    expect(json.root_eclasses).toHaveLength(1);
    expect(json.class_data).toEqual({});
    const root = Object.values(json.nodes).find(n => n.eclass === json.root_eclasses[0]);
    expect(root.op).toBe('*');
    expect(root.children).toHaveLength(2);
    expect(root.children[0]).toBe(root.children[1]);
    expect(root.cost).toBe(7);
    for (const n of Object.values(json.nodes)) {
      for (const c of n.children) expect(json.nodes[c].eclass).toBe(c.split('.')[0]);
    }
    await expect(page.locator('#export-status')).toHaveText('downloaded orrery-sharing-step-0.json');
  });

  test('lesson 8: the copy carries the polynomials as class data, costed under the cost in force', async ({ page }) => {
    await openLesson(page, 8);
    await page.locator('#export-copy').click();
    await expect(page.locator('#export-status')).toContainText('copied orrery-fix-step-2.json');
    const json = JSON.parse(await page.evaluate(() => navigator.clipboard.readText()));
    expect(Object.keys(json.nodes).length).toBeGreaterThan(12);
    const types = new Set(Object.values(json.class_data).map(d => d.type));
    expect(types.has('polynomial')).toBe(true);
    expect(Object.values(json.class_data).some(d => d.poly === 'a0 + a1 + a2 + a3 + a4')).toBe(true);
    const root = json.nodes[`${json.root_eclasses[0]}.0`];
    expect(root.eclass).toBe(json.root_eclasses[0]);
    await showTab(page, 'results');
    await page.locator('input[type=radio][value=ast-size]').check();
    await showTab(page, 'egraph');
    await page.locator('#export-copy').click();
    await expect(page.locator('#export-status')).toContainText('copied');
    const json2 = JSON.parse(await page.evaluate(() => navigator.clipboard.readText()));
    expect(Object.values(json2.nodes).every(n => Number.isInteger(n.cost))).toBe(true);
  });
});
