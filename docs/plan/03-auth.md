# 03 — auth

Accounts, credentials and tokens. User stories: **US-01, US-02, US-05** (P2 completes US-05).

`auth` is the only holder of the JWT private key and of the list of service client secrets. It calls only `players` (profile creation).

## API

| Method | Path | Auth | Phase | Description |
|---|---|---|---|---|
| POST | `/api/auth/register` | public | P1 | JSON `{username, password}` → `201 {player_id, username}` |
| POST | `/api/auth/token` | public | P1 / P2 | OAuth2 token endpoint, form-urlencoded. `grant_type=password` (P1), `grant_type=refresh_token` (P2) |
| POST | `/api/auth/logout` | player | P2 | Form `refresh_token=…` → `204`, revokes it |
| POST | `/internal/auth/token` | client credentials | P1 | `grant_type=client_credentials`, `client_id`, `client_secret` → service token |

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
  "expires_in": 900,
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
| Lifetime | P1: `TAMER_ACCESS_TOKEN_TTL` default 3600 s; P2: 900 s (with refresh) | 300 s |

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
- [ ] Check the Access Security lab slides for any extra constraint (scopes, endpoint names) and align.

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

**Depends on:** AUTH-3

- [ ] Password grant also returns a refresh token: 32 random bytes (`SecureRandom`), base64url; DB stores only `SHA-256` hex; TTL 7 days; new `family_id`.
- [ ] `grant_type=refresh_token`: look up by hash; if valid → revoke it, issue new access + refresh token with the same `family_id` (rotation).
- [ ] **Reuse detection:** a revoked token presented again → revoke the whole family → `invalid_grant`.
- [ ] `POST /api/auth/logout` revokes the presented token's family.
- [ ] Scheduled cleanup (`quarkus-scheduler`, daily) of expired rows.
- [ ] Access token TTL back to 900 s.
- [ ] Tests for rotation, reuse, logout, expiry.

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
