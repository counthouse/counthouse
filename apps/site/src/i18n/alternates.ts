import { type Locale, locales } from "./locales";

/** A page's canonical path and its `hreflang` links, read by its metadata and by the sitemap. */
export type LanguageLinks = {
  canonical: string;
  languages: Record<string, string>;
};

// For each page's `generateMetadata`; `path` is the route after the language ("" for home).
// x-default only on the home pages: `/` links back to them alone. Checked by `check:i18n`.
export function alternates(locale: Locale, path: string): LanguageLinks {
  const href = (l: Locale) => `/${l}/${path ? `${path}/` : ""}`;
  return {
    canonical: href(locale),
    languages: {
      ...Object.fromEntries(locales.map((l) => [l, href(l)])),
      ...(path ? {} : { "x-default": "/" }),
    },
  };
}

export const selectorAlternates: LanguageLinks = {
  canonical: "/",
  languages: {
    ...Object.fromEntries(locales.map((l) => [l, `/${l}/`])),
    "x-default": "/",
  },
};
