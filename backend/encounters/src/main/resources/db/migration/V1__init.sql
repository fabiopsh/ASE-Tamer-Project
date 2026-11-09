-- encounters schema (docs/plan/07-encounters.md, EN-1 / EN-3).
-- Additions to the plan's tables:
--   runs.bosses_defeated is integer[] (mapped as Integer[], same as players.starter_pool);
--   runs.active_battle_seed / active_battle_wild / active_battle_team keep what is needed to write a
--   minimal history record for an `interrupted` battle (service restarted, battle lost from memory);
--   battle_completions.outcome is a copy of the history outcome, for diagnostics.

CREATE TABLE encounter_templates (
    template_id      integer PRIMARY KEY,
    stage            smallint NOT NULL CHECK (stage BETWEEN 1 AND 8),
    name             varchar(40) NOT NULL,
    element          varchar(10) NOT NULL,
    is_boss          boolean NOT NULL,
    boss_monster_id  integer,
    level_min        smallint NOT NULL,
    level_max        smallint NOT NULL,
    rarity_weights   jsonb NOT NULL,           -- {"common":80,"rare":20,"legendary":0}
    CHECK (level_min >= 1 AND level_max >= level_min),
    CHECK (is_boss = (boss_monster_id IS NOT NULL))
);

CREATE TABLE runs (
    run_id                     uuid PRIMARY KEY,
    player_id                  uuid NOT NULL,
    status                     varchar(10) NOT NULL CHECK (status IN ('active', 'won', 'lost', 'abandoned')),
    stage                      smallint NOT NULL DEFAULT 1 CHECK (stage BETWEEN 1 AND 8),
    wins_in_stage              smallint NOT NULL DEFAULT 0,
    bosses_defeated            integer[] NOT NULL DEFAULT '{}',
    active_battle_id           uuid,
    active_battle_template_id  integer,
    active_battle_team_id      uuid,
    active_battle_started_at   timestamptz,
    active_battle_seed         bigint,
    active_battle_wild         jsonb,          -- {monster_id, name, level, rarity, element}
    active_battle_team         jsonb,          -- team snapshot used by the battle
    reset_pending              boolean NOT NULL DEFAULT false,
    reset_done                 boolean NOT NULL DEFAULT false,
    started_at                 timestamptz NOT NULL DEFAULT now(),
    ended_at                   timestamptz,
    version                    bigint NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX ux_runs_one_active ON runs (player_id) WHERE status = 'active';
CREATE INDEX ix_runs_player ON runs (player_id, started_at DESC);
CREATE INDEX ix_runs_reset_pending ON runs (player_id) WHERE reset_pending;

CREATE TABLE battle_completions (             -- transactional outbox for end-of-battle side effects
    encounter_id    uuid PRIMARY KEY,
    player_id       uuid NOT NULL,
    run_id          uuid NOT NULL,
    outcome         varchar(12) NOT NULL,
    payload         jsonb NOT NULL,           -- {reward, xp, history}
    reward_owned_id uuid,
    reward_done     boolean NOT NULL DEFAULT false,
    xp_done         boolean NOT NULL DEFAULT false,
    history_done    boolean NOT NULL DEFAULT false,
    created_at      timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX ix_completions_pending ON battle_completions (player_id, created_at)
    WHERE NOT (reward_done AND xp_done AND history_done);

-- V2__seed_templates: Java migration loading seed/encounter_templates.json
