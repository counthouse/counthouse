import { readFile } from "node:fs/promises";
import { join } from "node:path";

import { ImageResponse } from "next/og";

import { brand } from "@/brand";
import { clinic } from "@/content/clinic";
import { shareImage } from "@/lib/share-image";

// The link preview image for every page, written to `out/og.png` at build time. A route named
// `og.png` keeps the extension, which static hosts use to send `Content-Type: image/png`.
export const dynamic = "force-static";

// `ImageResponse` reads only static TTF, OTF or WOFF fonts and skips WebP images without an error.
const asset = (path: string) => readFile(join(process.cwd(), path));

export async function GET() {
  const [font, logo] = await Promise.all([
    asset("assets/fonts/EBGaramond-Medium.ttf"),
    asset("public/logo-512.png"),
  ]);
  return new ImageResponse(
    <div
      style={{
        display: "flex",
        flexDirection: "column",
        alignItems: "center",
        justifyContent: "center",
        gap: 32,
        width: "100%",
        height: "100%",
        background: brand["color-surface"][0],
        color: brand["color-neutral-950"][0],
        fontFamily: "EB Garamond",
      }}
    >
      {/* eslint-disable-next-line @next/next/no-img-element -- ImageResponse renders only plain <img> */}
      <img
        src={`data:image/png;base64,${logo.toString("base64")}`}
        width={176}
        height={176}
        alt=""
      />
      <div style={{ fontSize: 72 }}>{clinic.fullName}</div>
      <div style={{ fontSize: 40, color: brand["color-primary"][0] }}>
        {`${clinic.postalAddress.addressLocality}, Uruguay`}
      </div>
    </div>,
    {
      width: shareImage.width,
      height: shareImage.height,
      fonts: [
        { name: "EB Garamond", data: font, weight: 500, style: "normal" },
      ],
    },
  );
}
