import { clinic } from "../content/clinic.ts";

/** `path` on the site's own origin, for links read outside the site: crawlers, previews, structured data. */
export const absoluteUrl = (path: string) => new URL(path, clinic.siteUrl).href;
