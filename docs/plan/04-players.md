# 04 — players

Game profile, starter pool and the pointer to the current run. User stories: **US-03, US-06, US-07** (P1), **US-04** (P2, optional).

`players` is the **owner of `current_run_id`**: it generates the run id and passes it to `monsters` and `encounters`.

Calls: `monsters` (common pool, starters), `encounters` (open run). Called by: `auth` (create profile), `encounters` (new run).

## API

| Method | Path | Auth | Phase | Description |
|---|---|---|---|---|
| GET | `/api/players/me` | player | P1 | Profile |
| GET | `/api/players/me/starters` | player | P1 | Starter pool of the current run |
| POST | `/api/players/me/starters` | player | P1 | Choose 3 starters, opens the run |
| PATCH | `/api/players/me` | player | P2 | `{display_name?, avatar?}` (US-04, optional) |
| POST | `/internal/players` | service | P1 | Create profile, idempotent on `player_id` |
| POST | `/internal/players/{pid}/new-run` | service | P1 | Start a new run after loss/abandon/win, idempotent |

### Payloads

`GET /api/players/me` → `200`

```json
{
  "player_id": "7b0c…",
  "display_name": "Tamer-7b0c1a",
  "avatar": null,
  "current_run_id": "e41f…",
  "starters_chosen": false,
  "created_at": "2026-11-06T10:00:00Z"
}
```

`GET /api/players/me/starters` → `200 {"run_id": "e41f…", "pool": [3, 7, 12, …], "chosen": false}` (10 `monster_id`s). The client fetches details with `GET /api/monsters?ids=…` (documented client-side assumption).

`POST /api/players/me/starters` body `{"monster_ids": [3, 7, 21]}` → `201`

```json
{ "run_id": "e41f…", "starters": [ { "owned_id": "…", "monster_id": 3, "level": 1 }, … ] }
```

| Error | When |
|---|---|
| `400 INVALID_STARTERS` | Not exactly 3, duplicates, or an id not in the pool |
| `409 STARTERS_ALREADY_CHOSEN` | `starters_chosen = true` |
| `503 DEPENDENCY_UNAVAILABLE` | `monsters` or `encounters` failed; safe to retry |

`POST /internal/players` body `{"player_id": "…"}` → `201` (created) or `200` (already existed, same body).

`POST /internal/players/{pid}/new-run` body `{"ended_run_id": "…"}` → `200 {"current_run_id": "…"}`. If `ended_run_id` is not the current run, nothing changes and the current id is returned.

## Database (`players-db`)

```sql
-- V1__init.sql
CREATE TABLE players (
    player_id        uuid PRIMARY KEY,
    display_name     varchar(30) NOT NULL,
    avatar           varchar(30),                    -- P2: one of a fixed list of avatar keys
    current_run_id   uuid NOT NULL,
    starter_pool     integer[] NOT NULL,             -- 10 monster_id
    starters_chosen  boolean NOT NULL DEFAULT false,
    created_at       timestamptz NOT NULL DEFAULT now(),
    version          bigint NOT NULL DEFAULT 0       -- optimistic locking
);
```

---

## PL-1 — Players service · P1

**Depends on:** INFRA-3, INFRA-7, MO-2 (`GET /internal/monsters`, `POST /internal/collections/{pid}/starters`), EN-1 (`POST /internal/runs`). Mock the clients until they exist.

**Steps**

- [ ] Migration, entity `Player` (`@Version`), REST clients `MonstersClient`, `EncountersClient`.
- [ ] `PoolGenerator`: `GET /internal/monsters?rarity=common` → list of ids; pick 10 distinct with `SecureRandom` (shuffle + take 10). Unit-testable with an injected `Random`.
- [ ] `POST /internal/players`:
  - existing `player_id` → return `200` with the existing profile (idempotent);
  - else `current_run_id = UUID.randomUUID()`, pool from `PoolGenerator`, `display_name = "Tamer-" + first 6 hex chars of player_id` (P-19), save, `201`.
  - If `monsters` is unavailable → `503` (auth rolls back the account).
- [ ] `GET /api/players/me` (profile of `CurrentPlayer`; missing → 404).
- [ ] `GET /api/players/me/starters`.
- [ ] `POST /api/players/me/starters` — order matters, every remote step is idempotent:
  1. load player with optimistic lock; `starters_chosen` → 409;
  2. validate the selection against the pool → 400;
  3. `POST /internal/collections/{pid}/starters {run_id, monster_ids}` (idempotent on `run_id`);
  4. `POST /internal/runs {run_id, player_id}` (idempotent on `run_id`);
  5. set `starters_chosen = true`, commit; return the starters from step 3.
  On failure at 3 or 4 → `503`; a retry by the player repeats the steps without duplicates. Concurrent double submit: the `@Version` conflict makes the second request fail with `409 STARTERS_ALREADY_CHOSEN` (map `OptimisticLockException`).
- [ ] `POST /internal/players/{pid}/new-run`: if `ended_run_id == current_run_id` → new run id, new pool, `starters_chosen = false`; else no-op. Return current id.
- [ ] Reference implementation of the INFRA-7 commons lives here (copy to other services).
- [ ] Tests (`@QuarkusTest`, clients mocked):
  - create profile twice → one row, same pool;
  - pool has 10 distinct commons and is stable across `GET`s;
  - starters: valid; 2 ids; duplicate ids; id outside pool; second choice → 409; monsters failure → 503 and `starters_chosen` still false; retry after failure succeeds;
  - new-run with current id → new id and new pool; with old id → unchanged.
- [ ] Bruno component collection `players` (see [10-testing.md](10-testing.md)).
- [ ] Acceptance criteria US-03, US-06, US-07 (note: progress is kept until the run is lost or abandoned; past runs remain visible — roguelike rule R14).

**Done when**

- After registration the player always sees the same pool; after choosing, the 3 monsters are in the collection and the run at stage 1 exists; a second choice → 409.

## Acceptance criteria (draft)

> **US-06 — Receive an initial collection** · Dependencies: US-01, US-10
> - **Given** a new account, **when** I get my starter pool twice, **then** I see the same 10 common monsters.
> - **Given** I have not chosen starters, **when** I choose 3 distinct monsters from my pool, **then** they appear in my collection at level 1 with source `starter` and my run starts at stage 1.
> - **Given** I have not chosen starters, **when** I send 2 monsters, a duplicate, or a monster outside my pool, **then** I receive 400 and nothing changes.
> - **Given** I already chose starters, **when** I choose again, **then** I receive 409.

> **US-03 — Find my progress when I return** · Dependencies: US-01, US-02
> - **Given** I have an active run with monsters and history, **when** I log in again (also after a backend restart), **then** collection, levels, stage and history are unchanged.
> - **Given** my run was lost, **when** I log in, **then** I can start a new run and still consult past runs and history.

## P2

- [ ] US-04 (optional): `PATCH /api/players/me` with `display_name` (3–30 chars, `^[A-Za-z0-9 _-]+$`) and `avatar` (one of a fixed list); tests and AC.
