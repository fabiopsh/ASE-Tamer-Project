# 01 — Conventions

Rules shared by every service and by the frontend. There is **no shared Maven module** (decision A9): when a convention needs code (error mapper, service-token filter, request-id filter), the reference implementation is written once in `players` and **copied verbatim** into the other services. Changes to copied code must be applied to every copy in the same pull request.

## Repository layout

```
.github/workflows/ci.yml           CI (copy kept in docs/ci/)
backend/
  pom.xml                          aggregator: Quarkus BOM, Java 21, modules, plugin versions
  auth/ players/ monsters/ teams/ encounters/ history/
    pom.xml
    Dockerfile
    src/main/java/it/unipi/ase/tamer/<service>/...
    src/main/resources/application.properties
    src/main/resources/db/migration/V<n>__<name>.sql
    src/test/java/...
  monsters/seed/                   catalogue seed (generated, do not edit by hand)
  encounters/seed/                 stage templates seed
gateway/
  Dockerfile
  templates/default.conf.template  nginx config with env substitution
infra/
  init-secrets/Dockerfile
  init-secrets/init-secrets.sh
  postgres/                        (only if a custom image is needed)
frontend/                          Vite + React + TypeScript
docker-compose.yml
docker-compose.test.yml            exposes services on localhost for component tests
scripts/                           helper scripts (export OpenAPI, read test secrets, ...)
docs/
  plan/                            this plan
  backlog/                         completed backlog + acceptance criteria
  openapi/                         <service>.yaml + gateway.yaml
  tests/bruno/                     Bruno collections (source)
  tests/json/                      exported JSON collections (deliverable)
  locust/locustfile.py             performance tests (P2)
  ci/ci.yml                        copy of the workflow
  report/                          final report (P2)
```

## Java code structure (per service)

```
it.unipi.ase.tamer.<service>
  api/            JAX-RS resources (public: *Resource, internal: *InternalResource)
  api/dto/        request/response records with Bean Validation annotations
  domain/         Panache entities and enums
  repository/     PanacheRepository classes (only when queries are non-trivial)
  service/        business logic, @Transactional boundaries
  client/         MicroProfile REST client interfaces + DTOs of the called service
  security/       service-token provider, client request filter, ownership helpers
  error/          ApiException, error codes, ExceptionMappers
```

Rules:

- Resources are thin: validate input, read `player_id` from the token, call a service, map to DTO.
- Entities never leave the service layer; responses are always DTO records.
- Business rules live in `service/` (or `engine/` for encounters) and are unit-testable without Quarkus.
- Names: classes in `PascalCase`, packages lowercase, constants `UPPER_SNAKE`.

## API conventions

| Topic | Rule |
|---|---|
| Base paths | Public `/api/<area>/...`, internal `/internal/<area>/...`. Quarkus `/q/*` (health, OpenAPI) never routed by the gateway |
| JSON naming | `snake_case` (`quarkus.jackson.property-naming-strategy=SNAKE_CASE`) |
| Unknown fields | Rejected: `quarkus.jackson.fail-on-unknown-properties=true` |
| IDs | `player_id`, `run_id`, `owned_id`, `team_id`, `encounter_id`: UUID v4. `monster_id`, `ability_id`, `template_id`: integers from seed |
| `encounter_id` | Equals the battle id generated when the battle starts |
| Time | ISO-8601 UTC strings (`Instant`), e.g. `2026-11-06T10:15:30Z` |
| Identity | `player_id` always from the JWT `sub` claim, **never** from path, query or body of a public endpoint |
| Ownership | Every query on player data is scoped by `player_id`. Not found **or** not owned → `404` (P-11) |
| Creation | `201 Created` with the created resource in the body |
| Deletion | `204 No Content` |
| Content type | `application/json` everywhere, except the OAuth2 token endpoints (`application/x-www-form-urlencoded` request) |

### Pagination

Query parameters `page` (0-based, default 0) and `size` (default 20, max 50). Out-of-range values → `400`.

```json
{
  "items": [ ... ],
  "page": 0,
  "size": 20,
  "total_items": 100,
  "total_pages": 5
}
```

### Error format

Every error response (except OAuth2 token endpoints, see below) uses:

```json
{
  "error": "TEAM_INVALID",
  "message": "Team must contain between 1 and 3 monsters.",
  "details": [ { "field": "members", "code": "TEAM_SIZE" } ]
}
```

`details` is optional. Messages are in English and never contain stack traces, SQL, or internal hostnames.

| HTTP | `error` | When |
|---|---|---|
| 400 | `VALIDATION_ERROR` | Bean Validation failure, malformed JSON, unknown field, bad enum |
| 400 | domain codes (`TEAM_INVALID`, `INVALID_ACTION`, `INVALID_STARTERS`, ...) | Business rule violated |
| 401 | `UNAUTHORIZED` | Missing, invalid or expired token |
| 403 | `FORBIDDEN` | Wrong role (e.g. player token on `/internal/*`) |
| 404 | `NOT_FOUND` | Missing or not owned |
| 409 | domain codes (`USERNAME_TAKEN`, `BATTLE_IN_PROGRESS`, `STARTERS_ALREADY_CHOSEN`, ...) | State conflict |
| 429 | — | Rate limit (from Nginx) |
| 503 | `DEPENDENCY_UNAVAILABLE` | A called service failed after retries / circuit open |
| 500 | `INTERNAL_ERROR` | Anything else (logged with request id) |

Implementation: an `ApiException(status, code, message)` thrown by services, plus `ExceptionMapper`s for `ApiException`, `ConstraintViolationException`, Jackson mapping exceptions, `NotFoundException`, `WebApplicationException`, and a catch-all. Reference implementation in `players`, copied to the others.

**OAuth2 exception:** `/api/auth/token` and `/internal/auth/token` return RFC 6749 errors: `{"error": "invalid_grant", "error_description": "..."}` with status 400 (or 401 for `invalid_client`).

## Security conventions

| Topic | Rule |
|---|---|
| Roles | JWT `groups` claim: `player` for users, `service` for services |
| Public endpoints | `@RolesAllowed("player")`, except `/api/auth/token`, `/api/auth/register` and images (`@PermitAll`) |
| Internal endpoints | `@RolesAllowed("service")` |
| JWT verification | Local, RS256, public key from `/run/secrets/jwt-public.pem`; issuer `tamer-auth`; audiences `tamer-api` (player) and `tamer-internal` (service) |
| Input | Bean Validation on every DTO field (size, pattern, range); path/query params validated too; only parameterised queries (Panache) |
| Secrets | Read from files mounted under `/run/secrets` (see INFRA-4); never in `application.properties`, env files or logs |
| Logging | Never log passwords, tokens, secrets or full request bodies of auth endpoints |
| Dev/test profiles | `%dev` and `%test` disable TLS and use Dev Services; `%prod` (containers) enforces TLS |

## Inter-service calls and resilience

- MicroProfile REST client, one interface per called service in `client/`, configured with `quarkus.rest-client.<name>.url=${TAMER_<NAME>_URL}` (e.g. `https://monsters:8443`).
- TLS: REST clients use the named TLS configuration `internal` that trusts only the local CA (`/run/secrets/ca.crt`).
- Authentication: a `ServiceTokenProvider` obtains a token with `client_credentials` from `auth`, caches it until 30 s before expiry, and a `ClientRequestFilter` adds `Authorization: Bearer <service token>`. `auth` itself signs its service token locally.
- Every client method: `@Timeout(2000)`, `@Retry(maxRetries = 2, delay = 200)` **only on idempotent calls**, `@CircuitBreaker(requestVolumeThreshold = 10, failureRatio = 0.5, delay = 5000)`. Failures surface as `503 DEPENDENCY_UNAVAILABLE`. This avoids the *wobbly service interactions* smell.
- Every internal endpoint that changes data is **idempotent**, with the key given in its spec.
- Request id: Nginx sets `X-Request-Id`; each service puts it in the log MDC and forwards it on internal calls.

## Persistence

- One Postgres per service; schema only via Flyway migrations `V<n>__<description>.sql`, never edited after merge.
- Hibernate `database.generation=none`; Flyway `migrate-at-start=true`.
- Optimistic locking (`@Version`) on rows updated concurrently (players, runs).
- Timestamps `timestamptz`, UUIDs `uuid`, JSON columns `jsonb`.

## Versions

Every dependency is pinned to an exact version: Maven dependencies (via the Quarkus BOM version and explicit plugin versions), Docker image tags (never `latest`, never only a major), npm packages (exact versions in `package.json`, `package-lock.json` committed), GitHub Actions (exact tag or commit SHA), Python packages for Locust (`requirements.txt` with `==`).

Record the chosen versions here when INFRA-1 is done:

| Component | Version |
|---|---|
| Java (Temurin / UBI OpenJDK) | 21 (`maven.compiler.release=21`); runtime image `registry.access.redhat.com/ubi9/openjdk-21-runtime:1.24` |
| Quarkus platform | 3.39.5 |
| Maven (build image) | `maven:3.9.16-eclipse-temurin-21`; plugins: compiler 3.16.0, surefire/failsafe 3.6.1, resources 3.5.0 |
| PostgreSQL image | `postgres:17.11-alpine3.24` (not 18: its data directory layout changed) |
| nginx-unprivileged image | `nginxinc/nginx-unprivileged:1.30.6-alpine3.24` |
| Alpine (init-secrets) | `alpine:3.24.4` |
| Node.js | 24 LTS (frontend tested with 24.13) |
| Bruno CLI | `@usebruno/cli@4.3.0` (run with `npx`) |
| Locust | _fill in_ |

## Git workflow

- Branch per issue: `feat/<issue-id>-short-name` (e.g. `feat/TE-1-teams-service`), `fix/...`, `docs/...`.
- Pull request to `main` with at least one reviewer; CI must be green.
- Conventional commits (`feat:`, `fix:`, `docs:`, `test:`, `chore:`), referencing the issue (`Closes #12`).
