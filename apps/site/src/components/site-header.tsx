import { ButtonLink, Header, focusRing } from "@nicolassancho/ui";
import { MessageCircle } from "lucide-react";
import Image from "next/image";
import Link from "next/link";

import { LanguageLinks } from "@/components/language-links";
import { type Clinic, whatsappHref } from "@/content/clinic";
import type { Strings } from "@/content/strings";
import type { Locale } from "@/i18n/locales";

/**
 * On phones the WhatsApp link is fixed to the bottom right, in reach while scrolling; from `md` up
 * it sits in the header. One element either way, so it stays in the header landmark and tab order.
 */
export function SiteHeader({
  clinic,
  locale,
  strings,
}: {
  clinic: Clinic;
  locale: Locale;
  strings: Strings;
}) {
  return (
    <Header
      className="border-b border-neutral-200 shadow-none"
      navLabel={strings.headerNav}
      brand={
        <Link
          href={`/${locale}`}
          className={`flex items-center gap-3 rounded-sm text-neutral-950 ${focusRing}`}
        >
          {/* Decorative: the name next to it is the link's text. */}
          <Image
            src={clinic.logo.src}
            width={clinic.logo.width}
            height={clinic.logo.height}
            alt=""
            // Above the fold: `next/image` lazy-loads by default.
            loading="eager"
            // Pixels, not rem: the mark does not grow with the text size, which leaves the name
            // room on a 320px screen with text at 200%.
            className="size-[44px] rounded-full"
          />
          <span className="font-display text-xl font-medium sm:text-2xl tracking-[0.06em] uppercase">
            {clinic.name}
          </span>
        </Link>
      }
      actions={
        <>
          <LanguageLinks current={locale} show="others" />
          <ButtonLink
            href={whatsappHref(clinic, strings.whatsappMessage)}
            variant="primary"
            size="large"
            // With large text on a narrow phone the label wraps under the icon instead of being clipped.
            className="fixed right-4 bottom-4 z-20 max-w-[calc(100vw-2rem)] flex-wrap justify-center px-4 shadow-lg md:static md:max-w-none md:px-6 md:shadow-none"
          >
            <MessageCircle aria-hidden size={20} />
            {strings.whatsapp}
          </ButtonLink>
        </>
      }
    />
  );
}
