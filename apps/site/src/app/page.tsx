import { Container } from "@nicolassancho/ui";

import { clinic } from "@/content/clinic";
import { strings } from "@/content/strings";

// Placeholder until the pages are built.
export default function HomePage() {
  return (
    <Container as="main" className="flex-1 py-16">
      <h1 className="font-display text-4xl font-medium wrap-break-word text-neutral-950">
        {clinic.fullName}
      </h1>
      <p className="mt-4 text-lg">{strings.underConstruction}</p>
    </Container>
  );
}
