# Instructions for coding agents

Read [CONTRIBUTING.md](CONTRIBUTING.md) and, once they exist, the decision records in `docs/adr/` before changing
anything.

## Layout

Planned; each folder is created when its work starts.

- `apps/api`: Spring Boot API, the only owner of the database.
- `apps/backoffice`: Next.js App Router backoffice for staff.
- `apps/site`: Next.js static site of the first tenant.
- `contract/openapi.yaml`: the API contract; code on both sides is generated from it.
- `docs/`: decision records, architecture and operations.
- `infra/`: deployment configuration.

## Build and test

- `apps/api`: `cd apps/api && ./mvnw -B verify`. Docker must be running: the tests start PostgreSQL 18 through
  Testcontainers.

## Working rules

- Every rule is enforced by a test or a CI check. Run them before proposing a change.
- Keep each change to one concern, as described in CONTRIBUTING.md.
