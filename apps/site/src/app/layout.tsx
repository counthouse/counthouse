import "./globals.css";

import { brandToCssVariables, validateBrand } from "@nicolassancho/ui";
import type { Metadata } from "next";
import type { CSSProperties } from "react";

import { brand } from "@/brand";

// Runs when the layout module loads: a brand that `validateBrand` rejects stops `next build`.
const failures = validateBrand(brand);
if (failures.length > 0) {
  throw new Error(
    `The brand fails the design system's checks: ${JSON.stringify(failures, null, 2)}`,
  );
}
// React types `style` without custom properties.
const brandStyle = brandToCssVariables(brand) as CSSProperties;

export const metadata: Metadata = {
  title: "Clínica veterinaria",
};

export default function RootLayout({ children }: LayoutProps<"/">) {
  return (
    <html lang="es" style={brandStyle}>
      <body className="bg-surface font-sans text-neutral-900 antialiased">
        {children}
      </body>
    </html>
  );
}
