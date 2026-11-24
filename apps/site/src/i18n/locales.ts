export const locales = ["es", "pt"] as const;
export type Locale = (typeof locales)[number];

export function isLocale(value: string): value is Locale {
  return (locales as readonly string[]).includes(value);
}

// The text is Brazilian Portuguese; `hreflang` stays `pt` to reach every Portuguese reader.
export const htmlLang = { es: "es", pt: "pt-BR" } as const satisfies Record<
  Locale,
  string
>;

export const languageName: Record<Locale, string> = {
  es: "Español",
  pt: "Português",
};
