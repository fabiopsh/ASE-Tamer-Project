# Implementation handoff: 04 players, 05 monsters, 06 teams

**Date:** 09/11/2026  
**Scope:** P1 tasks PL-1, MO-1, MO-2, TE-1 (plans 04, 05, 06). The existing 03/auth implementation was preserved.

## What was added

| Area | Implementation |
|---|---|
| `players` | `Player` entity and V1 migration; persistent pool of 10 random common species; profile and starter-pool REST endpoints; idempotent internal profile creation and run rotation; validated 3-starter selection, reward grant and run opening orchestration. |
| `monsters` | V1 tables, Java Flyway V2 seed for 100 monsters/28 abilities/7 elements; public catalogue, abilities, elements, public sprites; per-run personal collection; instance stats and XP; internal candidate/stats queries, idempotent starter/reward/XP grants and archival. |
| `teams` | V1 team/slots schema; same rule validator for validate/create/update/internal lookup; team list/detail/create/replace/delete; internal revalidation with current stats and idempotent reset. |
| Tests and docs | Pure unit tests for pool selection, stat/XP formulas, team validation; Bruno component request suites (`docs/tests/bruno/services/{players,monsters,teams}`); US-03, US-06/07, US-09–12, US-14–16 acceptance criteria. |

All services use the existing JAX-RS `Resource` → CDI `service` → Panache entity design, existing error mappers, `@RolesAllowed`, OpenAPI operation annotations, `snake_case` JSON and REST clients with service-token filters and retry/timeout/circuit breaker.

## Contracts needed by 07 (`encounters`)

The following endpoints are now exposed, with **service JWT** and internal HTTPS. `players` also depends on `encounters` to complete starter selection.

| Method | Endpoint | Use by encounters |
|---|---|---|
| `GET` | `/internal/monsters?rarity=&element=&exclude=1,2` | Candidate species for a wild encounter; returns `[{monster_id,element,rarity}]` |
| `GET` | `/internal/monsters/{id}/stats?level=5` | Effective stats and ability; response includes `monster_id`, `name`, `element`, `rarity`, `level`, `stats`, `ability` |
| `GET` | `/internal/collections/{pid}/instances?ids=<uuid>,<uuid>` | Owned instances with `run_id`, `status`, `level`, `stats`, `ability` |
| `POST` | `/internal/collections/{pid}/monsters` | Reward: `{run_id,monster_id,level,encounter_id,source}`; idempotent on `encounter_id`; `201` created/`200` repeated; returns an owned instance with `owned_id` |
| `POST` | `/internal/collections/{pid}/xp` | `{encounter_id,owned_ids:[uuid,...],xp}`; idempotent per `encounter_id`; returns `[{owned_id,level_before,level_after,xp}]` |
| `POST` | `/internal/collections/{pid}/archive` | `{run_id}` → `{count}`; idempotent |
| `GET` | `/internal/teams/{team_id}?player_id=<uuid>` | Revalidates ownership, active status and run; returns team with ordered members and stats; `409 TEAM_INVALID` if invalid |
| `POST` | `/internal/teams/{pid}/reset` | `{run_id}` → `{count}`; idempotent |
| `POST` | `/internal/players/{pid}/new-run` | `{ended_run_id}` → `{current_run_id}`; idempotent if the run was already rotated |

**07 must implement** `POST /internal/runs` with `{run_id,player_id}` and idempotency on `run_id`. `players` calls this after monsters has granted the three starters. Without this endpoint, `/api/players/me/starters` will return `503 DEPENDENCY_UNAVAILABLE` by design; the request can be retried after 07 is available without granting duplicate starters.

`monsters` is the only authority for effective stats and levelling. `teams` never saves denormalised monster stats; it reads them from `monsters` whenever returning a team. No service accesses another service's database.

## Build and validation

The source tree was checked using the Java 21 compiler's syntax parser. Standalone functional checks of `PoolGenerator`, `Leveling`, `StatCalculator`, and `TeamValidator` succeeded with JDK 21. JSON seed counts (100/28/7), the 100 sprite files, and Maven POM XML were checked.

**Not run in the editing environment:** `mvn verify`, Quarkus/Dev Services tests, Docker Compose startup and the HTTPS/Bruno end-to-end suites, because Maven, Docker and the downloadable Quarkus dependencies are unavailable. The code should be built and tested on a development machine before merging:

```sh
mvn -f backend/pom.xml verify
# Once 07 is present and can open runs:
docker compose up --build
# Component tests (requires the test override, secrets, Bruno CLI):
docker compose -f docker-compose.yml -f docker-compose.test.yml up -d --build
scripts/test-env.sh
cd docs/tests/bruno
npx @usebruno/cli@4.3.0 run services/players -r --env component --cacert ../../../.test-secrets/ca.crt
npx @usebruno/cli@4.3.0 run services/monsters -r --env component --cacert ../../../.test-secrets/ca.crt
npx @usebruno/cli@4.3.0 run services/teams -r --env component --cacert ../../../.test-secrets/ca.crt
```

Some of the `players` Bruno tests require 07's `/internal/runs`; the `monsters` and `teams` component setups can grant starters directly with an authenticated service token, without requiring an active encounter. Environment variables and CA setup are described in `docs/plan/10-testing.md`.

## Scope boundaries / next tasks

- The P2 optional profile editing (US-04), monster nickname/customisation (US-13) and P2 security hardening are **not** implemented here.
- The P1 `encounters` implementation is still missing. Actual play, battle rewards at the end of a battle, and end-to-end persistence through history cannot be fully tested until 07 and 08 are integrated.
- Bruno source collections were added; JSON export of those collections is still needed for final submission (per TEST-1). Review or extend the component tests after live integration.
- The original seed JSON and generated monster sprites were **not modified**.
