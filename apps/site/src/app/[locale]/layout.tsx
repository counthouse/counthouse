import { focusRing, targetSize } from "@nicolassancho/ui";
import type { Metadata } from "next";
import { notFound } from "next/navigation";

import { BusinessData } from "@/components/business-data";
import { Document } from "@/components/document";
import { SiteFooter } from "@/components/site-footer";
import { SiteHeader } from "@/components/site-header";
import { clinic } from "@/content/clinic";
import { getStrings } from "@/content/strings";
import { htmlLang, isLocale, locales } from "@/i18n/locales";

export const dynamicParams = false;
export function generateStaticParams() {
  return locales.map((locale) => ({ locale }));
}

// No `alternates` or `description` here: every page below a layout would inherit its value.
export async function generateMetadata({
  params,
}: LayoutProps<"/[locale]">): Promise<Metadata> {
  const { locale } = await params;
  if (!isLocale(locale)) notFound();
  return {
    metadataBase: new URL(clinic.siteUrl),
    title: clinic.fullName,
  };
}

export default async function LocaleLayout({
  children,
  params,
}: LayoutProps<"/[locale]">) {
  const { locale } = await params;
  if (!isLocale(locale)) notFound();
  const strings = getStrings(locale);
  return (
    <Document lang={htmlLang[locale]}>
      <a
        href="#contenido"
        className={`sr-only z-30 items-center rounded-sm border border-neutral-400 bg-surface font-semibold focus:px-4 text-neutral-950 focus:not-sr-only focus:fixed focus:top-4 focus:left-4 focus:inline-flex ${targetSize} ${focusRing}`}
      >
        {strings.skipToContent}
      </a>
      <SiteHeader clinic={clinic} locale={locale} strings={strings} />
      <main className="flex-1">{children}</main>
      <BusinessData homePath={`/${locale}/`} />
      <SiteFooter clinic={clinic} locale={locale} strings={strings} />
    </Document>
  );
}
