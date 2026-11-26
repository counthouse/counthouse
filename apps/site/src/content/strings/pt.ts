import type { Strings } from "./es";

export const pt: Strings = {
  description:
    "Clínica veterinária em Rivera, Uruguai. Fale conosco pelo WhatsApp, ligue ou veja como chegar.",
  whatsapp: "WhatsApp",
  whatsappMessage: "Olá Ánima, quero fazer uma consulta",
  call: (phoneLabel: string) => `Ligar para ${phoneLabel}`,
  directions: "Como chegar",
  underConstruction: "Site em construção.",
  headerNav: "Principal",
  footerNav: "Rodapé",
  language: "Idioma",
  hours: "Horário",
  openNow: "Aberto agora",
  closedNow: "Fechado agora",
  rangeTo: " às ",
  emergenciesOnly: "Só urgências",
  emergencies: "Urgências até a meia-noite, pelo WhatsApp ou telefone.",
  // Monday first. "Segunda a sexta", "Sábados".
  dayGroup: (from: number, to: number) => {
    const names = [
      "segunda",
      "terça",
      "quarta",
      "quinta",
      "sexta",
      "sábado",
      "domingo",
    ];
    const name = (day: number) => names[day - 1] ?? "";
    const text = from === to ? `${name(from)}s` : `${name(from)} a ${name(to)}`;
    return text.charAt(0).toUpperCase() + text.slice(1);
  },
};
