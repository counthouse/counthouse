// Serves `out/` on 127.0.0.1 for the tests: `/es/` answers `es/index.html`, a missing file answers
// `404.html` with status 404. HTML is sent without a charset, so Lighthouse checks the page's own
// `<meta charset>`.
import { createReadStream, statSync } from "node:fs";
import { createServer, type Server } from "node:http";
import type { AddressInfo } from "node:net";
import { extname, join, normalize } from "node:path";

import { out } from "./built-pages.mts";

const types: Record<string, string> = {
  ".html": "text/html",
  ".css": "text/css; charset=utf-8",
  ".js": "text/javascript; charset=utf-8",
  ".txt": "text/plain; charset=utf-8",
  ".json": "application/json",
  ".xml": "application/xml",
  ".webp": "image/webp",
  ".png": "image/png",
  ".ico": "image/x-icon",
  ".svg": "image/svg+xml",
  ".woff2": "font/woff2",
};

function fileFor(pathname: string): string | undefined {
  // `normalize` resolves `..` before the join, so a request cannot leave `out/`.
  const path = join(out, normalize(decodeURIComponent(pathname)));
  for (const candidate of [path, join(path, "index.html")]) {
    try {
      if (statSync(candidate).isFile()) return candidate;
    } catch {
      // Not there: try the next candidate.
    }
  }
}

/** Port 0 picks a free port. Fails when the port is taken, so a test never reads another server. */
export function startServer(
  port: number,
): Promise<{ server: Server; origin: string }> {
  const server = createServer((request, response) => {
    const { pathname } = new URL(request.url ?? "/", "http://localhost");
    let file: string | undefined;
    try {
      file = fileFor(pathname);
    } catch {
      // Not valid percent-encoding: `decodeURIComponent` throws.
      response.writeHead(400).end();
      return;
    }
    const served = file ?? join(out, "404.html");
    response.writeHead(file ? 200 : 404, {
      "content-type": types[extname(served)] ?? "application/octet-stream",
    });
    createReadStream(served).pipe(response);
  });
  return new Promise((resolve, reject) => {
    server.once("error", reject);
    server.listen(port, "127.0.0.1", () => {
      const { port: bound } = server.address() as AddressInfo;
      resolve({ server, origin: `http://127.0.0.1:${bound}` });
    });
  });
}

if (import.meta.main) {
  const { origin } = await startServer(Number(process.env.PORT ?? 4719));
  console.log(`Serving ${out} on ${origin}`);
}
