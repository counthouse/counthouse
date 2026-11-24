import { ButtonLink, Container, TextLink } from "@nicolassancho/ui";
import Image from "next/image";

import { stackedLinkClass } from "@/components/link-styles";
import { NavLink } from "@/components/nav-link";
import { clinic, telHref, whatsappHref } from "@/content/clinic";
import { es } from "@/content/strings/es";
import { pt } from "@/content/strings/pt";
import { htmlLang, languageName, locales } from "@/i18n/locales";

// No redirect: a static export cannot, and Google advises against guessing the language.
export default function SelectorPage() {
  return (
    <Container
      as="main"
      className="flex flex-1 flex-col items-center justify-center gap-10 py-16 text-center"
    >
      <div className="flex flex-col items-center gap-4">
        <Image
          src={clinic.logo.src}
          width={clinic.logo.width}
          height={clinic.logo.height}
          alt=""
          loading="eager"
          className="size-[88px] rounded-full"
        />
        {/* `max-w-full`: a centred flex item is otherwise as wide as its longest word. */}
        <h1 className="max-w-full font-display text-3xl font-medium tracking-[0.06em] wrap-break-word hyphens-auto text-balance text-neutral-950 uppercase sm:text-4xl">
          {clinic.fullName}
        </h1>
      </div>
      <ul className="flex w-full flex-col gap-4 sm:w-auto sm:flex-row">
        {locales.map((locale) => (
          <li key={locale}>
            <ButtonLink
              href={`/${locale}`}
              LinkComponent={NavLink}
              lang={htmlLang[locale]}
              hrefLang={htmlLang[locale]}
              variant="primary"
              size="large"
              className="w-full justify-center border border-transparent sm:w-48"
            >
              {languageName[locale]}
            </ButtonLink>
          </li>
        ))}
      </ul>
      <address className="flex flex-col items-center gap-1 not-italic">
        <p>{clinic.address}</p>
        <ul className="flex flex-col items-center gap-1">
          <li>
            <TextLink
              href={whatsappHref(clinic, es.whatsappMessage)}
              className={stackedLinkClass}
            >
              {es.whatsapp}
            </TextLink>
          </li>
          <li>
            <TextLink href={telHref(clinic)} className={stackedLinkClass}>
              {clinic.phoneLabel}
            </TextLink>
          </li>
          <li>
            <TextLink href={clinic.mapUrl} className={stackedLinkClass}>
              {/* One child: inline-flex drops the space before a second one. */}
              <span>
                {es.directions} ·{" "}
                <span lang={htmlLang.pt}>{pt.directions}</span>
              </span>
            </TextLink>
          </li>
        </ul>
      </address>
    </Container>
  );
}
