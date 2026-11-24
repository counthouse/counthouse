import type { Metadata } from "next";
import type { ReactNode } from "react";

import { Document } from "@/components/document";
import { clinic } from "@/content/clinic";
import { selectorAlternates } from "@/i18n/alternates";
import { htmlLang } from "@/i18n/locales";

export const metadata: Metadata = {
  metadataBase: new URL(clinic.siteUrl),
  title: clinic.fullName,
  alternates: selectorAlternates,
};

export default function SelectorLayout({ children }: { children: ReactNode }) {
  return <Document lang={htmlLang.es}>{children}</Document>;
}
