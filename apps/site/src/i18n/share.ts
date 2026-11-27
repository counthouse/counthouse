import type { Metadata } from "next";

import { clinic } from "@/content/clinic";
import { getStrings } from "@/content/strings";
import { shareImage } from "@/lib/share-image";

import { type Locale, locales } from "./locales";

const ogLocale: Record<Locale, string> = { es: "es_UY", pt: "pt_BR" };

/**
 * Link preview tags for one page: `path` is its canonical path, `locale` its language (none for the
 * language selector, which shows both). Metadata objects merge shallowly, so a page that sets
 * `openGraph` replaces the layout's: every page passes the whole object from here.
 */
export function shareMetadata(
  path: string,
  locale?: Locale,
): Pick<Metadata, "openGraph" | "twitter"> {
  const main = locale ?? "es";
  return {
    openGraph: {
      type: "website",
      siteName: clinic.fullName,
      url: path,
      locale: ogLocale[main],
      alternateLocale: locales
        .filter((l) => l !== main)
        .map((l) => ogLocale[l]),
      images: { ...shareImage, alt: getStrings(main).shareImageAlt },
    },
    twitter: { card: "summary_large_image" },
  };
}
