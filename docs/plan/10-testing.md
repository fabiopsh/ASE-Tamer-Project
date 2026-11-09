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
| Integration | Same Bruno public requests against the gateway | `docs/tests/bruno/services/*/00-setup` + `public` | P2 (optional) | P1 |
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
docker compose -f docker-compose.yml -f docker-compose.test.yml up -d --build --wait
scripts/test-env.sh        # writes docs/tests/bruno/.env (CA path, tests client secret) — gitignored
cd docs/tests/bruno
npm ci                     # @usebruno/cli 4.3.0 pinned in package.json
npm run test:component     # bru run services -r --env component
npm run test:integration   # bru run services/<svc>/00-setup services/<svc>/public … -r --env integration
npm run test:e2e           # bru run e2e -r --env integration
npm run export             # docs/tests/json/ (Postman v2.1)
```

`bru` is the Bruno CLI; `-r` is needed to run the requests inside sub-folders. The full commands are in `docs/tests/bruno/package.json`.

`scripts/test-env.sh` (done) reads `tests/ca.crt` and `tests/client.env` from the `secrets` volume with a throw-away container (`docker run --rm -v tamer_secrets:/s:ro tamer/init-secrets:dev …`) and writes them to `.test-secrets/` (gitignored) and `docs/tests/bruno/.env`.

Component tests of the five services other than `auth` still need tokens: each service folder starts with a `00-setup` request sequence that registers a random user on `auth` and gets a player token. `00-setup` uses **public endpoints only**, so the integration run executes it through the gateway too. `internal/00-setup` registers a **separate** player and gets a service token with the `tests` client (for `/internal/*` requests): internal requests change state (archive, reset, new run) that the public requests must not see. Folder order is fixed with `folder.bru` (`seq`): `00-setup` → `public` → `internal`.

## Bruno collection layout

```
docs/tests/bruno/
  bruno.json  package.json  export-json.js  README.md
  .env.example                 TESTS_CLIENT_SECRET=, CA_PATH=
  environments/
    component.bru              authUrl=https://localhost:9001, playersUrl=…:9002, …
    integration.bru            every *Url=https://localhost:8443
  services/
    auth/public/…              register, token
    auth/internal/…            client credentials
    players/00-setup/…         public endpoints only (seq 1)
    players/public/…           (seq 2)
    players/internal/00-setup/ separate player + service token
    players/internal/…         (seq 3)
    monsters/…  teams/…  encounters/…  history/…
  e2e/
    01-register.bru … 14-collection-after-battle.bru, 15-17 negative block
docs/tests/json/               exported collections (deliverable, Postman v2.1)
```

Rules:

- Requests use the per-service URL variable (`{{monstersUrl}}/api/monsters`), so `public/` folders run unchanged against the gateway with the `integration` environment.
- `internal/` folders run only with `component` (the gateway returns 404 for `/internal/*`, asserted in `e2e/`).
- `public/` requests use only variables set by `00-setup`; foreign resources come from a second player created in `00-setup` (monsters, teams).
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

1. Register → 2. token → 3. starter pool → 4. catalogue details of pool → 5. choose 3 starters → 6. collection has 3 → 7. validate + 8. create team → 9. list encounters → 10. start battle → 11. loop `attack` with `bru.setNextRequest` until `status = finished` (guard: max 100 rounds) → 12. history list contains the encounter → 13. detail matches the battle result → 14. if victory, collection has 4 monsters (3 after a defeat).

Plus a negative block: 15. `/internal/players` through the gateway → 404; 16. protected endpoint without token → 401; 17. forged token → 401.

## Acceptance-criteria traceability

`docs/backlog/acceptance_criteria.md` lists, under each AC, the tests that verify it (JUnit class/method or Bruno request name). Every red/yellow AC must reference at least one test.

---

## TEST-1 — Bruno component and integration collections · P1

**Depends on:** INFRA-6 (test override), the service being tested. Each service owner writes their own folder as part of the service task; this task covers the shared setup.

- [x] `bruno.json`, environments, `.env.example`, `scripts/test-env.sh`.
- [x] Shared setup snippets (register + token, service token) documented at the top of the collection README ([`docs/tests/bruno/README.md`](../tests/bruno/README.md)).
- [x] Review that every endpoint has the valid + invalid pair (matrix above; full table in the collection README).
- [x] Export each service folder and `e2e/` to JSON into `docs/tests/json/` (`npm run export`, Postman v2.1, test scripts kept in Bruno syntax); re-export before each delivery.
- [x] README section "Running the API tests".

## TEST-2 — End-to-end collection · P1

**Depends on:** EN-3, HI-1, TEST-1

- [x] The `e2e/` folder described above, green on a fresh `docker compose up`.

## TEST-3 — Locust performance tests · P2

**Depends on:** P1 complete

- [x] `docs/locust/locustfile.py` + `docs/locust/requirements.txt` (`locust==2.46.0`).
- [x] `HttpUser` with `self.client.verify = os.environ["CA_PATH"]`; `on_start`: register a unique user, get a token, choose starters, create a team.
- [x] Tasks (weights): browse catalogue 5, view collection 3, list encounters 2, full battle (start + attack loop) 2, history list/detail 2, validate team 1.
- [x] Run with the test override (raised auth rate limit): `locust -f docs/locust/locustfile.py --host https://localhost:8443 --headless -u 50 -r 5 -t 3m --csv docs/locust/results/run`.
- [ ] Save CSV results and a short analysis (p50/p95 per endpoint, errors, bottlenecks) for the report. First run done on 09/11/2026 ([`docs/locust/README.md`](../locust/README.md)); **repeat on the final P2 system** before writing the report.

## TEST-4 — AC traceability and coverage · P2

- [ ] Fill the test references in `acceptance_criteria.md` for every AC.
- [ ] Optional: `quarkus-jacoco` coverage report per service; target ≥ 70% lines on `service/` and `engine/` packages.
