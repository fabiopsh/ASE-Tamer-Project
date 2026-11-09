# 07 — encounters

Stage templates, roguelike runs, the battle engine, the battle API and the orchestration of what happens at the end of a battle or a run. User stories: **US-17, US-18, US-19, US-20, US-21, US-22** and the trigger side of **US-12**.

The outcome is computed **only here**. The client sends actions (`attack`, `switch`, `flee`), never results.

Calls: `teams` (team for battle), `monsters` (wild pool, stats, reward, XP, archive), `history` (record), `players` (new run). Called by: `players` (open run).

## Game rules recap (from the architecture, with this plan's decisions)

- A run starts at stage 1 when the player picks the starters. 8 stages; beating the stage-8 boss wins the run.
- Each stage has 3 normal templates (replayable) and 1 boss. The boss unlocks after **2 wins** in the current stage and can be beaten **once per run**; beating it moves to the next stage.
- Templates of already-cleared stages stay playable (XP farming).
- Battle: relay 1v1, team order; wild monster keeps its damage; actions `attack`, `switch`, `flee` (P-10).
- Victory → the wild monster joins the collection **at its level**; XP to the **whole team**.
- Defeat (whole team KO) → run `lost` + reset. Abandon → run `abandoned` + same reset. Won run → the player starts a new run when they want (same reset).
- One active battle per player; expires after 30 minutes of inactivity; battles live in memory (R19).

## Stage design (EN-1 seed, initial values — tune with the simulation)

Template ids: `stage × 10 + k`, `k = 1..3` normal, `k = 4` boss. Normal wild level: random in `[3s − 2, 3s]`. Boss level: `3s + 2`.

| Stage | Normal levels | Normal template elements | Rarity weights (common / rare / legendary) | Boss (id, element, rarity) | Boss level |
|---|---|---|---|---|---|
| 1 | 1–3 | dream, echo, silence | 100 / 0 / 0 | Loomwidow (31, memory, rare) | 5 |
| 2 | 4–6 | memory, time, gravity | 90 / 10 / 0 | Frostveil (89, silence, rare) | 8 |
| 3 | 7–9 | dream, echo, silence | 80 / 20 / 0 | Nebulynx (74, gravity, rare) | 11 |
| 4 | 10–12 | memory, time, gravity | 70 / 30 / 0 | Choraleth (62, echo, legendary) | 14 |
| 5 | 13–15 | dream, silence, chaos | 60 / 40 / 0 | Chronarch (47, time, legendary) | 17 |
| 6 | 16–18 | echo, memory, gravity | 50 / 45 / 5 | Morpheon (16, dream, legendary) | 20 |
| 7 | 19–21 | time, silence, chaos | 40 / 50 / 10 | Quietus (92, silence, legendary) | 23 |
| 8 | 22–24 | dream, gravity, chaos | 30 / 55 / 15 | **Omnivoid** (100, chaos, legendary) | 26 |

**Wild selection for a normal template** (P-21):

1. Candidates = catalogue monsters with the template's element, **excluding the 8 boss species**.
2. Keep only rarities that have at least one candidate; renormalise their weights (e.g. no dream legendary is left after excluding Morpheon, so its weight is dropped).
3. Draw the rarity, then a species uniformly, then the level uniformly — all with the battle's seeded RNG.

## Formulas (initial values)

```
max_hp(team member)  = 2 × hp + 10
max_hp(wild)         = round((2 × hp + 10) × 1.5)      boss: × 2.5
damage               = max(1, round(atk² / (atk + def) × 0.6 × elem × var × boost × reduction))
elem                 = 1.5 if attacker strong against defender, 0.67 if weak, else 1
var                  = uniform [0.9, 1.1]
boost                = Π (1 + v/100) of active damage_boost effects of the attacker
reduction            = Π (1 − v/100) of active damage_reduction effects of the defender
atk                  = attacker attack × Π (1 − v/100) of active weaken effects on it
speed_eff            = speed × Π (1 + v/100) of active speed_boost effects
p_flee               = min(0.95, speed_eff(active) / (speed_eff(active) + speed_eff(wild)) + 0.25)
xp_victory           = round(10 × wild_level × {common 1, rare 1.5, legendary 2})   to every team member
```

## Abilities

Effects are data (`effect_type`, `effect_value`, `trigger`); logic lives only in the engine.

**Triggers**

| Trigger | Meaning |
|---|---|
| `always` | While the monster is on the field |
| `first_turn` | During the monster's first round on the field in this encounter (for `dodge`: only the first attack it receives) |
| `hp_below_50` / `hp_above_50` | Current HP < 50% / > 50% of max at the moment of use |
| `vs_faster` | Opponent's `speed_eff` > own |
| `on_hit` | When it receives damage (P2) |
| `on_attack` | When it deals damage (P2) |
| `once` | Once per encounter (P2) |

**Effects by phase** (P-09)

| Effect | Phase | Behaviour | Abilities |
|---|---|---|---|
| `damage_boost` | P1 | Multiply outgoing damage by `1 + v/100` | 4, 10, 15, 18, 21 |
| `damage_reduction` | P1 | Multiply incoming damage by `1 − v/100` | 1, 5, 14, 25 |
| `speed_boost` | P1 | Multiply speed by `1 + v/100` | 6, 13 |
| `dodge` | P1 | `v`% chance to take no damage from an attack | 2, 9, 17 |
| `weaken` | P1 | Opponent's attack × `1 − v/100` while this monster is on the field (and the trigger holds) | 16, 26 |
| `ignore_disadvantage` | P1 | When attacking, `elem` 0.67 becomes 1 | 19 |
| `heal` | P2 | First time HP drops below 50%: restore `v`% of max HP | 3, 12 |
| `reflect` | P2 | Attacker takes `max(1, floor(v% × damage taken))` | 7, 11, 23 |
| `stun` | P2 | After dealing damage, `v`% chance the target skips its next action | 8, 22 |
| `revive` | P2 | Once per encounter, when KO: return with `v`% HP | 24 |
| `random_boost` | P2 | Each action, a random stat among attack/defense/speed × `1 + v/100` for that action | 20 |
| `copy_ability` | P2 | On its first round, copy the opponent's ability for the rest of the encounter | 27 |
| `invert_advantage` | P2 | While on the field, `elem` 1.5 ↔ 0.67 for both sides | 28 |

In P1, P2 effects are **no-ops**; the UI still shows the ability text. Document this in the report for the prototype.

## Round resolution

`POST /api/battles/current/actions` resolves one full round:

1. **Player's switch or flee first.**
   - `switch {slot}`: target must be alive and not active, else `400 INVALID_ACTION`. The new monster does not attack this round.
   - `flee`: roll `p_flee`; success → battle ends `fled`; failure → continue.
2. **Attacks** in order of `speed_eff` (tie → RNG). If the player chose `attack`, both sides attack; otherwise only the wild attacks.
3. Each attack: dodge roll (only if a dodge effect is active) → `var` roll → damage → HP update → KO check.
4. KO of the active team monster: it does not act later this round; at the end of the round the **first alive member by position** becomes active (`auto_switch` event).
5. End: wild HP 0 → `victory`; all team KO → `defeat`.

**Determinism.** One `java.util.SplittableRandom(seed)` per battle, used also for the wild selection. The order of random calls is part of the specification (wild rarity → species → level → per round: tie-break → per attack: dodge roll if any → var). Changing it changes replays, so the engine has a constant `ENGINE_VERSION` stored in every history record.

## API

| Method | Path | Auth | Phase | Description |
|---|---|---|---|---|
| GET | `/api/encounters` | player | P1 | Available templates for the active run (US-17) |
| GET | `/api/encounters/{template_id}` | player | P1 | Characteristics; not available → 404 |
| GET | `/api/runs` | player | P1 | Runs of the player, newest first, paginated |
| GET | `/api/runs/current` | player | P1 | Active run (or the latest `won` run awaiting a new run); none → `404 NO_ACTIVE_RUN` |
| GET | `/api/runs/{run_id}` | player | P1 | Run detail (own only) |
| POST | `/api/runs/current/abandon` | player | P1 | Abandon (US-21): closes any battle as `abandoned`, run → `abandoned`, reset |
| POST | `/api/runs` | player | P1 | After a `won` run: reset and wait for new starters → `202` |
| POST | `/api/battles` | player | P1 | `{template_id, team_id}` → `201` battle state (US-18) |
| GET | `/api/battles/current` | player | P1 | Battle state; none → 404 |
| POST | `/api/battles/current/actions` | player | P1 | `{type: attack|switch|flee, slot?}` → round result (US-19, US-21, US-22) |
| POST | `/internal/runs` | service | P1 | `{run_id, player_id}` → open run at stage 1; idempotent on `run_id` |

### Battle state

```json
{
  "encounter_id": "…",
  "status": "in_progress",
  "turn": 3,
  "template": { "template_id": 12, "name": "…", "stage": 1, "element": "echo", "is_boss": false },
  "wild": { "monster_id": 51, "name": "…", "element": "echo", "rarity": "common", "level": 2,
            "hp": 30, "max_hp": 45, "image_url": "/images/monsters/051.png",
            "ability": { "name": "…", "description": "…" } },
  "team": [
    { "position": 1, "owned_id": "…", "monster_id": 3, "name": "…", "level": 3, "element": "dream",
      "hp": 0, "max_hp": 32, "active": false, "knocked_out": true },
    { "position": 2, "owned_id": "…", "monster_id": 7, "name": "…", "level": 2, "element": "silence",
      "hp": 25, "max_hp": 30, "active": true, "knocked_out": false }
  ],
  "last_round": {
    "turn": 3,
    "events": [
      { "type": "attack", "side": "wild", "attacker": "…", "target": "…", "damage": 9,
        "effectiveness": "neutral", "dodged": false, "abilities": [] },
      { "type": "knockout", "side": "team", "position": 1 },
      { "type": "auto_switch", "to": 2 }
    ]
  },
  "result": null
}
```

When the battle ends, `status` is `finished` and `result` is filled (this is the US-20 detail, also stored in history):

```json
"result": {
  "outcome": "victory",
  "reward": { "owned_id": "…", "monster_id": 51, "level": 2, "pending": false },
  "xp_gained": 20,
  "level_ups": [ { "owned_id": "…", "level_before": 2, "level_after": 3 } ],
  "run": { "stage": 1, "wins_in_stage": 2, "boss_available": true, "status": "active" }
}
```

Event types: `attack`, `switch`, `auto_switch`, `knockout`, `flee`, `ability` (P2 for effects like heal/revive/stun), `end`.

Errors: `409 NO_ACTIVE_RUN`, `409 BATTLE_IN_PROGRESS`, `409 RESET_PENDING`, `409 TEAM_INVALID` (from teams), `404 NOT_FOUND` (template not available, no battle), `400 INVALID_ACTION`.

## Database (`encounters-db`)

```sql
-- V1__init.sql
CREATE TABLE encounter_templates (
    template_id      integer PRIMARY KEY,
    stage            smallint NOT NULL CHECK (stage BETWEEN 1 AND 8),
    name             varchar(40) NOT NULL,
    element          varchar(10) NOT NULL,
    is_boss          boolean NOT NULL,
    boss_monster_id  integer,
    level_min        smallint NOT NULL,
    level_max        smallint NOT NULL,
    rarity_weights   jsonb NOT NULL            -- {"common":80,"rare":20,"legendary":0}
);

CREATE TABLE runs (
    run_id                     uuid PRIMARY KEY,
    player_id                  uuid NOT NULL,
    status                     varchar(10) NOT NULL,   -- active | won | lost | abandoned
    stage                      smallint NOT NULL DEFAULT 1,
    wins_in_stage              smallint NOT NULL DEFAULT 0,
    bosses_defeated            smallint[] NOT NULL DEFAULT '{}',
    active_battle_id           uuid,
    active_battle_template_id  integer,
    active_battle_team_id      uuid,
    active_battle_started_at   timestamptz,
    reset_pending              boolean NOT NULL DEFAULT false,
    reset_done                 boolean NOT NULL DEFAULT false,
    started_at                 timestamptz NOT NULL DEFAULT now(),
    ended_at                   timestamptz,
    version                    bigint NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX ux_runs_one_active ON runs (player_id) WHERE status = 'active';
CREATE INDEX ix_runs_player ON runs (player_id, started_at DESC);

CREATE TABLE battle_completions (             -- transactional outbox for end-of-battle side effects
    encounter_id    uuid PRIMARY KEY,
    player_id       uuid NOT NULL,
    run_id          uuid NOT NULL,
    payload         jsonb NOT NULL,           -- reward, xp, history record
    reward_owned_id uuid,
    reward_done     boolean NOT NULL DEFAULT false,
    xp_done         boolean NOT NULL DEFAULT false,
    history_done    boolean NOT NULL DEFAULT false,
    created_at      timestamptz NOT NULL DEFAULT now()
);
-- V2__seed_templates: Java migration loading seed/encounter_templates.json
```

### End-of-battle consistency (transactional outbox)

The run update (local truth) and the list of remote side effects are committed **in one local transaction**; the remote calls happen after commit and are all idempotent on `encounter_id`.

```mermaid
sequenceDiagram
    participant E as encounters
    participant DB as encounters-db
    participant M as monsters
    participant H as history
    E->>DB: BEGIN; update run; insert battle_completions; clear active battle; COMMIT
    E->>M: POST /internal/collections/{pid}/monsters (victory)
    E->>DB: reward_done, reward_owned_id
    E->>M: POST /internal/collections/{pid}/xp (victory)
    E->>DB: xp_done
    E->>H: POST /internal/history
    E->>DB: history_done
    Note over E: any failure leaves flags false; retried lazily (P1) and by a scheduler (P2)
```

If the reward is still pending when the response is built, `result.reward.pending = true` and `owned_id = null`; the collection shows it as soon as the retry succeeds.

### Run reset

`RunResetService.reset(run)` after defeat, abandon, or `POST /api/runs` on a won run:

1. In one transaction: set status (`lost`/`abandoned`, or keep `won`), `ended_at`, `reset_pending = true`.
2. Process outstanding `battle_completions` of the run (history must be written before archiving).
3. `POST /internal/collections/{pid}/archive {run_id}`.
4. `POST /internal/teams/{pid}/reset {run_id}`.
5. `POST /internal/players/{pid}/new-run {ended_run_id}`.
6. `reset_pending = false`, `reset_done = true`.

All steps are idempotent, so the whole routine can be re-run from the start. Failure → `reset_pending` stays true, the response still reports the run outcome, and the routine is resumed **at the start of the player's next request** to `encounters` (P1) and by a scheduler (P2).

### Lazy recovery (P-20)

At the start of every run/battle request of a player:

- `reset_pending` → resume the reset; if it still fails → `409 RESET_PENDING` on mutating requests.
- pending `battle_completions` → retry them.
- `active_battle_id` set but not in memory (service restarted) → close as `interrupted` (minimal history record from the `active_battle_*` columns; no reward; run continues).
- battle in memory idle > 30 min → close as `expired` (no reward; run continues).

---

## EN-1 — Stage templates and runs · P1

**Depends on:** INFRA-3, INFRA-7, MO-2

- [ ] `backend/encounters/seed/encounter_templates.json` with the 32 templates of the stage table; give each normal template an evocative English name.
- [ ] Migrations V1, V2 (seed loader like in monsters).
- [ ] Entities `EncounterTemplate`, `Run`.
- [ ] `POST /internal/runs`: existing `run_id` → `200`; else insert stage 1 → `201`; another active run for the player → `409` (unique index).
- [ ] `AvailabilityService.available(run)`: non-boss templates with `stage ≤ run.stage`; plus boss of `run.stage` if `wins_in_stage ≥ 2` and `run.stage ∉ bosses_defeated`.
- [ ] `GET /api/encounters`, `/api/encounters/{id}`, `/api/runs`, `/api/runs/current`, `/api/runs/{id}`.
- [ ] Tests: availability at stage 1 / after 2 wins / after boss, other player's run → 404, idempotent `/internal/runs`.
- [ ] AC US-17.

## EN-2 — Battle engine · P1 (start on day one)

**Depends on:** — (pure Java, package `it.unipi.ase.tamer.encounters.engine`, no Quarkus/DB imports)

- [ ] Immutable inputs: `Combatant(id, monsterId, name, element, rarity, level, hp, attack, defense, speed, ability)`, `ElementChart`, `EncounterSpec(template, wild, isBoss)`.
- [ ] Mutable `Battle` aggregate with `apply(Action) → RoundResult`; state as in the response above; `ENGINE_VERSION = 1`.
- [ ] `WildSelector` (rarity renormalisation, boss exclusion, level) using the battle RNG.
- [ ] `DamageCalculator`, `TurnOrder`, `FleeCalculator`, `XpCalculator`.
- [ ] `AbilityEngine` with a handler per `effect_type`; P1 handlers: `damage_boost`, `damage_reduction`, `speed_boost`, `dodge`, `weaken`, `ignore_disadvantage`; others registered as no-op.
- [ ] Unit tests with fixed seeds:
  - victory possible and **defeat possible** (requirement §4);
  - strong/weak/neutral multipliers, `ignore_disadvantage`;
  - speed order and tie-break;
  - auto-switch to first alive; switch to KO'd/active member rejected;
  - flee success and failure (wild attacks on failure);
  - each P1 ability with its trigger;
  - same seed + same actions ⇒ identical log;
  - wild selection never returns a boss species; renormalisation when a rarity is empty.
- [ ] Optional `BalanceSimulation` (main class in test sources): for each stage, simulate 1000 battles with a team of 3 commons at the stage's level range; print win rate per template and boss. Target ≈ 70–85% normal, 40–60% boss. Record the tuned constants in this file.

## EN-3 — Battle API, end of battle, abandon, new run · P1

**Depends on:** EN-1, EN-2, TE-1, MO-2, HI-1

- [ ] `BattleRegistry`: `ConcurrentHashMap<UUID playerId, BattleSession>`; `BattleSession` holds the `Battle`, a `ReentrantLock`, `lastActionAt`. Every action takes the lock (two concurrent actions cannot interleave).
- [ ] `POST /api/battles`:
  1. lazy recovery; run active and not `reset_pending`; no active battle (`409 BATTLE_IN_PROGRESS`);
  2. template available (else 404);
  3. `GET /internal/teams/{team_id}?player_id=` (409 if invalid, 404 if not found);
  4. `seed = SecureRandom.nextLong()`; wild: boss species or `WildSelector` over `GET /internal/monsters?element=&exclude=<boss ids>`; stats from `GET /internal/monsters/{id}/stats?level=`;
  5. set `active_battle_*` on the run (commit), register session, return state `201`.
- [ ] `GET /api/battles/current`.
- [ ] `POST /api/battles/current/actions`: validate DTO (`type` enum, `slot` 1–3 only for switch) → engine → if finished: `BattleCompletionService.complete(session)`:
  - victory: run `wins_in_stage + 1`; if boss: add stage to `bosses_defeated`, stage 8 → status `won` + `ended_at`, else `stage + 1`, `wins_in_stage = 0`;
  - defeat: run `lost`;
  - fled: run unchanged;
  - write `battle_completions` with reward (victory: `source = boss|encounter`, level = wild level), XP for every team `owned_id`, and the full history record; clear `active_battle_*`; commit; process the outbox; for defeat run the reset.
- [ ] `POST /api/runs/current/abandon`: close any battle as `abandoned` (history record, no reward) → reset with status `abandoned`.
- [ ] `POST /api/runs`: only if the latest run is `won` and not yet reset → reset → `202 {"status": "choose_starters"}`; otherwise `409`.
- [ ] Lazy recovery hook (`@ServerRequestFilter` or explicit call in each resource method).
- [ ] Tests (`@QuarkusTest`, clients mocked, deterministic seed injected via a test `SeedSource` bean): start / start twice → 409 / invalid team → 409 / unavailable template → 404; action valid and invalid; victory → reward + XP + history calls with the same `encounter_id`; monsters down at completion → `reward.pending = true`, retried on next request; defeat → archive, teams reset, new-run called; abandon; interrupted recovery after clearing the registry; expiry with a mocked clock.
- [ ] Bruno component collection `encounters` (battle tests assert on structure and invariants, not exact damage).
- [ ] AC US-18, US-19, US-20, US-21, US-22.

## Acceptance criteria (draft)

> **US-19 — Encounter resolved by the game rules** · Dependencies: US-18
> - **Given** a battle in progress, **when** I send `attack`, **then** the response contains the round events computed by the server and the updated HP.
> - **Given** a battle in progress, **when** the wild monster's HP reaches 0, **then** the outcome is `victory`; **when** all my team is KO, **then** the outcome is `defeat`.
> - **Given** any state, **when** I try to send an outcome or HP values, **then** the request is rejected (400) because only actions are accepted.

> **US-21 — Leave or escape an encounter** · Dependencies: US-18
> - **Given** a battle in progress, **when** I flee successfully, **then** the battle ends as `fled`, I get no reward and my run continues.
> - **Given** a battle in progress, **when** the flee fails, **then** the wild monster attacks and the battle continues.
> - **Given** an active run, **when** I abandon it, **then** the run is `abandoned`, my collection is archived and I must choose new starters.

## P2

- **EN-4** — P2 ability effects (`heal`, `reflect`, `stun`, `revive`, `random_boost`, `copy_ability`, `invert_advantage`) with unit tests; bump `ENGINE_VERSION` to 2.
- **EN-5** — `quarkus-scheduler` job every minute: expire idle battles, retry pending `battle_completions`, resume `reset_pending` runs.
- **EN-6** — Balance pass with the simulation including abilities; update constants and this document.
- **EN-7** — Remove the players ↔ encounters call cycle if the smells analysis flags it (option: `encounters` creates the run lazily by reading `GET /internal/players/{pid}` instead of `players` calling `/internal/runs`).

## Tuned constants

Measured on 2026-11-09 with `BalanceSimulation` (engine v1, P2 abilities no-op, player always attacks, 1000 battles per template, levels of the stage's normal range).

| Constant | Plan | Tuned | Why |
|---|---|---|---|
| Boss HP multiplier | × 2.5 | **× 1.5** (same as normal wild) | With × 2.5 every boss from stage 3 on was won 0–15% of the time; the boss is already harder through level (+2) and rarity. |

Every other formula is unchanged. Win rates with the tuned value (%):

| Stage | Commons-only team (plan's sim): normal templates / boss | Team drawn with the stage's rarity weights: normal / boss |
|---|---|---|
| 1 | 96.8, 98.6, 97.5 / 45.7 | 97.4, 98.6, 97.6 / 48.9 |
| 2 | 90.8, 93.8, 87.3 / 66.6 | 91.7, 94.6, 88.5 / 76.0 |
| 3 | 88.0, 85.6, 83.7 / 12.3 | 92.8, 88.4, 91.8 / 37.4 |
| 4 | 77.3, 84.1, 74.6 / 7.5 | 87.2, 89.6, 83.8 / 38.5 |
| 5 | 79.1, 74.4, 76.9 / 9.3 | 90.5, 90.3, 87.4 / 49.5 |
| 6 | 68.7, 71.8, 60.0 / 0.5 | 87.0, 87.1, 81.8 / 23.3 |
| 7 | 69.6, 70.4, 66.5 / 0.0 | 90.3, 94.4, 87.1 / 2.2 |
| 8 | 70.7, 48.3, 56.5 / 0.0 | 95.9, 79.9, 89.0 / 57.0 |

Open points, not decided here: with a commons-only team the late bosses stay out of reach whatever the multiplier, because legendary stats grow faster than common ones; a team that captured rares (the `mixed` mode, `BalanceSimulation <seedDir> 1000 mixed`) is closer to the target. Quietus (stage 7, Absolute Quiet `weaken` 35 `always`) and Morpheon (stage 6) remain outliers; the levers are their boss level in the template seed or the ability value in the monsters seed.
