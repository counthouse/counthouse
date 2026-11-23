import { Atkinson_Hyperlegible_Next, EB_Garamond } from "next/font/google";

// Downloaded at build time and served with the site. Variable fonts: one file per family and
// subset covers every weight. The brand's font tokens use `style.fontFamily`, which names the
// family and the fallback face `next/font` sizes to match it, so text does not shift on load.
export const atkinson = Atkinson_Hyperlegible_Next({
  subsets: ["latin"],
  // `next/font` has no metrics for this family, so it cannot size a fallback face. Text can
  // shift slightly when the font loads. Turbopack (Next 16.4.6) still prints a warning about the
  // missing metrics; webpack honors this option and does not.
  adjustFontFallback: false,
});
export const garamond = EB_Garamond({ subsets: ["latin"] });
