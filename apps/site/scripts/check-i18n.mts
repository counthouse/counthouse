// Checks `out/` after `next build`: `lang`, a self canonical, and two-way `hreflang` links.
import { readFileSync } from "node:fs";
import { join } from "node:path";

import { clinic } from "../src/content/clinic.ts";
import { htmlLang, isLocale } from "../src/i18n/locales.ts";
import { builtPages, out } from "./built-pages.mts";

const origin = new URL(clinic.siteUrl).origin;

interface Page {
  path: string;
  lang: string | undefined;
  canonical: string | undefined;
  alternates: Map<string, string>;
}

const pages = new Map<string, Page>();
for (const path of builtPages()) {
  const html = readFileSync(join(out, path, "index.html"), "utf8");
  const head = html.slice(0, html.indexOf("</head>"));
  pages.set(origin + path, {
    path,
    lang: html.match(/<html[^>]*\slang="([^"]+)"/)?.[1],
    canonical: head.match(/<link rel="canonical" href="([^"]+)"/)?.[1],
    alternates: new Map(
      [
        ...head.matchAll(
          /<link rel="alternate" hrefLang="([^"]+)" href="([^"]+)"/g,
        ),
      ].map((m) => [m[1]!, m[2]!]),
    ),
  });
}

const errors: string[] = [];
for (const [url, page] of pages) {
  const segment = page.path.split("/")[1] ?? "";
  const expectedLang = isLocale(segment) ? htmlLang[segment] : htmlLang.es;
  if (page.lang !== expectedLang)
    errors.push(
      `${page.path}: lang "${page.lang}", expected "${expectedLang}"`,
    );
  if (page.canonical !== url)
    errors.push(`${page.path}: canonical ${page.canonical}, expected ${url}`);
  if (![...page.alternates.values()].includes(url))
    errors.push(`${page.path}: no hreflang link to itself`);
  for (const [hreflang, target] of page.alternates) {
    const other = pages.get(target);
    if (!other)
      errors.push(
        `${page.path}: hreflang ${hreflang} points to ${target}, which is not a built page`,
      );
    else if (![...other.alternates.values()].includes(url))
      errors.push(
        `${page.path}: hreflang ${hreflang} points to ${target}, which does not link back`,
      );
  }
}

if (pages.size === 0)
  errors.push(`no pages found in ${out}: run the build first`);
if (errors.length > 0) {
  console.error(errors.join("\n"));
  process.exit(1);
}
console.log(`Language links are consistent on ${pages.size} pages.`);
