"use client";

import { Footer } from "@nicolassancho/ui";
import { usePathname } from "next/navigation";
import type { ReactNode } from "react";

import { NavLink } from "@/components/nav-link";
import {
  htmlLang,
  type Locale,
  languageName,
  localeHref,
  locales,
} from "@/i18n/locales";

// A client component only for `usePathname`: the locale layout does not know the page's path.
// The path is read at build time, so the links need no JavaScript.
export function FooterWithLanguages({
  brand,
  className,
  languageTitle,
  legal,
  locale,
  navLabel,
}: {
  brand: ReactNode;
  className?: string;
  languageTitle: string;
  legal: string;
  locale: Locale;
  navLabel: string;
}) {
  const pathname = usePathname();
  return (
    <Footer
      navLabel={navLabel}
      className={className}
      brand={brand}
      legal={legal}
      LinkComponent={NavLink}
      columns={[
        {
          title: languageTitle,
          links: locales.map((l) => ({
            label: languageName[l],
            href: localeHref(pathname, l),
            lang: htmlLang[l],
            hrefLang: htmlLang[l],
            current: l === locale,
          })),
        },
      ]}
    />
  );
}
