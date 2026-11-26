import { AxeBuilder } from "@axe-core/playwright";
import { expect, test, type Page } from "@playwright/test";

import { es } from "../src/content/strings/es.ts";
import { pt } from "../src/content/strings/pt.ts";

// The visitor is in Tokyo; the status must follow the clinic's clock (UTC−3) anyway.
test.use({ timezoneId: "Asia/Tokyo" });

const languages = [
  { path: "/es/", strings: es },
  { path: "/pt/", strings: pt },
];
// Monday 23 Nov 2026 in Rivera: open 9:00-12:00.
const beforeNoon = new Date("2026-11-23T11:59:30-03:00");
const justBeforeNoon = new Date("2026-11-23T11:59:50-03:00");
const afterNoon = new Date("2026-11-23T12:00:30-03:00");
const states = [
  { name: "open", time: beforeNoon, label: (s: typeof es) => s.openNow },
  { name: "closed", time: afterNoon, label: (s: typeof es) => s.closedNow },
];

const status = (page: Page) => page.locator("[data-open-status]");

// Hydration mismatches and render errors show up in the console.
function consoleProblems(page: Page): string[] {
  const problems: string[] = [];
  page.on("console", (message) => {
    if (["error", "warning"].includes(message.type()))
      problems.push(message.text());
  });
  page.on("pageerror", (error) => problems.push(String(error)));
  return problems;
}

for (const { path, strings } of languages) {
  test(`${path} shows the clinic's status and changes at closing time`, async ({
    page,
  }) => {
    const problems = consoleProblems(page);
    await page.clock.install({ time: justBeforeNoon });
    await page.goto(path);
    await expect(status(page)).toHaveText(strings.openNow);
    // Ten seconds reach 12:00:00 exactly: the status changes on the minute, not at a later tick.
    await page.clock.runFor(10_000);
    await expect(status(page)).toHaveText(strings.closedNow);
    expect(problems).toEqual([]);
  });

  for (const [event, target] of [
    ["visibilitychange", "document"],
    ["pageshow", "window"],
  ] as const)
    test(`${path} catches up on ${event}`, async ({ page }) => {
      await page.clock.install({ time: beforeNoon });
      await page.goto(path);
      await expect(status(page)).toHaveText(strings.openNow);
      // setSystemTime fires no timers, so only the listener can update the status.
      await page.clock.setSystemTime(afterNoon);
      await expect(status(page)).toHaveText(strings.openNow);
      await page.evaluate(
        ([event, target]) =>
          (target === "window" ? window : document).dispatchEvent(
            new Event(event),
          ),
        [event, target] as const,
      );
      await expect(status(page)).toHaveText(strings.closedNow);
    });
}

for (const { path, strings } of languages)
  test(`${path} reads each range with a word, not a dash`, async ({ page }) => {
    await page.goto(path);
    const tree = await page.locator("[data-hours]").ariaSnapshot();
    expect(tree).toContain(`9:00${strings.rangeTo}12:00`);
    expect(tree).not.toContain("–");
  });

test.describe("without JavaScript", () => {
  test.use({ javaScriptEnabled: false });
  for (const { path, strings } of languages)
    test(`${path} shows the hours and no status`, async ({ page }) => {
      await page.goto(path);
      // With JavaScript off, the page is the built HTML.
      await expect(status(page)).toHaveCount(1);
      await expect(status(page)).toBeEmpty();
      await expect(page.locator("[data-hours]")).toContainText(
        strings.emergenciesOnly,
      );
    });
});

// Lighthouse measures layout shift only at 100% text, and its clock decides which status it sees.
const layouts = [
  { name: "390 px", width: 390, height: 800, textSize: "" },
  {
    name: "320 px with text at 200%",
    width: 320,
    height: 640,
    textSize: "html { font-size: 200%; }",
  },
];
for (const layout of layouts)
  for (const { path } of languages)
    for (const state of states)
      test(`${path} keeps the status height when it appears, ${state.name}, ${layout.name}`, async ({
        browser,
      }) => {
        const height = async (javaScriptEnabled: boolean) => {
          const context = await browser.newContext({
            javaScriptEnabled,
            timezoneId: "Asia/Tokyo",
            viewport: { width: layout.width, height: layout.height },
          });
          const page = await context.newPage();
          // In the HTML itself: addStyleTag needs JavaScript, and the rule must apply from the first paint.
          await page.route(`**${path}`, async (route) => {
            const response = await route.fetch();
            const html = (await response.text()).replace(
              "</head>",
              `<style>${layout.textSize}</style></head>`,
            );
            await route.fulfill({ response, body: html });
          });
          await page.clock.install({ time: state.time });
          await page.goto(path);
          if (javaScriptEnabled) await expect(status(page)).not.toBeEmpty();
          const box = await status(page).boundingBox();
          await context.close();
          return box?.height;
        };
        const before = await height(false);
        expect(before).toBeGreaterThan(0);
        expect(await height(true)).toBe(before);
      });

// The site-wide axe run uses the real clock, so it sees only one of the two badge colors.
for (const { path, strings } of languages)
  for (const state of states)
    for (const colorScheme of ["light", "dark"] as const)
      test(`${path} has no axe violations when ${state.name}, ${colorScheme}`, async ({
        page,
      }) => {
        await page.emulateMedia({ colorScheme });
        await page.clock.install({ time: state.time });
        await page.goto(path);
        await expect(status(page)).toHaveText(state.label(strings));
        const { violations } = await new AxeBuilder({ page })
          .include("[data-open-status]")
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
