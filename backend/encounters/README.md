# encounters

Stage templates, roguelike runs, the battle engine, the battle API and what happens at the end of a battle or of a run.
The outcome of a battle is computed **only here**: the client sends actions (`attack`, `switch`, `flee`), never results.

- **Entities:** `EncounterTemplate` (seed, 32 rows), `Run`, `BattleCompletion` (transactional outbox); battles in progress live in memory (`BattleRegistry`)
- **User stories:** US-17, US-18, US-19, US-20, US-21, US-22 (and the trigger side of US-12)
- **Plan:** [docs/plan/07-encounters.md](../../docs/plan/07-encounters.md)
- **Calls:** `monsters` (wild pool, stats, reward, XP, archive), `teams` (team for a battle, reset), `players` (new run), `history` (record). **Called by:** `players` (`POST /internal/runs` when the starters are chosen).

## Endpoints

| Method | Path | Role | Description |
|---|---|---|---|
| GET | `/api/encounters` | player | Templates available in the active run (normal templates of stages ≤ current, boss of the current stage after 2 wins). No active run → `409 NO_ACTIVE_RUN` |
| GET | `/api/encounters/{template_id}` | player | One available template; not available → `404` |
| GET | `/api/runs?page=&size=` | player | My runs, newest first (`size` 1–50, default 20) |
| GET | `/api/runs/current` | player | Active run, or the latest `won` run not yet reset; none → `404 NO_ACTIVE_RUN` (choose starters) |
| GET | `/api/runs/{run_id}` | player | One of my runs; otherwise `404` |
| POST | `/api/runs/current/abandon` | player | Abandon (US-21): battle closed as `abandoned`, run `abandoned`, reset → `200` run |
| POST | `/api/runs` | player | After a `won` run: reset → `202 {"status":"choose_starters"}`; otherwise `409 NEW_RUN_NOT_ALLOWED` |
| POST | `/api/battles` | player | `{template_id, team_id}` → `201` battle state |
| GET | `/api/battles/current` | player | Battle in progress; none → `404` |
| POST | `/api/battles/current/actions` | player | `{type: attack\|switch\|flee, slot?}` → state after the round; at the end `status = finished` and `result` |
| POST | `/internal/runs` | service | `{run_id, player_id}` → run at stage 1: `201` created, `200` repeat, `409 RUN_CONFLICT` (another active run) |

Errors: `409 NO_ACTIVE_RUN`, `409 BATTLE_IN_PROGRESS`, `409 RESET_PENDING`, `409 TEAM_INVALID` (from teams, or team of another run), `404 NOT_FOUND`, `400 INVALID_ACTION` (switch to the active / a KO'd member or a slot outside the team, `slot` on a non-switch, `switch` without `slot`), `400 VALIDATION_ERROR` (unknown field such as `outcome` or `hp`, bad `type`, `slot` outside 1–3), `503 DEPENDENCY_UNAVAILABLE`.

### Shapes (snake_case)

Battle state (`POST /api/battles`, `GET /api/battles/current`, every action):

```json
{
  "encounter_id": "uuid", "status": "in_progress", "turn": 1,
  "template": { "template_id": 11, "name": "Drowsy Meadow", "stage": 1, "element": "dream", "is_boss": false },
  "wild": { "monster_id": 3, "name": "…", "element": "dream", "rarity": "common", "level": 2, "hp": 30, "max_hp": 45,
            "image_url": "/images/monsters/003.png", "ability": { "name": "…", "description": "…" } },
  "team": [ { "position": 1, "owned_id": "uuid", "monster_id": 7, "name": "…", "level": 3, "element": "silence",
              "hp": 25, "max_hp": 30, "active": true, "knocked_out": false, "image_url": "/images/monsters/007.png" } ],
  "last_round": { "turn": 1, "events": [
      { "type": "attack", "side": "team", "attacker": "…", "target": "…", "damage": 9, "effectiveness": "neutral", "dodged": false, "abilities": [] },
      { "type": "switch", "side": "team", "from": 1, "to": 2 }, { "type": "auto_switch", "to": 2 },
      { "type": "knockout", "side": "team", "position": 1 }, { "type": "knockout", "side": "wild" },
      { "type": "flee", "success": false }, { "type": "end", "outcome": "victory" } ] },
  "result": null
}
```

`wild.ability` is `null` when the species has none; `last_round` is `null` before the first action; event fields that do not belong to the type are omitted.
At the end (`status = "finished"`):

```json
"result": {
  "outcome": "victory | defeat | fled",
  "reward": { "owned_id": "uuid or null", "monster_id": 3, "level": 2, "pending": false },
  "xp_gained": 20,
  "level_ups": [ { "owned_id": "uuid", "level_before": 2, "level_after": 3 } ],
  "run": { "stage": 1, "wins_in_stage": 1, "boss_available": false, "status": "active | won | lost" }
}
```

`reward` is `null` unless victory; `pending: true` (with `owned_id: null`) when monsters could not be reached, the reward is retried automatically. `level_ups` lists only members that levelled up.

Run: `{run_id, status (active|won|lost|abandoned), stage, wins_in_stage, boss_available, bosses_defeated [stages], battle_in_progress, reset_pending, started_at, ended_at (null while active)}`.
Encounter: `{template_id, name, stage, element, is_boss, boss_monster_id (null for normal), level_min, level_max, rarity_weights {common, rare, legendary}}`.

## How it works

- **Start:** lazy recovery → active run, no battle → template available → `GET /internal/teams/{team_id}?player_id=` → seed from `SeedSource` (`SecureRandom`) → wild: boss species, or `WildSelector` over `GET /internal/monsters?element=&exclude=<8 boss ids>` → `GET /internal/monsters/{id}/stats?level=` → `active_battle_*` committed on the run → battle registered in memory.
- **End of battle (outbox):** one transaction updates the run (victory: `wins_in_stage + 1`, boss → next stage or `won` after stage 8; defeat: `lost` + `reset_pending`), clears `active_battle_*` and inserts `battle_completions` with the reward, XP and history bodies. After commit: reward (`POST /internal/collections/{pid}/monsters`, `source boss|encounter`), XP (`POST /internal/collections/{pid}/xp` for every team member), history (`POST /internal/history`, after the reward so it holds the `owned_id`). All idempotent on `encounter_id`; a failure leaves the flag false.
- **Run reset** (`RunResetService`, after defeat, abandon, `POST /api/runs`): pending completions of the run, `POST /internal/collections/{pid}/archive`, `POST /internal/teams/{pid}/reset`, `POST /internal/players/{pid}/new-run`, then `reset_done`. Re-runnable from the start.
- **Lazy recovery** at the start of every run/battle request: resume `reset_pending` (mutating requests get `409 RESET_PENDING` if it still fails), retry pending completions, close as `interrupted` a battle recorded on the run but missing from memory (restart), close as `expired` a battle idle for more than `tamer.battle.idle-timeout` (30 min).
- **Concurrency:** every mutating request of a player runs under a per-player lock; each action also takes the battle's `ReentrantLock`. `runs` has `@Version` and a unique index on the active run.
- **4xx from a dependency** (`RemoteClientError`) is not retried and not counted by the circuit breaker; in the outbox it is logged and the step skipped (it would fail forever).

Seed: [`seed/encounter_templates.json`](seed/encounter_templates.json) (copied into the classpath by the `copy-seed` execution of the pom, loaded by the Java migration `db/migration/V2__seed_templates`).

## Run and test

```bash
mvn -pl encounters test           # engine unit tests + @QuarkusTest with mocked clients (Docker for Dev Services)
mvn -pl encounters quarkus:dev    # port 8085, plain HTTP, throw-away PostgreSQL
```

Component tests (Bruno, test compose stack, encounters on port 9005, needs auth/players/monsters/teams too): `docs/tests/bruno/services/encounters`.
