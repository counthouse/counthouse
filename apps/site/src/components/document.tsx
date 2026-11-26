import "@/app/globals.css";

import { brandToCssVariables, validateBrand } from "@nicolassancho/ui";
import type { ReactNode } from "react";

import { brand } from "@/brand";

// Runs when the module loads: a brand that `validateBrand` rejects stops `next build`.
const failures = validateBrand(brand);
if (failures.length > 0) {
  throw new Error(
    `The brand fails the design system's checks: ${JSON.stringify(failures, null, 2)}`,
  );
}
// A `<style>` element, not the `style` of `<html>`: that attribute comes before `<meta charset>`
// and pushed it past the first 1024 bytes, where browsers look for it. Unlayered, so it wins over
// the defaults in Tailwind's `theme` layer.
const brandCss = `:root{${Object.entries(brandToCssVariables(brand))
  .map(([name, value]) => `${name}:${value}`)
  .join(";")}}`;

/** Shared by every root layout. */
export function Document({
  lang,
  children,
}: {
  lang: string;
  children: ReactNode;
}) {
  return (
    // Keeps focus scrolls clear of the WhatsApp link, fixed below `md` (site-header.tsx); WCAG 2.4.11.
    <html lang={lang} className="max-md:scroll-pb-22">
      <body className="flex min-h-dvh flex-col bg-surface font-sans text-neutral-900 antialiased">
        {/* `href` and `precedence` make React move it into `<head>`. */}
        <style href="brand" precedence="brand">
          {brandCss}
        </style>
        {children}
      </body>
    </html>
  );
}
