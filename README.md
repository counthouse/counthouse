# Counthouse

A multi-tenant back office for appointment-based businesses, built vertical-first: veterinary clinics are the first
vertical. One monorepo with a Spring Boot API, a Next.js backoffice and one OpenAPI contract that both sides are
generated from, plus the static website of the first business that uses it.

The core is domain-neutral: tenants, users, providers, services, schedule, appointments and customers. Features for
one kind of business live in their own module and are built only for a real customer. The API's modules and their
dependencies are drawn in [apps/api/docs/modules.md](apps/api/docs/modules.md), generated from the code.

## Status

In development and not deployed yet. Progress is tracked in GitHub milestones and issues.

## What it is not

- Not a SaaS. The code is tenant-aware, and production runs one tenant.
- Not a website builder. `apps/site` is built for the first business and is not part of the product: everything
  about that business is in its content and brand files.
- Not open source. The code is published for review; see [LICENSE](LICENSE).

## How it is built

Written with AI coding agents under the author's direction and review. Design decisions, specs and reviews are the
author's.

## Contributing

Conventions are in [CONTRIBUTING.md](CONTRIBUTING.md).
