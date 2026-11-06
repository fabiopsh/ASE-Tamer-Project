# 06 — teams

Teams built from the player's active monsters. User stories: **US-14, US-15, US-16**.

One validator implements the rules and is used by `validate`, create, update and the internal re-validation before a battle, so they can never disagree.

Calls: `monsters` (`GET /internal/collections/{pid}/instances`). Called by: `encounters`.

## Rules (R8, R9)

| Code | Rule |
|---|---|
| `TEAM_SIZE` | 1 to 3 members |
| `DUPLICATE_MEMBER` | The same `owned_id` appears at most once (several instances of the same species are allowed) |
| `NOT_OWNED` | Every `owned_id` belongs to the player (absent from the internal instances response) |
| `NOT_ACTIVE` | Every member has `status = active` (i.e. belongs to the current run) |
| `MIXED_RUNS` | All members share the same `run_id` (defensive; cannot normally happen) |
| `TEAM_LIMIT` | At most 5 teams per run (create only) |
| `NAME_INVALID` | Name 1–30 chars, `^[A-Za-z0-9 _-]+$` |

Before the starters are chosen the collection is empty, so every team fails with `NOT_OWNED`/`TEAM_SIZE`: this covers "no team before starters" without extra state.

## API

| Method | Path | Auth | Description |
|---|---|---|---|
| GET | `/api/teams` | player | Teams of the player (only current-run teams exist: old ones are deleted by the reset) |
| POST | `/api/teams` | player | `{name, members: [owned_id…]}` → `201` team; invalid → `400 TEAM_INVALID` with `details` |
| GET | `/api/teams/{id}` | player | Team with member summaries |
| PUT | `/api/teams/{id}` | player | `{name, members}` replaces name, members and order (US-15) |
| DELETE | `/api/teams/{id}` | player | `204` |
| POST | `/api/teams/validate` | player | `{members}` → `200 {valid, errors[{code, message, owned_id?}]}` — never saves |
| GET | `/internal/teams/{id}` | service | `?player_id=` → re-validated team with full member stats; invalid → `409 TEAM_INVALID`; missing/not owned → 404 |
| POST | `/internal/teams/{pid}/reset` | service | `{run_id}` → deletes the teams of that run; idempotent |

Team response:

```json
{
  "team_id": "…", "name": "Main", "run_id": "…",
  "members": [
    { "position": 1, "owned_id": "…", "monster_id": 3, "name": "…", "level": 4, "element": "echo", "image_url": "…" }
  ],
  "created_at": "…", "updated_at": "…"
}
```

The internal response adds per member: `stats {hp, attack, defense, speed}`, `ability {ability_id, effect_type, effect_value, trigger}`, `rarity`.

Member summaries come from `monsters` at read time (no denormalised copy), so levels are always current. If `monsters` is unavailable, `GET` returns `503`.

## Database (`teams-db`)

```sql
-- V1__init.sql
CREATE TABLE teams (
    team_id     uuid PRIMARY KEY,
    player_id   uuid NOT NULL,
    run_id      uuid NOT NULL,
    name        varchar(30) NOT NULL,
    created_at  timestamptz NOT NULL DEFAULT now(),
    updated_at  timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX ix_teams_player_run ON teams (player_id, run_id);

CREATE TABLE team_slots (
    team_id   uuid NOT NULL REFERENCES teams(team_id) ON DELETE CASCADE,
    position  smallint NOT NULL CHECK (position BETWEEN 1 AND 3),
    owned_id  uuid NOT NULL,
    PRIMARY KEY (team_id, position),
    UNIQUE (team_id, owned_id)
);
```

---

## TE-1 — Teams service · P1

**Depends on:** INFRA-3, INFRA-7, MO-2

**Steps**

- [ ] Migration, entities `Team`, `TeamSlot`, client `MonstersClient`.
- [ ] `TeamValidator.validate(playerId, name?, members, isCreate)`:
  1. local checks: size, duplicates, name;
  2. one call `GET /internal/collections/{pid}/instances?ids=…`;
  3. ownership, active, same run; `TEAM_LIMIT` on create (count teams of that `run_id`).
  Returns all errors, not only the first. Unit-test the pure part with a fake instance list.
- [ ] `POST /api/teams/validate` returns the validator output with status 200 even when invalid.
- [ ] Create / update: validate → `400 TEAM_INVALID` with `details`; on success store `run_id` from the instances, slots in the given order.
- [ ] `GET`/`PUT`/`DELETE /api/teams/{id}` scoped by player → 404 otherwise.
- [ ] `GET /internal/teams/{id}?player_id=` → re-run the validator (members may have been archived) and return full stats.
- [ ] `POST /internal/teams/{pid}/reset {run_id}` → delete; returns count; repeatable.
- [ ] Tests (`MonstersClient` mocked): every rule valid/invalid, order change via PUT, other player's team → 404, `validate` and `POST` agree on the same input, reset idempotent, internal with player token → 403.
- [ ] Bruno component collection `teams`.
- [ ] AC US-14, US-15, US-16.

**Done when:** a team with a foreign monster or the same instance twice is rejected with a clear code; `validate` and `POST` always give the same verdict.

## Acceptance criteria (draft)

> **US-16 — Know whether my team satisfies the rules** · Dependencies: US-14
> - **Given** I own monsters A, B, C, D, **when** I validate `[A, B, C, D]`, **then** I get `valid: false` with code `TEAM_SIZE` and nothing is saved.
> - **Given** I own monster A, **when** I validate `[A, A]`, **then** I get `DUPLICATE_MEMBER`.
> - **Given** monster X belongs to another player, **when** I validate `[X]`, **then** I get `NOT_OWNED`.
> - **Given** a valid selection, **when** I validate it and then create it, **then** both succeed.
