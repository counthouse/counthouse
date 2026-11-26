import type { Strings } from "./es";

export const pt: Strings = {
  description:
    "Clínica veterinária em Rivera, Uruguai. Fale conosco pelo WhatsApp, ligue ou veja como chegar.",
  whatsapp: "WhatsApp",
  whatsappMessage: "Olá Ánima, quero fazer uma consulta",
  call: (phoneLabel: string) => `Ligar para ${phoneLabel}`,
  directions: "Como chegar",
  skipToContent: "Pular para o conteúdo",
  heroTitle: "Veterinária em Rivera para gatos, cachorros e animais exóticos.",
  heroLead:
    "Priorizamos o atendimento personalizado: buscamos entender o que cada paciente precisa. Seguimos estudando todos os dias, com foco em medicina felina e animais não convencionais.",
  address: "Endereço",
  seeOnMap: "Ver no Google Maps",
  phone: "Telefone",
  phoneNote: "WhatsApp e ligações. Do Brasil, com o +598.",
  services: "Serviços",
  servicesLead:
    "Para gatos, cachorros e animais exóticos. As consultas são marcadas pelo WhatsApp.",
  exotics: "Animais exóticos",
  exoticsText:
    "Além de gatos e cachorros, atendemos animais não convencionais: aves, coelhos, roedores, répteis e mais. Se você não sabe se podemos atender o seu pet, pergunte.",
  exoticsAction: "Consultar sobre meu pet",
  exoticsMessage: "Olá Ánima, quero consultar sobre meu pet exótico",
  directionsSign: "Procure a placa verde com o logo.",
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
