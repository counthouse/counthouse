"use client";

import { TextLink } from "@nicolassancho/ui";
import { usePathname } from "next/navigation";

import { stackedLinkClass } from "@/components/link-styles";
import { NavLink } from "@/components/nav-link";
import { htmlLang, type Locale, languageName, locales } from "@/i18n/locales";

// The current page in other languages. The path is read at build time, so no JavaScript is needed.
export function LanguageLinks({
  current,
  show,
}: {
  current: Locale;
  show: "others" | "all";
}) {
  const pathname = usePathname();
  const rest = pathname.split("/").slice(2).join("/");
  const shown = show === "all" ? locales : locales.filter((l) => l !== current);
  return (
    <ul className="flex flex-col gap-1">
      {shown.map((locale) => (
        <li key={locale}>
          <TextLink
            href={`/${locale}/${rest}`.replace(/\/$/, "")}
            LinkComponent={NavLink}
            lang={htmlLang[locale]}
            hrefLang={htmlLang[locale]}
            aria-current={locale === current ? "page" : undefined}
            className={`${stackedLinkClass} aria-[current=page]:font-bold aria-[current=page]:text-neutral-950`}
          >
            {languageName[locale]}
          </TextLink>
        </li>
      ))}
    </ul>
  );
}
