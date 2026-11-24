import { Container, TextLink } from "@nicolassancho/ui";
import type { Metadata } from "next";

import { Document } from "@/components/document";
import { NavLink } from "@/components/nav-link";
import { clinic } from "@/content/clinic";
import { es } from "@/content/strings/es";
import { pt } from "@/content/strings/pt";
import { htmlLang, locales } from "@/i18n/locales";

export const metadata: Metadata = {
  title: `404 · ${clinic.fullName}`,
  // Both languages, like the page.
  description: `${es.description} ${pt.description}`,
};

const message = {
  es: { heading: "Página no encontrada", home: "Ir al inicio" },
  pt: { heading: "Página não encontrada", home: "Ir para o início" },
} as const;

// Skips every layout, so it renders `Document` itself. The URL does not say the language: both.
export default function GlobalNotFound() {
  return (
    <Document lang={htmlLang.es}>
      <Container
        as="main"
        className="flex flex-1 flex-col justify-center gap-8 py-16"
      >
        {locales.map((locale) => (
          <section
            key={locale}
            lang={htmlLang[locale]}
            className="flex flex-col gap-2"
          >
            {locale === "es" ? (
              <h1 className="font-display text-4xl font-medium text-neutral-950">
                {message[locale].heading}
              </h1>
            ) : (
              <p className="font-display text-4xl font-medium text-neutral-950">
                {message[locale].heading}
              </p>
            )}
            <p>
              <TextLink
                href={`/${locale}`}
                LinkComponent={NavLink}
                className="text-primary"
              >
                {message[locale].home}
              </TextLink>
            </p>
          </section>
        ))}
        <p className="text-sm text-neutral-600">{clinic.fullName}</p>
      </Container>
    </Document>
  );
}
