import type { NextConfig } from "next";

// A static export: `next build` writes plain files to `out/`, served by
// Cloudflare Workers with no server. Headers, redirects and the proxy are not
// supported in this mode, so the deploy sets them.
const nextConfig: NextConfig = {
  output: "export",
  // Each page becomes `<path>/index.html`.
  trailingSlash: true,
  // The default image loader needs a server.
  images: { unoptimized: true },
  typedRoutes: true,
  // `next dev` would otherwise write an AGENTS.md for coding agents into this
  // folder; the repository root has its own.
  agentRules: false,
  experimental: {
    // One root layout per language leaves the 404 page without one: `app/global-not-found.tsx`.
    globalNotFound: true,
  },
};

export default nextConfig;
