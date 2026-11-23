import type { Brand } from "@nicolassancho/ui";

import { atkinson, garamond } from "@/fonts";

/**
 * The business's brand, in the design system's brand contract: the same `Brand` type the product
 * uses for tenant branding. The root layout checks it with `validateBrand`, so a value that breaks a
 * contrast pairing fails the build. Colors are `[light, dark]`.
 */
export const brand = {
  "color-primary": ["#1e7a46", "#5fcb8a"],
  "color-primary-darker": ["#155c34", "#9be0b5"],
  "color-secondary": ["#0c6b5e", "#5bc4b2"],
  "color-secondary-darker": ["#08544a", "#8ad8ca"],
  "color-surface": ["#fbfaf6", "#121915"],
  "color-neutral-25": ["#fdfcf9", "#141b17"],
  "color-neutral-50": ["#f6f0e1", "#1a211d"],
  "color-neutral-100": ["#e8ece9", "#222b26"],
  "color-neutral-200": ["#d3d9d5", "#303a34"],
  "color-neutral-300": ["#b3bbb6", "#45504a"],
  "color-neutral-400": ["#7f8983", "#6e7a73"],
  "color-neutral-500": ["#68726c", "#87928b"],
  "color-neutral-600": ["#5c6862", "#9aa69f"],
  "color-neutral-700": ["#4a554f", "#b2bdb6"],
  "color-neutral-800": ["#3a4540", "#c7d0ca"],
  "color-neutral-900": ["#22302a", "#dce3de"],
  "color-neutral-950": ["#101a14", "#eef2ef"],
  "radius-button": "0.375rem",
  "radius-field": "0.375rem",
  "radius-card": "0.5rem",
  "radius-sm": "0.375rem",
  "radius-lg": "0.5rem",
  "font-sans": `${atkinson.style.fontFamily}, ui-sans-serif, system-ui, sans-serif`,
  "font-display": `${garamond.style.fontFamily}, Garamond, Georgia, serif`,
} satisfies Brand;
