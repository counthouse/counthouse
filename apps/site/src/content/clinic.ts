/**
 * Everything specific to the business this site is built for. Components take these values as
 * props and hold no business text. The business approved publishing these details.
 */
export interface Clinic {
  /** Short name, shown next to the logo. */
  name: string;
  /** Full name, for the page title and the legal line. */
  fullName: string;
  /** Each file is twice the size it is shown at, for high-density screens. */
  logo: Record<
    "header" | "large",
    { src: string; width: number; height: number }
  >;
  /** International format with the country code, digits only, as `wa.me` and `tel:` expect. */
  phone: string;
  /** How the phone number is written for people. */
  phoneLabel: string;
  address: string;
  mapUrl: string;
  /** For canonical links. `.invalid` never resolves (RFC 2606) until the deploy sets the domain. */
  siteUrl: string;
}

export const clinic: Clinic = {
  name: "Ánima",
  fullName: "Ánima Medicina Veterinaria",
  logo: {
    header: { src: "/logo-88.webp", width: 88, height: 88 },
    large: { src: "/logo-176.webp", width: 176, height: 176 },
  },
  phone: "59898657295",
  phoneLabel: "+598 98 657 295",
  address: "Av. Sarandí 711, sala 2, Rivera, Uruguay",
  mapUrl: "https://maps.app.goo.gl/dD1HNzFkP1Jk3vMM8",
  siteUrl: "https://anima.invalid",
};

export const whatsappHref = ({ phone }: Clinic, message: string) =>
  `https://wa.me/${phone}?text=${encodeURIComponent(message)}`;

export const telHref = ({ phone }: Clinic) => `tel:+${phone}`;
