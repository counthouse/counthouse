import { expect, test } from "@playwright/test";

import { hours } from "../src/content/hours.ts";
import { services } from "../src/content/services.ts";
import { es } from "../src/content/strings/es.ts";
import { pt } from "../src/content/strings/pt.ts";
import type { Locale } from "../src/i18n/locales.ts";
import { formatTime, groupDays } from "../src/lib/hours-list.ts";

// The page must show what the content files hold. A hard-coded copy fails here once it differs
// from the content; one that still matches is not seen, so "each fact exists once" is otherwise
// checked in review.
test.use({ javaScriptEnabled: false });

for (const { locale, strings } of [
  { locale: "es", strings: es },
  { locale: "pt", strings: pt },
] as { locale: Locale; strings: typeof es }[])
  test(`/${locale}/ lists every service and every group of hours`, async ({
    page,
  }) => {
    await page.goto(`/${locale}/`);
    const main = page.getByRole("main");
    for (const { name, summary } of services) {
      await expect(
        main.getByRole("heading", {
          level: 3,
          name: name[locale],
          exact: true,
        }),
      ).toBeVisible();
      await expect(
        main.getByText(summary[locale], { exact: true }),
      ).toBeVisible();
    }
    const groups = main.locator("[data-hours] > div");
    await expect(groups).toHaveText(
      groupDays(hours).map(({ from, to, ranges }) =>
        [
          strings.dayGroup(from, to),
          ...(ranges.length === 0
            ? [strings.emergenciesOnly]
            : ranges.map(
                ([start, end]) =>
                  `${formatTime(start, locale)}–${strings.rangeTo}${formatTime(end, locale)}`,
              )),
        ].join(""),
      ),
    );
  });
