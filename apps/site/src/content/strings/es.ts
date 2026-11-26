export const es = {
  description:
    "Clínica veterinaria en Rivera, Uruguay. Escribinos por WhatsApp, llamanos o mirá cómo llegar.",
  whatsapp: "WhatsApp",
  whatsappMessage: "Hola Ánima, quiero hacer una consulta",
  call: (phoneLabel: string) => `Llamar al ${phoneLabel}`,
  directions: "Cómo llegar",
  skipToContent: "Saltar al contenido",
  heroTitle: "Veterinaria en Rivera para gatos, perros y animales exóticos.",
  heroLead:
    "Priorizamos la atención personalizada: buscamos entender qué necesita cada paciente. Nos seguimos formando todos los días, con foco en medicina felina y animales no convencionales.",
  address: "Dirección",
  seeOnMap: "Ver en Google Maps",
  phone: "Teléfono",
  phoneNote: "WhatsApp y llamadas. Desde Brasil, con el +598.",
  services: "Servicios",
  servicesLead:
    "Para gatos, perros y animales exóticos. Los turnos se coordinan por WhatsApp.",
  exotics: "Animales exóticos",
  exoticsText:
    "Además de gatos y perros, atendemos animales no convencionales: aves, conejos, roedores, reptiles y más. Si no sabés si podemos atender a tu mascota, preguntanos.",
  exoticsAction: "Consultar por mi mascota",
  exoticsMessage: "Hola Ánima, quiero consultar por mi mascota exótica",
  directionsSign: "Buscá el cartel verde con el logo.",
  headerNav: "Principal",
  footerNav: "Pie de página",
  language: "Idioma",
  hours: "Horario",
  openNow: "Abierto ahora",
  closedNow: "Cerrado ahora",
  rangeTo: " a ",
  emergenciesOnly: "Solo urgencias",
  emergencies: "Urgencias hasta la medianoche, por WhatsApp o teléfono.",
  // Monday first. "Lunes a viernes", "Sábados".
  dayGroup: (from: number, to: number) => {
    const names = [
      "lunes",
      "martes",
      "miércoles",
      "jueves",
      "viernes",
      "sábado",
      "domingo",
    ];
    const name = (day: number) => names[day - 1] ?? "";
    const text =
      from === to
        ? name(from).endsWith("s")
          ? name(from)
          : `${name(from)}s`
        : `${name(from)} a ${name(to)}`;
    return text.charAt(0).toUpperCase() + text.slice(1);
  },
};

export type Strings = typeof es;
