import { expect, type Page, test } from "@playwright/test";

import { builtPages } from "../scripts/built-pages.mts";

// Below `md` the WhatsApp link is fixed to the bottom of the screen. Scrolled to the end, it must clear the legal
// line without leaving the page much longer than it needs to be.
const pages = builtPages().filter((path) => /^\/(es|pt)\//.test(path));

const cases = [
  { name: "390 px", width: 390, height: 800, textScale: false },
  {
    name: "320 px with text at 200%",
    width: 320,
    height: 640,
    textScale: true,
  },
];

async function gapAboveFloatingAction(page: Page) {
  const action = page.locator('header a[href^="https://wa.me/"]');
  const legal = page.locator("footer").getByText(/^©/);
  await expect(action).toHaveCSS("position", "fixed");
  await page.evaluate(() => document.fonts.ready);
  await page.evaluate(() =>
    window.scrollTo(0, document.documentElement.scrollHeight),
  );
  const actionBox = await action.boundingBox();
  const legalBox = await legal.boundingBox();
  if (!actionBox || !legalBox) throw new Error("not rendered");
  // Rounded: line boxes have fractional heights.
  return Math.round(actionBox.y - (legalBox.y + legalBox.height));
}

test("there are pages with the floating WhatsApp link", () => {
  expect(pages.length).toBeGreaterThan(0);
});

for (const path of pages)
  for (const { name, width, height, textScale } of cases)
    test(`${path} ends just below the legal line, ${name}`, async ({
      page,
    }) => {
      await page.setViewportSize({ width, height });
      await page.goto(path);
      if (textScale)
        await page.addStyleTag({ content: "html { font-size: 200%; }" });
      const gap = await gapAboveFloatingAction(page);
      expect(gap).toBeGreaterThanOrEqual(8);
      expect(gap).toBeLessThanOrEqual(24);
    });
