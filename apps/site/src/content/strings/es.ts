export const es = {
  description:
    "Clínica veterinaria en Rivera, Uruguay. Escribinos por WhatsApp, llamanos o mirá cómo llegar.",
  whatsapp: "WhatsApp",
  whatsappMessage: "Hola Ánima, quiero hacer una consulta",
  call: (phoneLabel: string) => `Llamar al ${phoneLabel}`,
  directions: "Cómo llegar",
  underConstruction: "Sitio en construcción.",
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
