import { Footer, TextLink } from "@nicolassancho/ui";

import { type Clinic, telHref, whatsappHref } from "@/content/clinic";
import { strings } from "@/content/strings";

// The site is a static export, so this is the year of the build.
const buildYear = new Date().getFullYear();

/** The page footer: the business's contact details and the legal line. */
export function SiteFooter({ clinic }: { clinic: Clinic }) {
  return (
    <Footer
      // Room for the fixed WhatsApp link on phones, so it never covers the legal line.
      className="pb-20 md:pb-0"
      brand={
        <address className="flex flex-col gap-2 not-italic">
          <p className="font-display text-xl font-medium text-neutral-950">
            {clinic.fullName}
          </p>
          <p>{clinic.address}</p>
          <ul className="flex flex-col gap-1">
            {[
              { label: strings.whatsapp, href: whatsappHref(clinic) },
              { label: strings.call(clinic.phoneLabel), href: telHref(clinic) },
              { label: strings.directions, href: clinic.mapUrl },
            ].map(({ label, href }) => (
              <li key={href}>
                {/* Stacked links, not links in a sentence: 44px tall on touch screens (WCAG 2.5.5). */}
                <TextLink
                  href={href}
                  className="inline-flex items-center py-1 text-primary pointer-coarse:min-h-11"
                >
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
