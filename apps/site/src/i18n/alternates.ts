import type { Metadata } from "next";

import { type Locale, locales } from "./locales";

// For each page's `generateMetadata`; `path` is the route after the language ("" for home).
// x-default only on the home pages: `/` links back to them alone. Checked by `check:i18n`.
export function alternates(
  locale: Locale,
  path: string,
): Metadata["alternates"] {
  const href = (l: Locale) => `/${l}/${path ? `${path}/` : ""}`;
  return {
    canonical: href(locale),
    languages: {
      ...Object.fromEntries(locales.map((l) => [l, href(l)])),
      ...(path ? {} : { "x-default": "/" }),
    },
  };
}

export const selectorAlternates: Metadata["alternates"] = {
  canonical: "/",
  languages: {
    ...Object.fromEntries(locales.map((l) => [l, `/${l}/`])),
    "x-default": "/",
  },
};
