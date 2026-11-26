import { expect, type Page, test } from "@playwright/test";

import { builtPages } from "../scripts/built-pages.mts";

const pages = [...builtPages(), "/no-such-page/"];

// Links inside a sentence are exempt (WCAG 2.5.8).
async function targets(page: Page) {
  return page
    .locator("a, button")
    .filter({ visible: true })
    .evaluateAll((elements) =>
      elements
        .filter((element) => !element.closest("p"))
        // The skip link is clipped to nothing until focused; skip-link.spec.ts measures it focused.
        .filter(
          (element) =>
            element.getAttribute("href") !== "#contenido" ||
            element === document.activeElement,
        )
        .map((element) => {
          const { left, top, right, bottom } = element.getBoundingClientRect();
          return {
            name: element.textContent?.trim() ?? "",
            // A fixed element floats over the page: its overlap depends on the scroll position.
            fixed: getComputedStyle(element).position === "fixed",
            left,
            top,
            right,
            bottom,
          };
        }),
    );
}

async function expectTargets(page: Page, path: string, minSize: number) {
  await page.goto(path);
  const found = await targets(page);
  if (path !== "/no-such-page/") expect(found.length).toBeGreaterThan(0);
  for (const target of found) {
    expect(target.bottom - target.top, target.name).toBeGreaterThanOrEqual(
      minSize,
    );
    expect(target.right - target.left, target.name).toBeGreaterThanOrEqual(
      minSize,
    );
  }
  const inFlow = found.filter((target) => !target.fixed);
  inFlow.forEach((a, i) =>
    inFlow.slice(i + 1).forEach((b) => {
      const overlaps =
        a.left < b.right &&
        b.left < a.right &&
        a.top < b.bottom &&
        b.top < a.bottom;
      expect(overlaps, `${a.name} overlaps ${b.name}`).toBe(false);
    }),
  );
}

for (const path of pages)
  test(`${path} targets are at least 24 px and do not overlap with a mouse`, async ({
    page,
  }) => {
    await page.setViewportSize({ width: 1280, height: 800 });
    await expectTargets(page, path, 24);
  });

test.describe("touch", () => {
  test.use({ hasTouch: true, viewport: { width: 390, height: 800 } });

  for (const path of pages)
    test(`${path} targets are at least 44 px and do not overlap on touch`, async ({
      page,
    }) => {
      await expectTargets(page, path, 44);
      expect(
        await page.evaluate(() => matchMedia("(pointer: coarse)").matches),
      ).toBe(true);
    });
});
