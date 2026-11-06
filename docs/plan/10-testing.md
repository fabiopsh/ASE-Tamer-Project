# 10 — Testing

What the requirements ask (§5):

- component tests for at least three internal services, **we cover all six** (P-12): for each endpoint one valid (2xx) and one invalid (error) request;
- component and integration tests in Bruno/Postman, exported as JSON;
- the same requests reused as integration tests against the gateway;
- Locust performance tests against the gateway only, covering the core functionalities;
- tests verifying the acceptance criteria: game rules, ownership, encounter outcomes, persistence.

## Test levels

| Level | Tool | Where | Runs in CI | Phase |
|---|---|---|---|---|
| Unit | JUnit 5 | `backend/<svc>/src/test` (engine, formulas, validators) | yes | P1 |
| Service (in-process) | `@QuarkusTest` + RestAssured, Dev Services Postgres, mocked REST clients | `backend/<svc>/src/test` | yes | P1 |
| Component | Bruno against one service container (test override) | `docs/tests/bruno/services/<svc>/` | P2 (optional) | P1 |
| Integration | Same Bruno public requests against the gateway | `docs/tests/bruno/services/*/public` | P2 (optional) | P1 |
| End-to-end | Bruno core-loop flow through the gateway | `docs/tests/bruno/e2e/` | P2 (optional) | P1 |
| Performance | Locust against the gateway | `docs/locust/` | no | P2 |

## Running services for component tests (P-13)

`docker-compose.test.yml` (INFRA-6) publishes each service's HTTPS port on localhost and enables the `tests` OAuth2 client:

| Service | URL |
|---|---|
| auth | `https://localhost:9001` |
| players | `https://localhost:9002` |
| monsters | `https://localhost:9003` |
| teams | `https://localhost:9004` |
| encounters | `https://localhost:9005` |
| history | `https://localhost:9006` |
| gateway | `https://localhost:8443` |

```bash
docker compose -f docker-compose.yml -f docker-compose.test.yml up -d --build
scripts/test-env.sh        # writes docs/tests/bruno/.env (CA path, tests client secret) — gitignored
cd docs/tests/bruno
bru run services -r --env component --cacert ../../../.test-secrets/ca.crt
bru run services/auth/public services/players/public services/monsters/public services/teams/public services/encounters/public services/history/public -r --env integration --cacert ../../../.test-secrets/ca.crt
bru run e2e -r --env integration --cacert ../../../.test-secrets/ca.crt
```

`bru` is the Bruno CLI (`npx @usebruno/cli@4.3.0 …`); `-r` is needed to run the requests inside sub-folders.

`scripts/test-env.sh` (done) reads `tests/ca.crt` and `tests/client.env` from the `secrets` volume with a throw-away container (`docker run --rm -v tamer_secrets:/s:ro tamer/init-secrets:dev …`) and writes them to `.test-secrets/` (gitignored) and `docs/tests/bruno/.env`.

Component tests of the five services other than `auth` still need tokens: each service folder starts with a `00-setup` request sequence that registers a random user directly on `auth` (9001), gets a player token, and gets a service token with the `tests` client (for `/internal/*` requests).

## Bruno collection layout

```
docs/tests/bruno/
  bruno.json
  .env.example                 TESTS_CLIENT_SECRET=, CA_PATH=
  environments/
    component.bru              authUrl=https://localhost:9001, playersUrl=…:9002, …
    integration.bru            every *Url=https://localhost:8443
  services/
    auth/public/…              register, token
    auth/internal/…            client credentials
    players/00-setup/…
    players/public/…
    players/internal/…
    monsters/…  teams/…  encounters/…  history/…
  e2e/
    01-register.bru … 12-history-detail.bru
docs/tests/json/               exported collections (deliverable)
```

Rules:

- Requests use the per-service URL variable (`{{monstersUrl}}/api/monsters`), so `public/` folders run unchanged against the gateway with the `integration` environment.
- `internal/` folders run only with `component` (the gateway returns 404 for `/internal/*`, which the integration run can assert separately in `e2e/`).
- Every request has `tests {}` assertions on status **and** body shape (and on the error `code` for invalid cases).
- Names: `<method> <path> - <valid|invalid: reason>`, e.g. `POST /api/teams - invalid: duplicate member`.
- Random data (usernames) generated in pre-request scripts; no dependence on execution order across folders.
- Battle requests assert invariants (HP decreases or stays, outcome in the allowed set) rather than exact numbers.

## Minimum request matrix (one valid + one invalid per endpoint)

| Service | Endpoints | Invalid cases to include |
|---|---|---|
| auth | register, token (password), internal token | duplicate username, short password, wrong password (same error as unknown user), unsupported grant, wrong client secret |
| players | me, starters GET/POST, internal create, internal new-run | no token, 2 starters, starter outside pool, second choice, player token on internal |
| monsters | monsters list/detail, abilities, elements, image, collection list/detail, every internal endpoint | bad filter, unknown id, foreign `owned_id`, level 51, reward retried (same id twice → one instance) |
| teams | list, create, get, put, delete, validate, internal get, internal reset | 4 members, duplicate member, foreign monster, foreign team id, 6th team |
| encounters | encounters list/detail, runs, battles start/current/actions, abandon, new run, internal runs | start twice, unavailable template, invalid team, switch to KO slot, action without battle, new run while active |
| history | list, detail, internal create | bad outcome filter, foreign record, duplicate `encounter_id` (idempotent 200) |

## End-to-end flow (`e2e/`)

1. Register → 2. token → 3. starter pool → 4. catalogue details of pool → 5. choose 3 starters → 6. collection has 3 → 7. validate + create team → 8. list encounters → 9. start battle → 10. loop `attack` with `bru.setNextRequest` until `status = finished` (guard: max 100 rounds) → 11. history list contains the encounter → 12. detail matches the battle result; if victory, collection has 4 monsters.

Plus a negative block: `/internal/players` through the gateway → 404; protected endpoint without token → 401.

## Acceptance-criteria traceability

`docs/backlog/acceptance_criteria.md` lists, under each AC, the tests that verify it (JUnit class/method or Bruno request name). Every red/yellow AC must reference at least one test.

---

## TEST-1 — Bruno component and integration collections · P1

**Depends on:** INFRA-6 (test override), the service being tested. Each service owner writes their own folder as part of the service task; this task covers the shared setup.

- [ ] `bruno.json`, environments, `.env.example`, `scripts/test-env.sh`.
- [ ] Shared setup snippets (register + token, service token) documented at the top of the collection README.
- [ ] Review that every endpoint has the valid + invalid pair (matrix above).
- [ ] Export each top-level folder to JSON into `docs/tests/json/` (Bruno app: *Export collection*); re-export before each delivery.
- [ ] README section "Running the API tests".

## TEST-2 — End-to-end collection · P1

**Depends on:** EN-3, HI-1, TEST-1

- [ ] The `e2e/` folder described above, green on a fresh `docker compose up`.

## TEST-3 — Locust performance tests · P2

**Depends on:** P1 complete

- [ ] `docs/locust/locustfile.py` + `docs/locust/requirements.txt` (`locust==<pinned>`).
- [ ] `HttpUser` with `self.client.verify = os.environ["CA_PATH"]`; `on_start`: register a unique user, get a token, choose starters, create a team.
- [ ] Tasks (weights): browse catalogue 5, view collection 3, list encounters 2, full battle (start + attack loop) 2, history list/detail 2, validate team 1.
- [ ] Run with the test override (raised auth rate limit): `locust -f docs/locust/locustfile.py --host https://localhost:8443 --headless -u 50 -r 5 -t 3m --csv docs/locust/results/run`.
- [ ] Save CSV results and a short analysis (p50/p95 per endpoint, errors, bottlenecks) for the report.

## TEST-4 — AC traceability and coverage · P2

- [ ] Fill the test references in `acceptance_criteria.md` for every AC.
- [ ] Optional: `quarkus-jacoco` coverage report per service; target ≥ 70% lines on `service/` and `engine/` packages.
