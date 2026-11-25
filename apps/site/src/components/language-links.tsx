"use client";

import { TextLink } from "@nicolassancho/ui";
import { usePathname } from "next/navigation";

import { stackedLinkClass } from "@/components/link-styles";
import { NavLink } from "@/components/nav-link";
import {
  htmlLang,
  type Locale,
  languageName,
  localeHref,
  locales,
} from "@/i18n/locales";

// The current page in the other languages. The path is read at build time, so no JavaScript is needed.
export function LanguageLinks({ locale }: { locale: Locale }) {
  const pathname = usePathname();
  return (
    <ul className="flex flex-col gap-2">
      {locales
        .filter((other) => other !== locale)
        .map((other) => (
          <li key={other}>
            <TextLink
              href={localeHref(pathname, other)}
              LinkComponent={NavLink}
              lang={htmlLang[other]}
              hrefLang={htmlLang[other]}
              className={stackedLinkClass}
            >
              {languageName[other]}
            </TextLink>
          </li>
        ))}
    </ul>
  );
}
