// Checks `out/` after `next build`: robots.txt, a sitemap that lists every built page with the
// same language links as the page, link preview tags, the preview image, and the structured data.
import { existsSync, readFileSync, statSync } from "node:fs";
import { isDeepStrictEqual } from "node:util";
import { join } from "node:path";

import { clinic } from "../src/content/clinic.ts";
import { businessData } from "../src/lib/business-data.ts";
import { shareImage } from "../src/lib/share-image.ts";
import { absoluteUrl } from "../src/lib/site-url.ts";
import { builtPages, out } from "./built-pages.mts";

const origin = new URL(clinic.siteUrl).origin;
const errors: string[] = [];
const read = (file: string) =>
  existsSync(join(out, file)) ? readFileSync(join(out, file), "utf8") : "";

// Every URL of this site that crawlers follow must be on `clinic.siteUrl` and reach a built file.
// Origins are compared parsed, never by prefix: "https://anima.invalid.example" starts with the
// site URL too.
function checkSiteUrl(where: string, url: string | undefined) {
  if (!url) return errors.push(`${where}: missing`);
  let parsed: URL;
  try {
    parsed = new URL(url);
  } catch {
    return errors.push(`${where}: ${url} is not an absolute URL`);
  }
  if (parsed.origin !== origin)
    return errors.push(`${where}: ${url} is not on ${origin}`);
  const file = join(out, decodeURIComponent(parsed.pathname));
  const target =
    existsSync(file) && statSync(file).isDirectory()
      ? join(file, "index.html")
      : file;
  if (!existsSync(target))
    errors.push(`${where}: ${url} is not a file in the build`);
}

const robots = read("robots.txt");
const expectedRobots = `User-Agent: *\nAllow: /\n\nSitemap: ${absoluteUrl("/sitemap.xml")}\n`;
if (robots !== expectedRobots)
  errors.push(`robots.txt: expected\n${expectedRobots}got\n${robots}`);
checkSiteUrl("robots.txt Sitemap", robots.match(/^Sitemap: (.+)$/m)?.[1]);

// The page's own `hreflang` links, as written in its HTML.
const languageLinks = (head: string) =>
  new Map(
    [
      ...head.matchAll(
        /<link rel="alternate" hrefLang="([^"]+)" href="([^"]+)"/g,
      ),
    ].map((m) => [m[1]!, m[2]!]),
  );
const sameLinks = (a: Map<string, string>, b: Map<string, string>) =>
  a.size === b.size && [...a].every(([lang, href]) => b.get(lang) === href);

const sitemap = new Map(
  [...read("sitemap.xml").matchAll(/<url>([\s\S]*?)<\/url>/g)].map((m) => {
    const block = m[1]!;
    const links = new Map(
      [
        ...block.matchAll(
          /<xhtml:link rel="alternate" hreflang="([^"]+)" href="([^"]+)"/g,
        ),
      ].map((l) => [l[1]!, l[2]!]),
    );
    return [block.match(/<loc>([^<]+)<\/loc>/)?.[1] ?? "", links];
  }),
);
for (const [loc, links] of sitemap) {
  checkSiteUrl(`sitemap <loc>`, loc);
  for (const [lang, href] of links)
    checkSiteUrl(`sitemap ${loc} hreflang ${lang}`, href);
}

const ogLocale: Record<string, string> = { es: "es_UY", "pt-BR": "pt_BR" };
const pages = builtPages();
for (const path of pages) {
  const html = read(join(path, "index.html"));
  const head = html.slice(0, html.indexOf("</head>"));
  const meta = (key: string) =>
    head.match(
      new RegExp(`<meta (?:property|name)="${key}" content="([^"]*)"`),
    )?.[1];
  const url = absoluteUrl(path);
  const canonical = head.match(/<link rel="canonical" href="([^"]+)"/)?.[1];
  checkSiteUrl(`${path} canonical`, canonical);

  // x-default stays on the language selector and the home pages, the only pages `/` links back to.
  const isHome = path === "/" || /^\/[^/]+\/$/.test(path);
  if (isHome && languageLinks(head).get("x-default") !== absoluteUrl("/"))
    errors.push(`${path}: no x-default link to ${absoluteUrl("/")}`);

  const listed = sitemap.get(url);
  if (!listed) errors.push(`${path}: not in sitemap.xml`);
  else if (!sameLinks(listed, languageLinks(head)))
    errors.push(
      `${path}: sitemap.xml language links differ from the page's hreflang links`,
    );

  if (meta("og:url") !== canonical)
    errors.push(`${path}: og:url ${meta("og:url")}, canonical ${canonical}`);
  for (const key of ["og:title", "og:description", "og:image:alt"])
    if (!meta(key)) errors.push(`${path}: no ${key}`);
  const lang = html.match(/<html[^>]*\slang="([^"]+)"/)?.[1] ?? "";
  if (meta("og:locale") !== ogLocale[lang])
    errors.push(
      `${path}: og:locale ${meta("og:locale")} for lang "${lang}", expected ${ogLocale[lang]}`,
    );
  checkSiteUrl(`${path} og:image`, meta("og:image"));
  if (
    meta("og:image") !== absoluteUrl(shareImage.url) ||
    meta("og:image:width") !== String(shareImage.width) ||
    meta("og:image:height") !== String(shareImage.height) ||
    meta("og:image:type") !== shareImage.type
  )
    errors.push(
      `${path}: og:image is not ${shareImage.url} with its size and type`,
    );
  if (meta("twitter:card") !== "summary_large_image")
    errors.push(`${path}: twitter:card ${meta("twitter:card")}`);

  // Language pages describe the clinic; the selector does not.
  const scripts = [
    ...html.matchAll(/<script type="application\/ld\+json">([^<]*)<\/script>/g),
  ].map((m) => m[1]!);
  const segment = path.split("/")[1] ?? "";
  if (segment === "") continue;
  if (scripts.length !== 1) {
    errors.push(`${path}: ${scripts.length} JSON-LD scripts, expected 1`);
    continue;
  }
  let data: ReturnType<typeof businessData>;
  try {
    data = JSON.parse(scripts[0]!);
  } catch {
    errors.push(`${path}: JSON-LD does not parse`);
    continue;
  }
  if (!isDeepStrictEqual(data, businessData(`/${segment}/`)))
    errors.push(
      `${path}: JSON-LD differs from businessData("/${segment}/"):\n${JSON.stringify(data)}`,
    );
  for (const key of ["url", "logo", "image"] as const)
    checkSiteUrl(`${path} JSON-LD ${key}`, data[key]);
}
const sitemapEntries = read("sitemap.xml").match(/<url>/g)?.length ?? 0;
if (sitemapEntries !== sitemap.size)
  errors.push(
    `sitemap.xml: ${sitemapEntries - sitemap.size} duplicate entries`,
  );
for (const loc of sitemap.keys())
  if (!pages.includes(new URL(loc).pathname))
    errors.push(`sitemap.xml: ${loc} is not a built page`);

// A PNG starts with an 8-byte signature, then the IHDR chunk: width and height at bytes 16-23.
const png = existsSync(join(out, shareImage.url))
  ? readFileSync(join(out, shareImage.url))
  : Buffer.alloc(0);
if (
  png.length < 24 ||
  png.toString("latin1", 1, 4) !== "PNG" ||
  png.readUInt32BE(16) !== shareImage.width ||
  png.readUInt32BE(20) !== shareImage.height
)
  errors.push(
    `${shareImage.url}: not a ${shareImage.width}×${shareImage.height} PNG`,
  );

if (pages.length === 0)
  errors.push(`no pages found in ${out}: run the build first`);
if (errors.length > 0) {
  console.error(errors.join("\n"));
  process.exit(1);
}
console.log(
  `Sitemap, robots.txt, link previews and structured data are consistent on ${pages.length} pages.`,
);
