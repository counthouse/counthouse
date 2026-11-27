import { businessData } from "@/lib/business-data";

// `<` escaped so no value can close the script element (Next's JSON-LD guide).
export function BusinessData({ homePath }: { homePath: string }) {
  return (
    <script
      type="application/ld+json"
      dangerouslySetInnerHTML={{
        __html: JSON.stringify(businessData(homePath)).replace(/</g, "\\u003c"),
      }}
    />
  );
}
