# 03 — auth

Accounts, credentials and tokens. User stories: **US-01, US-02, US-05** (P2 completes US-05).

`auth` is the only holder of the JWT private key and of the list of service client secrets. It calls only `players` (profile creation).

## API

| Method | Path | Auth | Phase | Description |
|---|---|---|---|---|
| POST | `/api/auth/register` | public | P1 | JSON `{username, password}` → `201 {player_id, username}` |
| POST | `/api/auth/token` | public | P1 / P2 | OAuth2 token endpoint, form-urlencoded. `grant_type=password` (P1), `grant_type=refresh_token` (P2) |
| POST | `/api/auth/logout` | refresh-token credential | P2 | Form `refresh_token=…` → `204`, revokes its family; works after access-token expiry |
| POST | `/internal/auth/token` | client credentials | P1 | `grant_type=client_credentials`, `client_id`, `client_secret` → service token |
| GET | `/internal/auth/authorize` | bearer, original method/path from gateway | P2 | Private gateway adapter: 204 allowed, 401 invalid token, 403 wrong role (INFRA-9) |

### Token endpoint (RFC 6749 §4.3 and §6)

Request:

```
POST /api/auth/token
Content-Type: application/x-www-form-urlencoded

grant_type=password&username=ash&password=pikachu123
```

Success `200`, headers `Cache-Control: no-store`, `Pragma: no-cache`:

```json
{
  "access_token": "eyJ...",
  "token_type": "Bearer",
  "expires_in": 300,
  "refresh_token": "kP3...",          // P2 only
  "scope": "player"
}
```

Errors (RFC 6749 §5.2), status 400:

| Case | `error` |
|---|---|
| Wrong username or password (same response for both) | `invalid_grant` |
| Missing/invalid parameters | `invalid_request` |
| Unknown `grant_type` | `unsupported_grant_type` |
| Expired/revoked refresh token (P2) | `invalid_grant` |

`/internal/auth/token` with wrong client credentials → `401 {"error": "invalid_client"}`.

### JWT claims

| Claim | Player token | Service token |
|---|---|---|
| `iss` | `tamer-auth` | `tamer-auth` |
| `aud` | `tamer-api` | `tamer-internal` |
| `sub` | `player_id` | `client_id` (e.g. `encounters`) |
| `upn` | username | `client_id` |
| `groups` | `["player"]` | `["service"]` |
| `iat`, `exp`, `jti` | yes | yes |
| Lifetime | P1: `TAMER_ACCESS_TOKEN_TTL` default 3600 s; P2: **300 s**, with refresh (P-22) | 300 s |

## Database (`auth-db`)

```sql
-- V1__init.sql
CREATE TABLE accounts (
    player_id      uuid PRIMARY KEY,
    username       varchar(20) NOT NULL,          -- lowercase; replaced in P2 (see AUTH-8)
    password_hash  varchar(72) NOT NULL,          -- bcrypt
    created_at     timestamptz NOT NULL DEFAULT now(),
    last_login     timestamptz
);
CREATE UNIQUE INDEX ux_accounts_username ON accounts (username);

CREATE TABLE refresh_tokens (                    -- used from P2
    token_hash  char(64) PRIMARY KEY,            -- SHA-256 hex
    player_id   uuid NOT NULL REFERENCES accounts(player_id),
    family_id   uuid NOT NULL,                   -- rotation chain
    expires_at  timestamptz NOT NULL,
    revoked     boolean NOT NULL DEFAULT false,
    created_at  timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX ix_refresh_tokens_player ON refresh_tokens (player_id);
```

---

## AUTH-1 — Schema and Account entity · P1

**Status:** done on 06/11/2026, see [implementation notes](#implementation-notes-06112026).

**Depends on:** INFRA-1, INFRA-3

- [x] Migration above, entity `Account`, repository `findByUsername(String normalized)`.
- [x] `UsernameNormalizer`: trim + `toLowerCase(Locale.ROOT)`; used for both registration and login.
- [x] Unit test of the normalizer.

**Done when:** schema created at startup; duplicate username insert fails at DB level.

---

## AUTH-2 — Registration (US-01) · P1

**Status:** done (needs PL-1 to work end to end) on 06/11/2026, see [implementation notes](#implementation-notes-06112026).

**Depends on:** AUTH-1, AUTH-4 (local service token), PL-1 (`POST /internal/players`; mock until ready)

**Rules**

- `username`: 3–20 chars, `^[a-zA-Z0-9_]+$`, unique case-insensitively.
- `password`: 8–72 chars (**bcrypt ignores bytes beyond 72**), not equal to the username.

**Flow**

```mermaid
sequenceDiagram
    participant C as Client
    participant A as auth
    participant P as players
    C->>A: POST /api/auth/register
    A->>A: validate, normalise, bcrypt
    A->>A: BEGIN; INSERT account
    A->>P: POST /internal/players {player_id}
    alt profile created (201/200)
        A->>A: COMMIT
        A-->>C: 201 {player_id, username}
    else players unavailable
        A->>A: ROLLBACK
        A-->>C: 503 DEPENDENCY_UNAVAILABLE
    end
```

- [x] DTO `RegisterRequest(username, password)` with Bean Validation.
- [x] `BcryptUtil.bcryptHash(password, 12)` (cost 12).
- [x] Insert and the call to `players` inside one `@Transactional` method; any exception → rollback.
- [x] If the commit fails after `players` succeeded, an orphan profile may remain: harmless (never reachable without an account) and documented.
- [x] Duplicate username → `409 USERNAME_TAKEN`, also when caught as unique-constraint violation (concurrent requests).
- [x] REST client `PlayersClient` with `@Retry` (the call is idempotent on `player_id`), `@Timeout`, `@CircuitBreaker`.
- [x] `auth` signs its own service token for this call (no HTTP round trip to itself).
- [x] Never log the password; log `player_id` only.

**Done when**

- Valid registration → 201 and the profile exists in `players`.
- `players` down → 503 and no account row.
- Duplicate (also `Ash` vs `ash`) → 409.

---

## AUTH-3 — OAuth2 password grant and player JWT (US-02) · P1

**Status:** done on 06/11/2026, see [implementation notes](#implementation-notes-06112026).

**Depends on:** AUTH-1, INFRA-4

- [x] Resource consuming `application/x-www-form-urlencoded` with `@FormParam`s; dispatch on `grant_type`.
- [x] `password` grant: normalise username, load account; if absent, still run `BcryptUtil.matches(password, DUMMY_HASH)` so timing does not reveal whether the username exists; on mismatch → `invalid_grant` with the same `error_description` ("Invalid username or password.").
- [x] Sign with `Jwt.issuer("tamer-auth").audience("tamer-api").subject(id).upn(username).groups(Set.of("player")).expiresIn(ttl).jws().keyId("tamer-1").sign()` using `smallrye.jwt.sign.key.location=/run/secrets/jwt-private.pem`.
- [x] Update `last_login`.
- [x] `Cache-Control: no-store` on every token response.
- [ ] Complete the P2 Access Security follow-up tracked in AUTH-7, INFRA-9/10, TEST-5 and CI-3/4. Keep the OAuth2 form-urlencoded contract and `400 invalid_grant` for incorrect login credentials; protected API calls use 401/403 as specified in the conventions.

**Done when**

- Correct credentials → token accepted by another service.
- Wrong username and wrong password → identical 400 `invalid_grant`.
- Tampered or expired token → 401 everywhere.

---

## AUTH-4 — Client credentials grant for services · P1

**Status:** done on 06/11/2026, see [implementation notes](#implementation-notes-06112026).

**Depends on:** AUTH-3, INFRA-4

- [x] `POST /internal/auth/token` (not routed by the gateway), `grant_type=client_credentials`.
- [x] Known clients from config `tamer.service-clients.<id>=<secret>` (`players`, `teams`, `encounters`, and `tests` only if `TAMER_TEST_CLIENT_ENABLED=true`).
- [x] Compare secrets in constant time (`MessageDigest.isEqual` on UTF-8 bytes).
- [x] Service token claims as in the table above, TTL 300 s.
- [x] `LocalServiceTokenSigner` used by `auth` for its own outgoing calls.

**Done when**

- `players` obtains a token and calls `monsters` `/internal/*` successfully.
- Wrong secret → 401 `invalid_client`; player token on `/internal/*` → 403.

---

## AUTH-5 — Tests · P1

**Status:** done on 06/11/2026, see [implementation notes](#implementation-notes-06112026).

**Depends on:** AUTH-2, AUTH-3, AUTH-4

- [x] `@QuarkusTest` + RestAssured, Dev Services Postgres, `@InjectMock @RestClient PlayersClient`.
- [x] Cases: register valid / duplicate / case-insensitive duplicate / short password / 73-char password / invalid username chars / players failure → rollback; token valid / wrong password / unknown user / missing grant_type / unsupported grant; client credentials valid / invalid; claims (`sub`, `groups`, `aud`, `exp`).
- [x] Unit test: stored hash starts with `$2` and is not the password.
- [x] Bruno: `auth` component collection and the shared "login" request used by other collections (see [10-testing.md](10-testing.md)).

---

## AUTH-6 — Acceptance criteria US-01, US-02 · P1

**Status:** done on 06/11/2026, see [implementation notes](#implementation-notes-06112026).

Write in [`docs/backlog/acceptance_criteria.md`](../backlog/acceptance_criteria.md). Example:

> **US-01 — Create an account** · Dependencies: none
> - **Given** no account named `ash` exists, **when** I register `ash` with a valid password, **then** I receive 201 with my `player_id` and a player profile exists.
> - **Given** an account `ash` exists, **when** I register `ASH`, **then** I receive 409 and no account is created.
> - **Given** any state, **when** I register with a 7-character password, **then** I receive 400 and no account is created.
> - **Given** the players service is unavailable, **when** I register, **then** I receive 503 and no account is created.

> **US-02 — Log in** · Dependencies: US-01
> - **Given** a registered account, **when** I request a token with the correct credentials, **then** I receive an access token of type Bearer.
> - **Given** a registered account, **when** I use a wrong password or an unknown username, **then** I receive the same `invalid_grant` error.
> - **Given** no token or an expired token, **when** I call a protected endpoint, **then** I receive 401.

---

## AUTH-7 — Refresh tokens and logout · P2

**Status:** planned; expanded after the Access Security review of 17/11/2026. The existing table and response field do not constitute refresh/logout implementation.

**Depends on:** AUTH-3, INFRA-10. Coordinate with FE-3, INFRA-9, TEST-5 and CI-3.

**API contract**

Use [RFC 6749 §6](https://www.rfc-editor.org/rfc/rfc6749.html#section-6) for `POST /api/auth/token` with form `grant_type=refresh_token&refresh_token=…`. No unexpired access token is required. A successful password or refresh grant returns an access token (`expires_in=300`), an opaque refresh token, `token_type=Bearer` and `scope=player`, with `Cache-Control: no-store` and `Pragma: no-cache`. Service client-credentials grants keep their 300 s access token and do not receive refresh tokens.

`POST /api/auth/logout` accepts form `refresh_token=…`. Possession of that credential authorizes revocation of its own family; the endpoint does not require a valid access token or accept a caller-selected `player_id`/`family_id`. A well-formed request returns 204 for known, unknown, expired or already-revoked tokens. If the token is known, revoke its whole family, including when an older rotated token is submitted. Missing/empty parameters return `400 invalid_request`; database failure returns 503, never a successful revocation response.

**Implementation**

- [ ] Implement the refresh-token entity/repository using the existing `refresh_tokens` schema. Generate 32 random bytes with `SecureRandom`, encode base64url and store only the SHA-256 hex digest. Keep passwords and both token types out of logs and error bodies.
- [ ] Each password login creates a separate `family_id` with an absolute expiry 7 days after login. Rotation preserves that family and its original `expires_at`; it does not extend the session indefinitely.
- [ ] Set the default and Compose override of `TAMER_ACCESS_TOKEN_TTL` to **300 s**, update token examples/OpenAPI, and assert `exp - iat = 300` and `expires_in = 300` in tests. Historical P1 3600 s settings are not the final configuration.
- [ ] Add the refresh grant dispatch. A valid, unexpired, unrevoked token for an existing account is consumed once: revoke it and persist a fresh token in the same transaction before returning the new pair. Invalid, expired, revoked or unknown credentials produce the same `400 invalid_grant` without an account-existence disclosure.
- [ ] **Reuse detection:** presenting a previously consumed token revokes every token in that family and returns `invalid_grant`. Other login families for the same player remain usable.
- [ ] Serialize rotation, reuse handling and logout for the same player using an account-row database lock; re-read token state under the lock. Commit revocations even when returning an OAuth error, so exception rollback cannot undo reuse detection. A concurrent refresh/logout must not leave a usable descendant after revocation.
- [ ] Implement idempotent family revocation for logout with the contract above, and rate-limit it alongside register/token. Do not wrap rotating grants in automatic HTTP retries: a lost response may follow a successful rotation.
- [ ] Add daily scheduled cleanup of expired rows. Retain consumed/revoked rows until family expiry so reuse detection still works. Document the configured JWT clock skew alongside the 300 s lifetime.
- [ ] Add service tests for issuance, hashes at rest, successful rotation, unknown/expired/revoked tokens, reuse revoking the family, independent sessions, concurrent refresh and refresh/logout, idempotent logout, logout after access-token expiry, and persistence failures.
- [ ] Add component and gateway Bruno flows, UI integration (FE-3), API exports and acceptance-criteria references. Run the API flows in CI-3 through TEST-5.

**Revocation boundary:** logout and reuse detection immediately prevent further refreshes. Already issued access JWTs remain valid until their 300 s expiry, subject to the configured clock skew, because services verify them locally. The gateway adapter performs the same signature/claim/policy checks; this task does not introduce an access-token denylist or database introspection on every API call.

**Done when:** login, access-token expiry, refresh rotation, replay rejection and logout work through the gateway. A revoked family cannot create another access token; another login family is unaffected. The frontend clears its session on logout, handles expiry through refresh, and API/CI tests prove the revocation boundary above.

---

## AUTH-8 — US-05 hardening and encryption at rest · P2

**Depends on:** AUTH-2, AUTH-3

- [ ] Replace `username` column with:
  - `username_hmac char(64)` unique: `HMAC-SHA256(username_hmac_key, normalized username)`, used for lookup and uniqueness;
  - `username_enc bytea`: `AES-256-GCM(username_enc_key, normalized username)`, random 12-byte IV prepended.
  Migration `V2__encrypt_username.sql` + Java migration that converts existing rows (or reset the DB: there is no production data).
- [ ] JPA `AttributeConverter` (or explicit service methods) for encryption; keys from `secrets.properties`.
- [ ] Login backoff: after 5 failures for the same `username_hmac` within 15 minutes, reject further attempts for 15 minutes with the same `invalid_grant` (no account-lock disclosure). Store counters in a table `login_failures`.
- [ ] Security event logs (failed login, refresh reuse) with `player_id`/hmac only.
- [ ] Password policy check: reject passwords equal to username; optional top-1000 common-password list.
- [ ] Review against the Access Security lab; document in the report.

---

## Implementation notes (06/11/2026)

| Task | Note |
|---|---|
| AUTH-1 | `Account` is a Panache entity with `findByUsername`; no separate repository class. |
| AUTH-2 | bcrypt runs **outside** the transaction (it is slow); `QuarkusTransaction.requiringNew()` wraps the duplicate check, the insert (`persistAndFlush`, so a concurrent duplicate fails on the unique index → 409) and the call to `players`. Any client failure → `503 DEPENDENCY_UNAVAILABLE` and rollback. Passwords longer than 72 **bytes** (not only characters) are rejected, as are passwords equal to the username (`details.code = EQUALS_USERNAME`). |
| AUTH-2 | Until `players` implements `POST /internal/players` (PL-1, which needs MO-2 for the starter pool), registration through the containers returns 503 by design. The `@QuarkusTest`s mock `PlayersClient`. |
| AUTH-3 | `PasswordHasher.burn()` runs a bcrypt comparison against a dummy hash when the username does not exist. The token response carries `scope` (`player`/`service`). The upn of the player token is the normalised username. |
| AUTH-4 | Known clients come from `tamer.service-clients.<id>` (a `@ConfigMapping` map). Secrets are compared as SHA-256 digests with `MessageDigest.isEqual`, also for unknown client ids. `tests` is accepted only with `tamer.test-client.enabled=true` (env `TAMER_TEST_CLIENT_ENABLED`, set by the test override). |
| AUTH-5 | 38 tests: `RegistrationResourceTest`, `TokenResourceTest`, `InternalTokenResourceTest`, `security/TokenVerificationTest` (tampered, `alg: none`, expired, wrong issuer, wrong audience, wrong role), unit tests for the normaliser and the hasher. `JwtKeysTestResource` generates a throw-away key pair: Quarkus generates its own keys in test mode and sets the inline properties `smallrye.jwt.sign.key` / `mp.jwt.verify.publickey`, which win over `*.location`, so the resource overrides both forms. Bruno: `docs/tests/bruno/services/auth/` (public + internal). |
| AUTH-6 | Written in [`docs/backlog/acceptance_criteria.md`](../backlog/acceptance_criteria.md). |
