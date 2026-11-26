import { expect, test } from "@playwright/test";

import { es } from "../src/content/strings/es.ts";
import { pt } from "../src/content/strings/pt.ts";

for (const { path, strings } of [
  { path: "/es/", strings: es },
  { path: "/pt/", strings: pt },
])
  for (const { name, touch, minSize, width } of [
    { name: "mouse", touch: false, minSize: 24, width: 1280 },
    { name: "touch", touch: true, minSize: 44, width: 390 },
  ])
    test.describe(`${path} ${name}`, () => {
      test.use({ hasTouch: touch, viewport: { width, height: 800 } });

      test("the first Tab shows the skip link, which moves focus to the content", async ({
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
        await page.keyboard.press("Tab");
        expect(
          await page.evaluate(
            () => document.activeElement?.closest("main")?.id,
          ),
        ).toBe("contenido");
      });
    });
