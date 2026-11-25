export const locales = ["es", "pt"] as const;
export type Locale = (typeof locales)[number];

export function isLocale(value: string): value is Locale {
  return (locales as readonly string[]).includes(value);
}

// The text is Brazilian Portuguese. Only `alternates()` uses `pt`, so search engines show it to every Portuguese reader.
export const htmlLang = { es: "es", pt: "pt-BR" } as const satisfies Record<
  Locale,
  string
>;

export const languageName: Record<Locale, string> = {
  es: "Español",
  pt: "Português",
};

/** The page at `pathname` in `locale`, without the trailing slash (`next/link` adds it). */
export function localeHref(pathname: string, locale: Locale): string {
  const rest = pathname.split("/").slice(2).join("/");
  return `/${locale}/${rest}`.replace(/\/$/, "");
}
