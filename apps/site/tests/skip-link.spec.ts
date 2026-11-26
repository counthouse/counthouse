import { expect, test } from "@playwright/test";

import { builtPages } from "../scripts/built-pages.mts";
import { es } from "../src/content/strings/es.ts";
import { pt } from "../src/content/strings/pt.ts";
import { isLocale } from "../src/i18n/locales.ts";

// Every page in a language has the skip link; its target is the page's h1.
const pages = builtPages().flatMap((path) => {
  const locale = path.split("/")[1] ?? "";
  return isLocale(locale) ? [{ path, strings: { es, pt }[locale] }] : [];
});

test("the build has pages with a skip link", () => {
  expect(pages.length).toBeGreaterThan(0);
});

for (const { path, strings } of pages)
  for (const { name, touch, minSize, width } of [
    { name: "mouse", touch: false, minSize: 24, width: 1280 },
    { name: "touch", touch: true, minSize: 44, width: 390 },
  ])
    test.describe(`${path} ${name}`, () => {
      test.use({ hasTouch: touch, viewport: { width, height: 800 } });

      test("the first Tab shows the skip link, which moves focus to the h1", async ({
        page,
      }) => {
        await page.goto(path);
        await page.keyboard.press("Tab");
        const skip = page.getByRole("link", { name: strings.skipToContent });
        await expect(skip).toBeFocused();
        const box = await skip.boundingBox();
        expect(box?.height).toBeGreaterThanOrEqual(minSize);
        expect(box?.width).toBeGreaterThanOrEqual(minSize);
        await page.keyboard.press("Enter");
        await expect(page).toHaveURL(/#contenido$/);
        await expect(page.getByRole("heading", { level: 1 })).toBeFocused();
        await page.keyboard.press("Tab");
        expect(
          await page.evaluate(() => !!document.activeElement?.closest("main")),
        ).toBe(true);
      });
    });
