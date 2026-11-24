# Contributing

These conventions apply to everyone working in the repository, people and coding agents alike. Rules for a part of
the code are added together with that part.

## Issues and pull requests

- Work starts from a GitHub issue, written before the work, with acceptance criteria. Issues are grouped by GitHub
  milestones.
- One issue, one pull request. An issue too large to review in one pull request is split into several, each a single
  concern: their descriptions start with `Part of #<issue>`, and the last one with `Closes #<issue>`. Otherwise the
  description starts with `Closes #<issue>`.
- `main` takes changes only through pull requests with green checks, squash-merged. The pull request title becomes
  the commit subject on `main`, so it follows the commit format below.

## Branches and commits

- Branches are named `<type>/<issue>-<slug>`, for example `feat/12-appointment-search`.
- Commits follow Conventional Commits. The scope names the app or area: `feat(api): ...`, `fix(backoffice): ...`.
  Scopes: `api`, `backoffice`, `site`, `contract`, `infra`, `docs`. Changes to the whole repository, such as CI or
  root files, have no scope: `ci: ...`, `chore: ...`.
- Commits are signed.

## Formatting and warnings

- One formatter per language, run by its own build and checked by CI.
- Java is formatted with palantir-java-format through Spotless: `cd apps/api && ./mvnw spotless:apply`. The check
  runs at the start of the build lifecycle, so `compile`, `verify` and `spring-boot:run` stop on unformatted code. The
  palantir-java-format IntelliJ plugin formats in the IDE; its formatter version can differ, and the build's check
  decides.
- Every other file Prettier can parse is formatted with Prettier and its default options: `npm run format` at the
  root. The Node version is in `.nvmrc`.
- Java compiles with `-Xlint:all,-processing -Werror`: every compiler warning fails the build. `processing` is
  excluded because it only reports annotation processors that find nothing to do. A dependency update that adds a
  deprecation fails its own pull request and is fixed there.

## Tests

- Every rule is enforced by a test or a CI check, not only by a sentence in a document. A rule checked only in
  review says so.
- A new test is seen failing once, by breaking the code it protects, before it is trusted.

## Contract

- `contract/openapi.yaml` is the source of truth for the API. It changes in the pull request whose code uses it: an
  operation is added together with the code that implements it.
- The conventions at the top of the file (tenancy, ids, naming, versioning) are checked only in review.
- The API's build generates Spring interfaces and models from the contract into `target/`; nothing generated is
  committed. The generated packages, `io.github.counthouse.contract` and its `model` subpackage, are outside the
  application's package, so every module can use them.
- The interfaces have no default methods, so a controller that leaves out an operation of its interface fails to
  compile (`generatedInterfacesHaveNoDefaultMethods`).

## Database

- Rules on a table's own data are named constraints. Each constraint has a test that sees it reject bad data and
  checks the constraint's name in the error.
- Rules the database cannot check, such as a valid time zone or a rule across modules, live in the owning module's
  service, each with a test.
- A migration is frozen once it is on `main`: change the schema with a new migration whose version is higher than
  every existing one. CI applies the migrations of `main` and then those of the branch with Flyway, and fails on a
  changed, renamed, deleted, duplicate, out-of-order or misnamed migration. It does the same on each push to `main`,
  from the previous commit to the new one.

### Tenant isolation

Tenant data is isolated by PostgreSQL row-level security (ADR 0001). `TenantCatalogTests` checks each rule over every
application schema, so a new table that breaks one fails the build. Each rule also runs on a test-only schema of
tables that each break it, so a rule that can no longer fail is caught too.

- Every table has a `tenant_id` column, enables and forces row-level security, and has exactly one policy, permissive,
  for all commands, comparing `tenant_id` with `current_tenant_id()`. The exceptions are `tenants` (enabled, not
  forced, readable only as the current tenant) and `flyway_schema_history`
  (`everyTableIsIsolatedByExactlyOneTenantPolicy`, `tenantsAreReadableOnlyAsTheCurrentTenant`).
- Every primary key is one `uuid` column (`everyPrimaryKeyIsOneUuidColumn`).
- Unique keys and exclusion constraints on tenant tables, other than the primary key, include `tenant_id`
  (`uniqueAndExclusionKeysOnTenantTablesIncludeTheTenant`).
- Foreign keys between tenant tables include `tenant_id`; a parent has a unique `(tenant_id, id)` for them to
  reference (`foreignKeysBetweenTenantTablesIncludeTheTenant`).
- The migration that creates a table grants `counthouse_app` only what the API needs, never `TRUNCATE`, `REFERENCES`
  or `TRIGGER` (`apiRoleHasNoPrivilegeThatGetsAroundThePolicies`).
- Views set `security_invoker`; materialized views are not used (`everyViewRunsWithTheCallersRights`).
- `counthouse_app` cannot create objects or temporary tables (`apiRoleCannotCreateObjectsOrTemporaryTables`).
- The API stores no large objects: row-level security does not cover them.
- Data access runs inside `TenantScope`: the transaction manager refuses a transaction without a tenant.

## Workflows

- The `workflows` job lints `.github/workflows` on every pull request. actionlint checks syntax, expressions and
  shell scripts (through shellcheck). zizmor checks security with its regular persona; any finding of low severity
  or above fails the job. zizmor's online audits, such as known-vulnerable actions, can fail a pull request that
  changes no workflow when a new advisory is published; the fix is the Dependabot security update or a bumped pin.
- Run both from the repository root, with the versions CI uses: actionlint's is in `ci.yml`, and zizmor prints its
  own at the start of its step in the `workflows` job log. The local zizmor run is offline; CI also runs the online
  audits.

  ```sh
  docker run --rm -v "$PWD:/repo:ro" --workdir /repo rhysd/actionlint:<version> -color
  docker run --rm -v "$PWD:/repo:ro" --workdir /repo ghcr.io/zizmorcore/zizmor:<version> \
    --offline --min-severity=low --min-confidence=low .
  ```

- Actions are pinned by commit SHA and updated by Dependabot. zizmor's version comes with the `zizmor-action`
  release.
- Images run from `run:` scripts (actionlint, gitleaks) are pinned by digest and bumped by hand: Dependabot does not
  read them.
- PostgreSQL is pinned by digest in the `migrations` service and in `TestcontainersConfiguration.java`. Bump both in
  one commit when a PostgreSQL minor release ships. The `workflows` job fails when they differ or the digest is
  missing.

## Decision records

- Decisions with real trade-offs get a record in `docs/adr/`, stating the decision, the alternatives and the
  trade-offs.
- A record stays `Proposed` until its code ships and may be edited until then. Once `Accepted` it is frozen: only its
  status changes, and a changed decision gets a new record. A formatter update may change a record's whitespace;
  that is not a change to the record.

## Agents

Coding agents follow the same rules. Claude Code reads `.claude/settings.json` only when it starts at the
repository root, and in an interactive session runs its hooks only after the folder is trusted. The rules and hooks
give early feedback; CI and the branch ruleset on `main` stay the guard.

- **Permission rules.** Claude Code asks before `git push`, `gh api`, `gh pr merge`, `gh release` and `gh repo`, and
  before editing anything under `.github/` or `.claude/`, or `LICENSE`. Command patterns are not a security
  boundary: a differently written command, such as `git -C <dir> push`, does not match. The ruleset blocks force
  pushes and changes to `main` without a pull request.
- **Frozen migrations.** A hook blocks an edit to a migration that exists on `origin/main`. Edits made through the
  shell, renames and a stale `origin/main` are not seen, and a crash of the hook lets the edit through; the
  `migrations` job checks every pull request.
- **API build at the end of a turn.** When a session has edited `apps/api`, a hook runs
  `./mvnw -B -o test-compile` as the agent stops and reports formatting failures and compiler warnings and errors.
  A failing build is reported once; the hook stays quiet until the next edit to `apps/api`. Edits made through the
  shell are not seen; the `api` job runs the same checks and the tests.
- The hooks are tested with `npm run test:hooks`, which the `agent-hooks` job runs.

Some areas are reviewed line by line, whoever wrote the change: the contract, exclusion constraints and their
concurrency tests, slot generation, row-level security policies and tenant scope, migrations, token handling, CSRF
protection, tenant provisioning and authorization.
