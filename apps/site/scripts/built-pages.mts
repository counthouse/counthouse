// The pages `next build` wrote to `out/`, as URL paths such as `/es/`.
import { existsSync, readdirSync } from "node:fs";
import { join, relative } from "node:path";

export const out = join(import.meta.dirname, "..", "out");

/** Every `index.html` page, without Next's copies of the 404 page (`404.html` is the one served). */
export function builtPages(): string[] {
  if (!existsSync(out)) return [];
  return readdirSync(out, { recursive: true, withFileTypes: true })
    .filter((entry) => entry.isFile() && entry.name === "index.html")
    .map(
      (entry) =>
        `/${relative(out, join(entry.parentPath, entry.name)).replace(/index\.html$/, "")}`,
    )
    .filter((path) => path !== "/404/" && path !== "/_not-found/")
    .sort();
}
