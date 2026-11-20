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
- Commits follow Conventional Commits with the app as scope: `feat(api): ...`, `fix(backoffice): ...`. Scopes: `api`,
  `backoffice`, `site`, `contract`, `infra`, `docs`.
- Commits are signed.

## Tests

- Every rule is enforced by a test or a CI check, not only by a sentence in a document.
- A new test is seen failing once, by breaking the code it protects, before it is trusted.

## Decision records

- Decisions with real trade-offs get a record in `docs/adr/`, stating the decision, the alternatives and the
  trade-offs.
- A record stays `Proposed` until its code ships and may be edited until then. Once `Accepted` it is frozen: only its
  status changes, and a changed decision gets a new record.
