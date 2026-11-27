import assert from "node:assert/strict";
import { test } from "node:test";

import { clinic } from "../content/clinic.ts";
import { businessData } from "./business-data.ts";

// Literal values: `check:search` compares the built pages with `businessData()` itself, so only this
// test sees a wrong field.
test("describes the clinic as a local veterinary business", () => {
  const { openingHoursSpecification, ...rest } = businessData("/pt/");
  assert.deepEqual(rest, {
    "@context": "https://schema.org",
    "@type": ["VeterinaryCare", "LocalBusiness"],
    name: "Ánima Medicina Veterinaria",
    url: `${clinic.siteUrl}/pt/`,
    logo: `${clinic.siteUrl}/logo-512.png`,
    image: `${clinic.siteUrl}/og.png`,
    telephone: "+598 98 657 295",
    hasMap: "https://maps.app.goo.gl/dD1HNzFkP1Jk3vMM8",
    address: {
      "@type": "PostalAddress",
      streetAddress: "Av. Sarandí 711, sala 2",
      addressLocality: "Rivera",
      addressCountry: "UY",
    },
  });
  assert.equal(openingHoursSpecification.length, 4);
});
