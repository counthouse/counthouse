import { AxeBuilder } from "@axe-core/playwright";
import { expect, test } from "@playwright/test";

import { builtPages } from "../scripts/built-pages.mts";

// Any path that is not a page shows the 404 page.
const notFound = "/no-such-page/";
const pages = [...builtPages(), notFound];

test("the build has the selector and both languages", () => {
  expect(builtPages()).toEqual(expect.arrayContaining(["/", "/es/", "/pt/"]));
});

test("an unknown path answers the 404 page with status 404", async ({
  page,
}) => {
  const response = await page.goto(notFound);
  expect(response?.status()).toBe(404);
  await expect(page.getByRole("heading", { level: 1 })).toHaveText(
    "Página no encontrada",
  );
  await expect(page.getByText("Página não encontrada")).toBeVisible();
});

test("a path that is not valid percent-encoding answers 400", async ({
  request,
}) => {
  expect((await request.get("/%E0%A4%A")).status()).toBe(400);
});

for (const path of pages)
  for (const colorScheme of ["light", "dark"] as const)
    for (const width of [390, 1280])
      test(`${path} has no axe violations, ${colorScheme}, ${width} px`, async ({
        page,
      }) => {
        await page.emulateMedia({ colorScheme });
        await page.setViewportSize({ width, height: 800 });
        await page.goto(path);
        const { violations } = await new AxeBuilder({ page })
          .withTags([
            "wcag2a",
            "wcag2aa",
            "wcag21a",
            "wcag21aa",
            "wcag22aa",
            "best-practice",
          ])
          .analyze();
        expect(violations).toEqual([]);
      });

// axe has no reflow rule (WCAG 1.4.10). Doubling the root font size stands in for a browser's text
// size setting (WCAG 1.4.4), which scales `rem`.
for (const path of pages)
  test(`${path} fits 320 px wide with text at 200%`, async ({ page }) => {
    await page.setViewportSize({ width: 320, height: 640 });
    await page.goto(path);
    await page.addStyleTag({ content: "html { font-size: 200%; }" });
    const layout = await page.evaluate(() => ({
      fontSize: getComputedStyle(document.documentElement).fontSize,
      // Hidden overflow would clip content and still pass the width check.
      overflowX: [document.documentElement, document.body].map(
        (element) => getComputedStyle(element).overflowX,
      ),
      scrollWidth: document.documentElement.scrollWidth,
      clientWidth: document.documentElement.clientWidth,
    }));
    expect(layout.fontSize).toBe("32px");
    expect(layout.overflowX).toEqual(["visible", "visible"]);
    expect(layout.scrollWidth).toBeLessThanOrEqual(layout.clientWidth);
  });
