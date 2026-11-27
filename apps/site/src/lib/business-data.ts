import { clinic } from "../content/clinic.ts";
import { hours } from "../content/hours.ts";
import { openingHoursSpecification } from "./opening-hours-spec.ts";
import { shareImage } from "./share-image.ts";
import { absoluteUrl } from "./site-url.ts";

/**
 * The clinic as schema.org structured data. `VeterinaryCare` is not a `LocalBusiness` in schema.org,
 * and Google's local business results read `LocalBusiness` types, so the node has both.
 */
export function businessData(homePath: string) {
  return {
    "@context": "https://schema.org",
    "@type": ["VeterinaryCare", "LocalBusiness"],
    name: clinic.fullName,
    url: absoluteUrl(homePath),
    logo: absoluteUrl("/logo-512.png"),
    image: absoluteUrl(shareImage.url),
    telephone: clinic.phoneLabel,
    hasMap: clinic.mapUrl,
    address: { "@type": "PostalAddress", ...clinic.postalAddress },
    openingHoursSpecification: openingHoursSpecification(hours),
  };
}
