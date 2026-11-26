import { expect, test } from "@playwright/test";

import { builtPages } from "../scripts/built-pages.mts";
import { htmlLang, locales } from "../src/i18n/locales.ts";

const built = builtPages();
const localePages = built.flatMap((path) => {
  const locale = locales.find((l) => path.startsWith(`/${l}/`));
  return locale ? [{ path, locale }] : [];
});

// Reads the prerendered HTML: the links must not depend on JavaScript.
test.use({ javaScriptEnabled: false });

const samePageIn = (path: string, locale: string) =>
  `/${locale}/${path.split("/").slice(2).join("/")}`;

test("there are pages in every language", () => {
  for (const locale of locales)
    expect(localePages.some((p) => p.locale === locale)).toBe(true);
});

for (const { path, locale } of localePages) {
  test(`language links on ${path}`, async ({ page }) => {
    await page.goto(path);
    const read = (selector: string) =>
      page.locator(selector).evaluateAll((links) =>
        links.map((link) => ({
          href: link.getAttribute("href"),
          lang: link.getAttribute("lang"),
          hreflang: link.getAttribute("hreflang"),
          current: link.getAttribute("aria-current"),
        })),
      );

    const footer = await read("footer a[hreflang]");
    expect(footer).toEqual(
      locales.map((l) => ({
        href: samePageIn(path, l),
        lang: htmlLang[l],
        hreflang: htmlLang[l],
        current: l === locale ? "page" : null,
      })),
    );
    for (const { href } of footer) expect(built).toContain(href);

    const header = await read("header a[hreflang]");
    expect(header).toEqual(
      locales
        .filter((l) => l !== locale)
        .map((l) => ({
          href: samePageIn(path, l),
          lang: htmlLang[l],
          hreflang: htmlLang[l],
          current: null,
        })),
    );
  });
}

// From md the language link sits beside the large WhatsApp button; 8 px read as one cramped group.
for (const width of [940, 1280])
  test(`the header language link is at least 24 px from WhatsApp at ${width} px`, async ({
    page,
  }) => {
    await page.setViewportSize({ width, height: 800 });
    await page.goto("/es/");
    const header = page.locator("header");
    const language = await header
      .getByRole("link", { name: "Português" })
      .boundingBox();
    const whatsapp = await header
      .getByRole("link", { name: "WhatsApp" })
      .boundingBox();
    expect(
      whatsapp!.x - (language!.x + language!.width),
    ).toBeGreaterThanOrEqual(24);
  });
