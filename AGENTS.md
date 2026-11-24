# Instructions for coding agents

Read [CONTRIBUTING.md](CONTRIBUTING.md) and, once they exist, the decision records in `docs/adr/` before changing
anything.

## Layout

Built so far: `apps/api`, `apps/site` and `contract`. The other folders are created when their work starts.

- `apps/api`: Spring Boot API, the only owner of the database.
- `apps/backoffice`: Next.js App Router backoffice for staff.
- `apps/site`: Next.js static site of the first business that uses Counthouse; not part of the product.
- `contract/openapi.yaml`: the API contract; code on both sides is generated from it.
- `docs/`: decision records, architecture and operations.
- `infra/`: deployment configuration.

## Build and test

- `apps/api`: `cd apps/api && ./mvnw -B verify`. Docker must be running: the tests start PostgreSQL 18 through
  Testcontainers, with the production role split: Flyway runs as `counthouse_migrator`, the API as
  `counthouse_app`. Format Java first with `./mvnw spotless:apply`; the build fails on unformatted code.
- `apps/site`: `npm ci` once at the root, then `npm run lint -w apps/site`, `npm run typecheck -w apps/site`,
  `npm run build -w apps/site` and `npm run check:i18n -w apps/site` (reads the built pages). The build is a static
  export written to `apps/site/out/`. Each page exists in Spanish (`/es/`) and Portuguese (`/pt/`): text comes from
  `src/content/strings/`, and each page's `generateMetadata` returns `alternates(locale, path)`.
- Other files: `npm ci` once, then `npm run format` (or `npm run format:check`) at the root, on the Node version
  in `.nvmrc`.
- Claude Code hooks in `.claude/hooks`: `npm run test:hooks` at the root.

## Working rules

- Every rule is enforced by a test or a CI check. Run them before proposing a change.
- Keep each change to one concern, as described in CONTRIBUTING.md.
