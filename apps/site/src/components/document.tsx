import "@/app/globals.css";

import { brandToCssVariables, validateBrand } from "@nicolassancho/ui";
import type { CSSProperties, ReactNode } from "react";

import { brand } from "@/brand";

// Runs when the module loads: a brand that `validateBrand` rejects stops `next build`.
const failures = validateBrand(brand);
if (failures.length > 0) {
  throw new Error(
    `The brand fails the design system's checks: ${JSON.stringify(failures, null, 2)}`,
  );
}
// React types `style` without custom properties.
const brandStyle = brandToCssVariables(brand) as CSSProperties;

/** Shared by every root layout. */
export function Document({
  lang,
  children,
}: {
  lang: string;
  children: ReactNode;
}) {
  return (
    <html lang={lang} style={brandStyle}>
      <body className="flex min-h-dvh flex-col bg-surface font-sans text-neutral-900 antialiased">
        {children}
      </body>
    </html>
  );
}
