import { Footer, Text, TextLink } from "@nicolassancho/ui";

import { LanguageLinks } from "@/components/language-links";
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
    <Footer
      navLabel={strings.footerNav}
      // Room for the fixed WhatsApp link on phones, so it never covers the legal line.
      className="pb-20 md:pb-0"
      brand={
        <div className="flex flex-col gap-8">
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
          {/* Library columns cannot set `lang` or `aria-current`. */}
          <section aria-labelledby="footer-language">
            <Text
              as="h2"
              id="footer-language"
              size="small"
              weight="semibold"
              color="darker"
            >
              {strings.language}
            </Text>
            <LanguageLinks current={locale} show="all" className="mt-2" />
          </section>
        </div>
      }
      legal={`© ${buildYear} ${clinic.fullName}`}
    />
  );
}
