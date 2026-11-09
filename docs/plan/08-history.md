# 08 — history

Permanent record of every finished encounter, written only by `encounters`. User stories: **US-23, US-24** (P1), **US-25** (P2).

Calls: nobody. Called by: `encounters`.

## API

| Method | Path | Auth | Phase | Description |
|---|---|---|---|---|
| GET | `/api/history` | player | P1 | `?run_id=&outcome=&page=&size=` summaries, newest first (US-23) |
| GET | `/api/history/{encounter_id}` | player | P1 | Full record with turn log (US-24); not owned → 404 |
| GET | `/api/history/verify` | player | P2 | Verifies the player's hash chain → `{valid, records, first_invalid?}` (US-25) |
| POST | `/internal/history` | service | P1 | Store a finished encounter; idempotent on `encounter_id` |

### Record (`POST /internal/history` body and `GET /api/history/{id}` response)

```json
{
  "encounter_id": "…",
  "player_id": "…",
  "run_id": "…",
  "template": { "template_id": 12, "name": "…", "stage": 1, "element": "echo", "is_boss": false },
  "wild": { "monster_id": 51, "name": "…", "level": 2, "rarity": "common", "element": "echo" },
  "team_snapshot": [ { "position": 1, "owned_id": "…", "monster_id": 3, "name": "…", "level": 3, "stats": { … } } ],
  "outcome": "victory",
  "reward": { "owned_id": "…", "monster_id": 51, "level": 2 },
  "xp_gained": 20,
  "turns": [ { "turn": 1, "events": [ … ] } ],
  "rng_seed": "-4512…",
  "engine_version": 1,
  "started_at": "…",
  "ended_at": "…"
}
```

`outcome` ∈ `victory | defeat | fled | expired | interrupted | abandoned`. Summaries (`GET /api/history`) omit `team_snapshot` details and `turns`. `player_id` is never accepted from the client on public endpoints; `GET` responses may omit it. `rng_seed` is a string (64-bit values exceed JavaScript's safe integer range).

## Database (`history-db`)

```sql
-- V1__init.sql
CREATE TABLE encounter_records (
    encounter_id    uuid PRIMARY KEY,
    player_id       uuid NOT NULL,
    run_id          uuid NOT NULL,
    template_id     integer NOT NULL,
    stage           smallint NOT NULL,
    is_boss         boolean NOT NULL,
    wild            jsonb NOT NULL,
    team_snapshot   jsonb NOT NULL,
    outcome         varchar(12) NOT NULL,
    reward          jsonb,
    xp_gained       integer NOT NULL DEFAULT 0,
    turns           jsonb NOT NULL,
    rng_seed        bigint,
    engine_version  smallint NOT NULL,
    started_at      timestamptz NOT NULL,
    ended_at        timestamptz NOT NULL,
    recorded_at     timestamptz NOT NULL DEFAULT now(),
    seq             bigint,                 -- P2: position in the player's chain
    prev_hash       char(64),               -- P2
    hash            char(64)                -- P2
);
CREATE INDEX ix_records_player_time ON encounter_records (player_id, ended_at DESC);
CREATE INDEX ix_records_player_run ON encounter_records (player_id, run_id);
```

---

## HI-1 — History service · P1

**Depends on:** INFRA-3, INFRA-7

- [ ] Migration, entity `EncounterRecord` (JSON columns mapped with `@JdbcTypeCode(SqlTypes.JSON)`).
- [ ] `POST /internal/history`: validate the body (outcome enum, sizes, `turns` max 500 rounds); `INSERT … ON CONFLICT (encounter_id) DO NOTHING`; `201` if inserted, `200` if it already existed.
- [ ] `GET /api/history`: filter by player (token), optional `run_id`, `outcome` (enum, else 400); pagination; summaries.
- [ ] `GET /api/history/{encounter_id}`: scoped by player → 404 otherwise.
- [ ] Tests: idempotent insert, filters, pagination, foreign record → 404, internal with player token → 403, invalid outcome → 400.
- [ ] Bruno component collection `history`.
- [ ] AC US-23, US-24.

## Acceptance criteria (draft)

> **US-23 — View my previous encounters** · Dependencies: US-19
> - **Given** I finished 3 encounters, **when** I list my history, **then** I see 3 summaries, newest first, with outcome, wild monster and date.
> - **Given** another player finished encounters, **when** I list my history, **then** I see none of theirs.
> - **Given** history across two runs, **when** I filter by `run_id`, **then** I see only that run's encounters.

> **US-24 — View the details of a previous encounter** · Dependencies: US-23
> - **Given** a finished encounter, **when** I open it, **then** I see team, wild monster, outcome, reward, XP and every round's events.
> - **Given** an encounter of another player, **when** I open its id, **then** I receive 404.

---

## HI-2 — Integrity: append-only hash chain (US-25) · P2

**Depends on:** HI-1

**Description.** Saved history cannot be modified or deleted silently, and tampering is detectable.

- [ ] **Append-only in the database:** migration `V2__append_only.sql` with a trigger that raises an exception on `UPDATE` and `DELETE` of `encounter_records`. Optionally: Flyway connects as the owner role, the application as a role with only `SELECT, INSERT` (needs a second DB user from INFRA-4).
- [ ] **Hash chain per player:**
  - table `chain_heads(player_id PK, seq bigint, last_hash char(64))`;
  - on insert, in one transaction: `SELECT … FOR UPDATE` on the player's head (insert if absent, `prev_hash = 64 × '0'`), `seq = head.seq + 1`, `hash = SHA-256(prev_hash ‖ canonical_json(record without seq/prev_hash/hash))`, insert record, update head.
  - canonical JSON: keys sorted, no whitespace, UTC timestamps with fixed precision (document the exact rules in code).
- [ ] `GET /api/history/verify`: recompute the chain in `seq` order; report the first broken link.
- [ ] Replay check (optional): an internal tool re-runs a battle from `rng_seed`, `engine_version`, team snapshot and actions, and compares the log.
- [ ] Tests: update/delete rejected by DB; chain valid after N inserts; manual tampering (via a superuser in the test) detected; concurrent inserts for the same player keep a consistent chain.
- [ ] AC US-25 (together with backups: document `pg_dump` of the volumes in the README as "protection from loss").
