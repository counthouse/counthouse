import type { Metadata } from "next";
import { notFound } from "next/navigation";

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

// No `alternates` here: every page below a layout inherits its value.
export const metadata: Metadata = {
  metadataBase: new URL(clinic.siteUrl),
  title: clinic.fullName,
};

export default async function LocaleLayout({
  children,
  params,
}: LayoutProps<"/[locale]">) {
  const { locale } = await params;
  if (!isLocale(locale)) notFound();
  const strings = getStrings(locale);
  return (
    <Document lang={htmlLang[locale]}>
      <SiteHeader clinic={clinic} locale={locale} strings={strings} />
      {children}
      <SiteFooter clinic={clinic} locale={locale} strings={strings} />
    </Document>
  );
}
