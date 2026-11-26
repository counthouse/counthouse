import { TextLink } from "@nicolassancho/ui";

import { FooterWithLanguages } from "@/components/footer-with-languages";
import { stackedLinkClass } from "@/components/link-styles";
import { type Clinic, telHref, whatsappHref } from "@/content/clinic";
import type { Strings } from "@/content/strings";
import type { Locale } from "@/i18n/locales";

// The site is a static export, so this is the year of the build.
const buildYear = new Date().getFullYear();

export function SiteFooter({
  clinic,
  locale,
  strings,
}: {
  clinic: Clinic;
  locale: Locale;
  strings: Strings;
}) {
  return (
    <FooterWithLanguages
      locale={locale}
      navLabel={strings.footerNav}
      languageTitle={strings.language}
      // Room for the fixed WhatsApp link on phones, so it never covers the legal line.
      className="pb-12 md:pb-0"
      brand={
        <address className="flex flex-col gap-2 not-italic">
          <p className="font-display text-xl font-medium text-neutral-950">
            {clinic.fullName}
          </p>
          <p>{clinic.address}</p>
          <ul className="flex flex-col gap-2">
            {[
              {
                label: strings.whatsapp,
                href: whatsappHref(clinic, strings.whatsappMessage),
              },
              {
                label: strings.call(clinic.phoneLabel),
                href: telHref(clinic),
              },
              { label: strings.directions, href: clinic.mapUrl },
            ].map(({ label, href }) => (
              <li key={href}>
                <TextLink href={href} className={stackedLinkClass}>
                  {label}
                </TextLink>
              </li>
            ))}
          </ul>
        </address>
      }
      legal={`© ${buildYear} ${clinic.fullName}`}
    />
  );
}
