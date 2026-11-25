# 0002. Access tokens and the resource server

Status: Proposed

## Context

Staff sign in through Keycloak. The backoffice is the OAuth 2.0 client: it holds the session and sends an access token
with each call to the API. The API never sees a password, issues no token and keeps no session. It must accept only
tokens Keycloak issued for it to the backoffice, know the tenant without trusting anything the client sends, and know
which staff member is calling and with which role. Some staff will share a desktop at the clinic's counter, so a
Keycloak user is not always one person.

## Decision

- **RFC 9068 access tokens only.** The API validates each token with Spring Security's RFC 9068 validator: header
  `typ` `at+jwt`, the configured issuer, audience `counthouse-api`, client id `bff`, and `exp`, `iat`, `jti` and `sub`
  present, signed with RS256 by a key Keycloak publishes. Keycloak puts `azp` in its tokens but no `client_id`, so the
  realm gives the backoffice's client a mapper that adds it; the token then conforms instead of the validator being
  loosened. The client id is set explicitly: without it, the validator accepts any client's token.
- **Public issuer, internal keys.** The expected issuer is Keycloak's public URL, pinned by Keycloak's hostname
  setting; the keys are fetched from its internal URL on the server's network, never through the public tunnel.
- **Keys cached, fetched rarely, kept through an outage.** The key set is cached for 5 minutes and fetched at most
  once per 30 seconds, whatever the tokens ask for, with 2-second timeouts. When a fetch fails, the last keys stay in
  use for 10 minutes from that first failure. It comes after Keycloak went down, and tokens live 5 minutes, so this
  covers every token issued before the outage, also when the API was idle. A token signed with a key that is not in
  the key set is a 401; a request when no usable key set exists is a 503, so the backoffice can tell a rejected token
  from a dependency failure.
- **New signing keys are published before they sign.** A new key is added to Keycloak below the current one's
  priority, so Keycloak publishes it without signing with it, and made the signing key only after one cache lifetime
  (5 minutes), when every API instance has fetched it. Otherwise new tokens would carry a key id the API does not have
  yet, and the rate limit could keep it from fetching the key, signing staff out.
- **The tenant is the organization alias.** Each tenant's Keycloak organization has the tenant id as its alias. The
  token's `organization` claim must hold exactly one entry, the tenant id in its canonical form. Keycloak refuses to
  change an alias after creation, so the tenant of a token cannot be edited in the admin console, as an organization
  attribute could.
- **Person and login are separate.** `staff_members` holds the person (display name, role, active); `logins` links a
  Keycloak user, by issuer and subject, to a staff member. Both are tenant tables under ADR 0001. The split exists for
  shared desktops, where one Keycloak user will act for several people.
- **Each request reads the staff member.** After the token passes, the rest of the request runs in the token's
  tenant: the API looks up the login by issuer and subject in that tenant, and the staff member must be active. The
  role comes from the database, not the token. An unknown login, an inactive staff member, or a tenant without a row
  is a 401.
- **The realm is code.** `infra/keycloak/realm.json` defines the realm and the backoffice's client: confidential,
  authorization code with PKCE, no password grant, refresh tokens not rotated, no offline tokens, account console and
  built-in clients off, no self-service required action but passkey registration. It holds no keys, secrets or users.
  Tests start Keycloak from it and check the imported realm through the Admin API.

## Alternatives

- **The tenant in an organization attribute.** Keycloak lets an admin edit it, and a realm import dropped it.
- **Loosening the validator to `azp`.** It works with Keycloak's default tokens, but checks a claim RFC 9068 does not
  define for this purpose, where a mapper makes the token conform.
- **The role in the token.** It would stay valid for the token's lifetime after a change, and Keycloak would become a
  second source of roles.
- **Rotating refresh tokens.** RFC 9700 requires rotation or sender-constrained tokens for public clients only; the
  backoffice is a confidential client.

## What this does not protect against

- A stolen backoffice session cookie, until the session ends.
- A stolen unlocked phone, until the staff member is deactivated.
- A compromised backoffice server, which holds the client secret and the sessions.
- A compromised Keycloak, or anyone with its admin rights, who can issue any token.
- A Keycloak that sends its key set very slowly. The 2-second timeout applies to each read, so the request that
  fetches waits until the transfer ends, and other requests wait up to 2 seconds each for it before using the last
  keys. Keycloak is reached only on the internal network, so this needs a broken or compromised Keycloak.
- A token used within its 5 minutes after the Keycloak user is disabled: the API does not ask Keycloak about each
  token. Deactivating the staff member in the API stops it at once.

## Consequences

- Deployed environments apply the realm file on every deploy with keycloak-config-cli, since Keycloak's own import
  skips an existing realm.
- Tests need Docker for Keycloak as well as PostgreSQL.
- Changing Keycloak's public hostname changes the issuer, so every login's issuer must be updated with it.
