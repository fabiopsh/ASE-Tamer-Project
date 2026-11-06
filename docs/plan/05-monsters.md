# 05 — monsters

Catalogue (100 monsters, 28 abilities, 7 elements), sprites, and the per-run personal collection with levels and XP. User stories: **US-09, US-10, US-11, US-12** (P1), **US-13** (P2, optional).

The effective-stat and levelling formulas live **only here**. There is **no public endpoint that adds monsters or XP** (anti-fraud, US-12).

Calls: nobody. Called by: `players`, `teams`, `encounters`.

## Seed data (already in `backend/monsters/seed/`)

| File | Shape |
|---|---|
| `monsters.json` | list of `{monster_id, name, element, rarity, base_hp, base_attack, base_defense, base_speed, growth_hp, growth_attack, growth_defense, growth_speed, ability_id, description, image_url}` |
| `abilities.json` | list of `{ability_id, name, scope, element, effect_type, effect_value, trigger, description}` |
| `elements.json` | `{cycle: [...], strong_against: {el: el}, weak_against: {el: el}}` |
| `images/monsters/NNN.png` | 64×64 sprites |

Distribution: 60 common, 30 rare, 10 legendary; elements `dream, echo, time, gravity, silence, memory, chaos`.

## API

| Method | Path | Auth | Phase | Description |
|---|---|---|---|---|
| GET | `/api/monsters` | player | P1 | `?element=&rarity=&ids=&page=&size=` paginated catalogue |
| GET | `/api/monsters/{id}` | player | P1 | Species detail with ability |
| GET | `/api/abilities` | player | P1 | All abilities |
| GET | `/api/abilities/{id}` | player | P1 | One ability |
| GET | `/api/elements` | player | P1 | Element cycle |
| GET | `/images/monsters/{NNN}.png` | **public** | P1 | Sprite (P-16) |
| GET | `/api/collection` | player | P1 | Active instances of the current run; `?run_id=` archived instances of a past own run; paginated |
| GET | `/api/collection/{owned_id}` | player | P1 | Instance detail |
| GET | `/internal/monsters` | service | P1 | `?rarity=&element=&exclude=` → `[{monster_id, element, rarity}]` (no pagination) |
| GET | `/internal/monsters/{id}/stats` | service | P1 | `?level=` → effective stats + element + ability of a species at a level |
| POST | `/internal/collections/{pid}/starters` | service | P1 | `{run_id, monster_ids}` idempotent on `run_id` |
| POST | `/internal/collections/{pid}/monsters` | service | P1 | `{run_id, monster_id, level, encounter_id, source}` idempotent on `encounter_id` |
| POST | `/internal/collections/{pid}/xp` | service | P1 | `{encounter_id, owned_ids, xp}` idempotent on `encounter_id` |
| GET | `/internal/collections/{pid}/instances` | service | P1 | `?ids=` → instances with effective stats, element, ability, `run_id`, `status` |
| POST | `/internal/collections/{pid}/archive` | service | P1 | `{run_id}` → all instances of that run become `archived`, idempotent |

### Payloads

Catalogue item:

```json
{
  "monster_id": 1, "name": "Snoozle", "element": "dream", "rarity": "common",
  "image_url": "/images/monsters/001.png"
}
```

Species detail adds `description`, `base_stats {hp, attack, defense, speed}`, `growth {hp, attack, defense, speed}`, `ability {ability_id, name, description, effect_type, effect_value, trigger}`.

Owned instance:

```json
{
  "owned_id": "…", "monster_id": 1, "name": "Snoozle", "element": "dream", "rarity": "common",
  "image_url": "/images/monsters/001.png",
  "level": 4, "xp": 12, "xp_to_next_level": 60,
  "stats": { "hp": 42, "attack": 42, "defense": 42, "speed": 42 },
  "ability": { "ability_id": 3, "name": "Second Wind", "description": "…" },
  "source": "starter", "encounter_id": null, "run_id": "…", "status": "active",
  "obtained_at": "2026-11-06T10:05:00Z"
}
```

## Formulas

```
stat_effective(stat, level) = floor(base_stat + growth_stat × (level − 1))
xp_to_next_level(level)     = 15 × level          (0 at MAX_LEVEL)
MAX_LEVEL                   = 50
```

`xp` stores the XP accumulated **towards the next level**. Applying `gained` XP:

```
xp += gained
while level < 50 and xp >= 15 × level:
    xp -= 15 × level
    level += 1
if level == 50: xp = 0
```

## Database (`monsters-db`)

```sql
-- V1__init.sql
CREATE TABLE elements (
    element         varchar(10) PRIMARY KEY,
    strong_against  varchar(10) NOT NULL,
    weak_against    varchar(10) NOT NULL,
    cycle_position  smallint NOT NULL
);
CREATE TABLE abilities (
    ability_id    integer PRIMARY KEY,
    name          varchar(40) NOT NULL,
    scope         varchar(10) NOT NULL,          -- generic | element | legendary
    element       varchar(10) REFERENCES elements(element),
    effect_type   varchar(30) NOT NULL,
    effect_value  integer NOT NULL,
    trigger       varchar(20) NOT NULL,
    description   text NOT NULL
);
CREATE TABLE monsters (
    monster_id      integer PRIMARY KEY,
    name            varchar(40) NOT NULL UNIQUE,
    element         varchar(10) NOT NULL REFERENCES elements(element),
    rarity          varchar(10) NOT NULL,        -- common | rare | legendary
    base_hp integer NOT NULL, base_attack integer NOT NULL,
    base_defense integer NOT NULL, base_speed integer NOT NULL,
    growth_hp numeric(4,2) NOT NULL, growth_attack numeric(4,2) NOT NULL,
    growth_defense numeric(4,2) NOT NULL, growth_speed numeric(4,2) NOT NULL,
    ability_id      integer NOT NULL REFERENCES abilities(ability_id),
    description     text NOT NULL,
    image_url       varchar(60) NOT NULL
);
CREATE INDEX ix_monsters_element_rarity ON monsters (element, rarity);

CREATE TABLE owned_monsters (
    owned_id      uuid PRIMARY KEY,
    player_id     uuid NOT NULL,
    run_id        uuid NOT NULL,
    monster_id    integer NOT NULL REFERENCES monsters(monster_id),
    level         smallint NOT NULL CHECK (level BETWEEN 1 AND 50),
    xp            integer NOT NULL DEFAULT 0 CHECK (xp >= 0),
    source        varchar(10) NOT NULL,          -- starter | encounter | boss
    encounter_id  uuid,
    status        varchar(10) NOT NULL DEFAULT 'active',   -- active | archived
    obtained_at   timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX ix_owned_player_status ON owned_monsters (player_id, status);
CREATE INDEX ix_owned_player_run ON owned_monsters (player_id, run_id);
CREATE UNIQUE INDEX ux_owned_encounter ON owned_monsters (encounter_id) WHERE encounter_id IS NOT NULL;

CREATE TABLE xp_grants (                        -- idempotency of /xp
    encounter_id  uuid PRIMARY KEY,
    player_id     uuid NOT NULL,
    xp            integer NOT NULL,
    granted_at    timestamptz NOT NULL DEFAULT now()
);
-- V2__seed_catalogue: Java migration loading the seed JSON (INFRA-3)
```

---

## MO-1 — Public catalogue, abilities, elements, sprites · P1

**Depends on:** INFRA-3, INFRA-7

- [ ] Migrations V1 + V2 (Java seed migration reading `seed/*.json` from the classpath; resources copied from `backend/monsters/seed` by `maven-resources-plugin`).
- [ ] Entities `Element`, `Ability`, `Monster`.
- [ ] `GET /api/monsters`:
  - `element`, `rarity` validated against the enums → 400 `VALIDATION_ERROR`;
  - `ids` comma-separated, max 50 ids, integers only;
  - ordered by `monster_id`; pagination format from conventions.
- [ ] `GET /api/monsters/{id}` → 404 if missing.
- [ ] `GET /api/abilities`, `/api/abilities/{id}`, `/api/elements`.
- [ ] Sprites: copy `seed/images/monsters/*.png` to `META-INF/resources/images/monsters/` at build time; mark `/images/*` as permitted without auth (`quarkus.http.auth.permission.images.paths=/images/*`, `policy=permit`); `Cache-Control: public, max-age=86400`.
- [ ] Tests: pagination bounds, each filter, invalid filter, `ids`, 404, sprite 200 and 404, catalogue without token → 401.
- [ ] AC US-10, US-11.

**Done when:** `?element=dream&rarity=common` returns only those 10 monsters; sprites load through the gateway in the browser.

---

## MO-2 — Collection, levelling and internal endpoints · P1

**Depends on:** MO-1

- [ ] Entity `OwnedMonster`, `XpGrant`; `StatCalculator` and `Leveling` (pure classes, unit-tested).
- [ ] `GET /api/collection`:
  - no `run_id` → `status = active` for the current player;
  - `run_id` → instances of that run for the current player (empty page if none: a foreign run id leaks nothing);
  - ordered by `obtained_at`, paginated.
- [ ] `GET /api/collection/{owned_id}` → 404 if missing or not owned.
- [ ] Internal endpoints (all `@RolesAllowed("service")`):
  - `GET /internal/monsters?rarity=&element=&exclude=1,2` — used by `players` (commons) and `encounters` (wild pool, excluding boss species).
  - `GET /internal/monsters/{id}/stats?level=` — level 1–50 else 400.
  - `POST …/starters`: if instances with `run_id` and `source = starter` exist → return them (`200`); else create 3 at level 1 (`201`). Validate 3 distinct common ids.
  - `POST …/monsters`: `encounter_id` already present → return the existing instance (`200`); else create (`201`) with `level` capped at 50, `source` `encounter` or `boss`.
  - `POST …/xp`: `encounter_id` already in `xp_grants` → return current instances unchanged; else in one transaction insert the grant and apply `Leveling` to each `owned_id` (must belong to `pid` and be `active`, else 400). Response: `[{owned_id, level_before, level_after, xp}]`.
  - `GET …/instances?ids=`: returns only instances of `pid`; missing ids are simply absent (caller decides).
  - `POST …/archive`: `UPDATE … SET status='archived' WHERE player_id=:pid AND run_id=:run AND status='active'`; returns count; repeatable.
- [ ] Tests:
  - stat formula and levelling (multi-level jump, cap at 50);
  - reward called twice with the same `encounter_id` → one instance;
  - XP called twice → applied once;
  - instance of another player → 404 (public) / absent (internal);
  - archived instances not in `GET /api/collection` but visible with `run_id`;
  - internal endpoints with player token → 403.
- [ ] AC US-09, US-12.

**Done when:** a victory adds exactly one instance even if the call is retried; players see only their monsters.

## Acceptance criteria (draft)

> **US-12 — Get a monster earned in an encounter** · Dependencies: US-19, US-09
> - **Given** I won an encounter against a level 7 wild monster, **when** the encounter ends, **then** that species appears in my collection at level 7, linked to the encounter id.
> - **Given** any state, **when** I look for a public endpoint to add monsters or XP, **then** none exists (only `/internal`, unreachable through the gateway).
> - **Given** the reward call is repeated by the backend, **when** I view my collection, **then** the monster appears once.

## P2

- [ ] US-13 (optional, "improve or customize my monsters"): levelling already covers "improve"; optionally add nickname per instance (`PATCH /api/collection/{owned_id} {nickname}`), document as implemented green story.
