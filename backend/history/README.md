# history

Permanent record of every finished encounter. It is written only by `encounters` and read by the player.
It calls no other service: it only validates JWTs and uses its own database (`history-db`).

- **Entity:** `EncounterRecord` (table `encounter_records`, migration `V1__init.sql`)
- **User stories:** US-23, US-24 (P1), US-25 (P2, not implemented yet)
- **Plan:** [docs/plan/08-history.md](../../docs/plan/08-history.md)

## Endpoints

| Method | Path | Role | Description |
|---|---|---|---|
| POST | `/internal/history` | `service` | Store a finished encounter. Idempotent on `encounter_id`: `201` when stored, `200` when it already existed (nothing changes, the first write wins). Body: the record below; response: summary of the stored record |
| GET | `/api/history?run_id=&outcome=&page=&size=` | `player` | Summaries of my encounters, newest first (`ended_at` descending). `size` default 20, max 50. Bad `outcome`, `run_id`, `page` or `size` give `400 VALIDATION_ERROR` |
| GET | `/api/history/{encounter_id}` | `player` | Full record with team snapshot and turn log. Missing or not mine gives `404 NOT_FOUND` |

`player_id` comes from the token on the public endpoints and is never returned. A player token on `/internal/*` gives `403`, no token gives `401`.

### Record accepted by `POST /internal/history`

All names are `snake_case`; unknown fields are rejected with `400`.

```json
{
  "encounter_id": "uuid", "player_id": "uuid", "run_id": "uuid",
  "template": { "template_id": 12, "name": "Mossy Hollow", "stage": 1, "element": "echo", "is_boss": false },
  "wild": { "monster_id": 51, "name": "Mossling", "level": 2, "rarity": "common", "element": "echo" },
  "team_snapshot": [ { "any": "json object, at most 6 entries; optional (default [])" } ],
  "outcome": "victory | defeat | fled | expired | interrupted | abandoned",
  "reward": { "any": "json, optional (default none)" },
  "xp_gained": 20,
  "turns": [ { "turn": 1, "events": [ "any json; at most 500 rounds; optional (default [])" ] } ],
  "rng_seed": "-4512345678901234567",
  "engine_version": 1,
  "started_at": "2026-11-06T10:00:00Z", "ended_at": "2026-11-06T10:05:00Z"
}
```

Required: `encounter_id`, `player_id`, `run_id`, `template` (all five fields), `wild` (all five fields), `outcome`, `engine_version`, `started_at`, `ended_at` (not before `started_at`). Optional: `team_snapshot`, `reward`, `xp_gained` (default 0), `turns`, `rng_seed` (a decimal string that fits a signed 64-bit integer; stored as `bigint`).
`team_snapshot`, `reward` and `turns` are stored exactly as sent and returned untouched (no renaming of keys).
The summary in the list contains `encounter_id, run_id, outcome, template, wild, reward, xp_gained, started_at, ended_at`.

## Run and test

```bash
mvn -pl history test             # @QuarkusTest suite, needs Docker (Dev Services PostgreSQL)
mvn -pl history quarkus:dev      # port 8086, plain HTTP, throw-away PostgreSQL
```

`quarkus.hibernate-orm.mapping.format.global=ignore` is set on purpose: the JSON columns use Hibernate's own Jackson mapper, not the REST one (which applies `snake_case`).
Component tests (Bruno, needs the test compose stack on port 9006): `docs/tests/bruno/services/history`.
