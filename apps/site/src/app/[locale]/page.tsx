import { Container } from "@nicolassancho/ui";
import type { Metadata } from "next";
import { notFound } from "next/navigation";

import { clinic } from "@/content/clinic";
import { getStrings } from "@/content/strings";
import { alternates } from "@/i18n/alternates";
import { isLocale } from "@/i18n/locales";

export async function generateMetadata({
  params,
}: PageProps<"/[locale]">): Promise<Metadata> {
  const { locale } = await params;
  if (!isLocale(locale)) notFound();
  return { alternates: alternates(locale, "") };
}

// Placeholder until the pages are built.
export default async function HomePage({ params }: PageProps<"/[locale]">) {
  const { locale } = await params;
  if (!isLocale(locale)) notFound();
  return (
    <Container as="main" className="flex-1 py-16">
      <h1 className="font-display text-4xl font-medium wrap-break-word text-neutral-950">
        {clinic.fullName}
      </h1>
      <p className="mt-4 text-lg">{getStrings(locale).underConstruction}</p>
    </Container>
  );
}
