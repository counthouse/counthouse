import { ButtonLink, Header } from "@nicolassancho/ui";
import { MessageCircle } from "lucide-react";
import Image from "next/image";
import Link from "next/link";

import { type Clinic, whatsappHref } from "@/content/clinic";
import { strings } from "@/content/strings";

/**
 * The page header: the business's logo and name linking home, and the WhatsApp link. On phones
 * the link is fixed to the bottom right of the screen, where it stays in reach while scrolling;
 * from `md` up it sits in the header. It is one element either way, so it stays inside the
 * header landmark and second in tab order.
 */
export function SiteHeader({ clinic }: { clinic: Clinic }) {
  return (
    <Header
      className="border-b border-neutral-200 shadow-none"
      brand={
        <Link
          href="/"
          // The design system's focus ring, which it does not export.
          className="flex items-center gap-3 rounded-sm text-neutral-950 focus:outline-hidden focus-visible:ring-2 focus-visible:ring-primary focus-visible:ring-offset-2 focus-visible:ring-offset-surface"
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
        <ButtonLink
          href={whatsappHref(clinic)}
          variant="primary"
          size="large"
          // The transparent border is drawn in forced-colors mode, where the background is not.
          // With large text on a narrow phone the label wraps under the icon instead of being clipped.
          className="fixed right-4 bottom-4 z-20 max-w-[calc(100vw-2rem)] flex-wrap justify-center border border-transparent px-4 shadow-lg md:static md:max-w-none md:px-6 md:shadow-none"
        >
          <MessageCircle aria-hidden size={20} />
          {strings.whatsapp}
        </ButtonLink>
      }
    />
  );
}
