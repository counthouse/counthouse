import type { Locale } from "../i18n/locales.ts";

// One entry per service; a missing language fails the type check.
export const services: readonly {
  name: Record<Locale, string>;
  summary: Record<Locale, string>;
}[] = [
  {
    name: { es: "Consultas", pt: "Consultas" },
    summary: {
      es: "Medicina general para gatos, perros y animales exóticos.",
      pt: "Clínica geral para gatos, cachorros e animais exóticos.",
    },
  },
  {
    name: { es: "Cirugías", pt: "Cirurgias" },
    summary: {
      es: "Intervenciones con control antes y después de la operación.",
      pt: "Intervenções com acompanhamento antes e depois da operação.",
    },
  },
  {
    name: { es: "Internación", pt: "Internação" },
    summary: {
      es: "Cuidado y monitoreo de pacientes internados.",
      pt: "Cuidado e monitoramento de pacientes internados.",
    },
  },
  {
    name: { es: "Hotel para mascotas", pt: "Hotel para pets" },
    summary: {
      es: "Tu mascota queda cuidada mientras viajás.",
      pt: "Seu pet fica bem cuidado enquanto você viaja.",
    },
  },
  {
    name: { es: "Peluquería", pt: "Banho e tosa" },
    summary: { es: "Baño y corte.", pt: "Banho e corte de pelo." },
  },
  {
    name: { es: "Tienda", pt: "Loja" },
    summary: {
      es: "Alimento y accesorios. Preguntanos si tenemos lo que buscás.",
      pt: "Ração e acessórios. Pergunte se temos o que você procura.",
    },
  },
];
