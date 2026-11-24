import type { Locale } from "@/i18n/locales";

import { es, type Strings } from "./es";
import { pt } from "./pt";

const byLocale: Record<Locale, Strings> = { es, pt };

// Functions cannot be passed to Client Components: call them on the server, pass the string.
export function getStrings(locale: Locale): Strings {
  return byLocale[locale];
}

export type { Strings };
