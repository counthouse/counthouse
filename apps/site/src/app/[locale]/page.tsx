import { Container } from "@nicolassancho/ui";
import type { Metadata } from "next";
import { notFound } from "next/navigation";

import { HoursList } from "@/components/hours-list";
import { OpenStatus } from "@/components/open-status";
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
  const strings = getStrings(locale);
  return (
    <Container as="main" className="flex-1 py-16">
      <h1 className="font-display text-4xl font-medium wrap-break-word text-neutral-950">
        {clinic.fullName}
      </h1>
      <p className="mt-4 text-lg">{strings.underConstruction}</p>
      <div className="mt-6">
        <OpenStatus
          openLabel={strings.openNow}
          closedLabel={strings.closedNow}
        />
      </div>
      <h2 className="mt-8 font-display text-2xl font-medium text-neutral-950">
        {strings.hours}
      </h2>
      <div className="mt-3">
        <HoursList locale={locale} strings={strings} />
      </div>
      <p className="mt-5 text-neutral-600">{strings.emergencies}</p>
    </Container>
  );
}
