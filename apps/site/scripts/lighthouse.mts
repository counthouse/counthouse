// Lighthouse (mobile) on every built page, 3 runs each: the median score of each category must be
// at least 0.95, and each audit in `audits` must pass in every run. Run `npm run build` first.
import { mkdirSync, rmSync, writeFileSync } from "node:fs";
import { createServer } from "node:net";
import { join } from "node:path";

import { chromium } from "@playwright/test";
import lighthouse from "lighthouse";

import { builtPages } from "./built-pages.mts";
import { startServer } from "./serve.mts";

const runs = 3;
const minScore = 0.95;
const categories = ["performance", "accessibility", "best-practices", "seo"];
// Defects found when the checks were added, each asserted on its own: one failing audit can leave
// its category above 0.95.
const audits = [
  "charset",
  "meta-description",
  "errors-in-console",
  "image-size-responsive",
];
// Lighthouse stops on a 404 status, so it reads the file the server answers with. Not indexed on
// purpose, so no SEO score.
const notFound = "/404.html";
const reports = join(import.meta.dirname, "..", "lighthouse-report");

function freePort(): Promise<number> {
  return new Promise((resolve, reject) => {
    const probe = createServer().once("error", reject);
    probe.listen(0, "127.0.0.1", () => {
      const { port } = probe.address() as { port: number };
      probe.close(() => resolve(port));
    });
  });
}

const median = (values: number[]) =>
  [...values].sort((a, b) => a - b)[Math.floor(values.length / 2)]!;

const pages = builtPages();
if (pages.length === 0) {
  console.error("No pages in out/: run the build first.");
  process.exit(1);
}

rmSync(reports, { recursive: true, force: true });
mkdirSync(reports);
const { server, origin } = await startServer(0);
const debuggingPort = await freePort();
// The full Chromium build Playwright installed (new headless mode), pinned by the lockfile.
const browser = await chromium.launch({
  channel: "chromium",
  args: [`--remote-debugging-port=${debuggingPort}`],
});

const errors: string[] = [];
try {
  for (const path of [...pages, notFound]) {
    const scores = new Map<string, number[]>();
    for (let run = 1; run <= runs; run++) {
      const result = await lighthouse(origin + path, {
        port: debuggingPort,
        output: "html",
        logLevel: "error",
      });
      if (!result) throw new Error(`${path}: Lighthouse returned no result`);
      const { lhr, report } = result;
      const name = path.replaceAll("/", "_").replace(/^_|_$/g, "") || "root";
      writeFileSync(join(reports, `${name}-${run}.html`), report as string);
      if (lhr.runtimeError) {
        errors.push(`${path}: ${lhr.runtimeError.message}`);
        break;
      }
      for (const category of categories) {
        const score = lhr.categories[category]?.score ?? 0;
        scores.set(category, [...(scores.get(category) ?? []), score]);
      }
      for (const id of audits)
        if (lhr.audits[id]?.score !== 1)
          errors.push(`${path}, run ${run}: audit ${id} failed`);
    }
    const line: string[] = [];
    for (const [category, values] of scores) {
      const score = median(values);
      line.push(`${category} ${score}`);
      if (category === "seo" && path === notFound) continue;
      if (score < minScore)
        errors.push(
          `${path}: ${category} ${score} (runs ${values.join(", ")}), expected at least ${minScore}`,
        );
    }
    console.log(`${path}: ${line.join(", ")}`);
  }
} finally {
  await browser.close();
  server.close();
}

if (errors.length > 0) {
  console.error(`\n${errors.join("\n")}\nReports: ${reports}`);
  process.exit(1);
}
console.log(`Lighthouse passed on ${pages.length + 1} pages.`);
