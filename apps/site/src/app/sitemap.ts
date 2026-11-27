import type { MetadataRoute } from "next";

import {
  alternates,
  type LanguageLinks,
  selectorAlternates,
} from "@/i18n/alternates";
import { locales } from "@/i18n/locales";
import { absoluteUrl } from "@/lib/site-url";

export const dynamic = "force-static";

// Each entry lists the same language versions as the page's own `hreflang` links, itself included.
const entry = ({
  canonical,
  languages,
}: LanguageLinks): MetadataRoute.Sitemap[number] => ({
  url: absoluteUrl(canonical),
  alternates: {
    languages: Object.fromEntries(
      Object.entries(languages).map(([lang, href]) => [
        lang,
        absoluteUrl(href),
      ]),
    ),
  },
});

// Every page: `check:search` fails when a built page is missing here.
export default function sitemap(): MetadataRoute.Sitemap {
  return [
    entry(selectorAlternates),
    ...locales.map((locale) => entry(alternates(locale, ""))),
  ];
}
