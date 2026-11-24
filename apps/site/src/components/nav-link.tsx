import Link from "next/link";
import type { ComponentProps } from "react";

type Href = ComponentProps<typeof Link>["href"];

// Typed routes reject the library's string hrefs. Write hrefs without the trailing slash:
// `next/link` adds it.
export const NavLink = ({
  href,
  ...props
}: Omit<ComponentProps<typeof Link>, "href"> & { href: string }) => (
  <Link href={href as Href} {...props} />
);
