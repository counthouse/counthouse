import { ButtonLink, Container, TextLink } from "@nicolassancho/ui";
import { MessageCircle } from "lucide-react";
import type { Metadata } from "next";
import { notFound } from "next/navigation";
import type { ReactNode } from "react";

import { HoursList } from "@/components/hours-list";
import { stackedLinkClass } from "@/components/link-styles";
import { OpenStatus } from "@/components/open-status";
import { clinic, telHref, whatsappHref } from "@/content/clinic";
import { services } from "@/content/services";
import { getStrings } from "@/content/strings";
import { alternates } from "@/i18n/alternates";
import { isLocale } from "@/i18n/locales";
import { shareMetadata } from "@/i18n/share";

export async function generateMetadata({
  params,
}: PageProps<"/[locale]">): Promise<Metadata> {
  const { locale } = await params;
  if (!isLocale(locale)) notFound();
  const strings = getStrings(locale);
  return {
    title: `${clinic.fullName} · ${strings.homeTitle}`,
    description: strings.description,
    alternates: alternates(locale, ""),
    ...shareMetadata(`/${locale}/`, locale),
  };
}

const h2Class =
  "font-display text-[clamp(1.5rem,1.25rem+1.25vw,2rem)] leading-tight font-medium text-balance text-neutral-950";
const actionClass = "w-full justify-center sm:w-auto";

// Heading on the left, content on the right from `md`.
function Section({
  title,
  intro,
  children,
}: {
  title: string;
  intro?: ReactNode;
  children: ReactNode;
}) {
  return (
    <Container
      as="section"
      className="grid grid-cols-1 gap-6 py-12 md:grid-cols-[4fr_8fr] md:gap-16 md:py-24"
    >
      <div className="flex flex-col gap-4">
        <h2 className={h2Class}>{title}</h2>
        {intro}
      </div>
      {children}
    </Container>
  );
}

export default async function HomePage({ params }: PageProps<"/[locale]">) {
  const { locale } = await params;
  if (!isLocale(locale)) notFound();
  const strings = getStrings(locale);
  return (
    <>
      <Container
        as="section"
        className="grid grid-cols-1 gap-10 pt-10 pb-12 md:grid-cols-[7fr_5fr] md:items-start md:gap-16 md:pt-18 md:pb-20"
      >
        <div className="flex flex-col gap-6">
          <h1
            // The skip link's target: a magnifier follows focus to it, which `main` cannot take.
            id="contenido"
            tabIndex={-1}
            // `hyphens-auto` breaks a word too long for a narrow screen with a hyphen, in the page's `lang`.
            className="max-w-[20ch] scroll-mt-8 font-display text-[clamp(2rem,1.5rem+2.5vw,3.25rem)] leading-[1.12] font-medium tracking-[-0.01em] wrap-break-word hyphens-auto text-balance text-neutral-950"
          >
            {strings.heroTitle}
          </h1>
          <p className="max-w-xl text-lg text-pretty text-neutral-700">
            {strings.heroLead}
          </p>
          <OpenStatus
            openLabel={strings.openNow}
            closedLabel={strings.closedNow}
          />
          <div className="flex flex-col gap-3 sm:flex-row sm:flex-wrap sm:items-center">
            <ButtonLink
              href={whatsappHref(clinic, strings.whatsappMessage)}
              variant="primary"
              size="large"
              className={actionClass}
            >
              <MessageCircle aria-hidden size={20} />
              {strings.whatsapp}
            </ButtonLink>
            <ButtonLink
              href={telHref(clinic)}
              variant="outline"
              size="large"
              className={actionClass}
            >
              {strings.call(clinic.phoneLabelLocal)}
            </ButtonLink>
          </div>
        </div>

        <dl className="flex flex-col gap-8 rounded-card bg-neutral-50 p-6">
          <div className="flex flex-col gap-1">
            <dt className="text-sm font-semibold text-neutral-600">
              {strings.address}
            </dt>
            <dd className="flex flex-col items-start gap-1 text-neutral-950">
              {clinic.address}
              <TextLink href={clinic.mapUrl} className={stackedLinkClass}>
                {strings.seeOnMap}
              </TextLink>
            </dd>
          </div>
          <div className="flex flex-col gap-2">
            <dt className="text-sm font-semibold text-neutral-600">
              {strings.hours}
            </dt>
            <dd className="text-neutral-950">
              <HoursList locale={locale} strings={strings} />
              <p className="mt-4 text-neutral-600">{strings.emergencies}</p>
            </dd>
          </div>
          <div className="flex flex-col gap-1">
            <dt className="text-sm font-semibold text-neutral-600">
              {strings.phone}
            </dt>
            <dd className="flex flex-col items-start gap-1 text-neutral-950">
              {/* Text, not a link: the call button beside it already dials it. */}
              <span>{clinic.phoneLabel}</span>
              <span className="text-neutral-600">{strings.phoneNote}</span>
            </dd>
          </div>
        </dl>
      </Container>

      <Section
        title={strings.services}
        intro={<p className="text-neutral-600">{strings.servicesLead}</p>}
      >
        {/* Safari drops the list role of a styled list without it. */}
        <ul
          role="list"
          className="grid grid-cols-1 gap-x-10 gap-y-8 sm:grid-cols-2"
        >
          {services.map(({ name, summary }) => (
            <li key={name.es} className="flex flex-col gap-1">
              <h3 className="font-display text-xl leading-snug font-semibold text-neutral-950">
                {name[locale]}
              </h3>
              <p className="text-pretty text-neutral-700">{summary[locale]}</p>
            </li>
          ))}
        </ul>
      </Section>

      <Section title={strings.exotics}>
        <div className="flex flex-col items-start gap-6">
          <p className="max-w-prose text-pretty">{strings.exoticsText}</p>
          <ButtonLink
            href={whatsappHref(clinic, strings.exoticsMessage)}
            variant="outline"
            size="large"
            className={actionClass}
          >
            {strings.exoticsAction}
          </ButtonLink>
        </div>
      </Section>

      <Section title={strings.directions}>
        <div className="flex flex-col items-start gap-2">
          <p className="max-w-prose">
            {clinic.address}. {strings.directionsSign}
          </p>
          <TextLink href={clinic.mapUrl} className={stackedLinkClass}>
            {strings.seeOnMap}
          </TextLink>
        </div>
      </Section>
    </>
  );
}
