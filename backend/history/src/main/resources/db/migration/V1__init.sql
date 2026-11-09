-- History of finished encounters (docs/plan/08-history.md). Written only by the encounters service.
-- template_name / template_element are stored next to template_id so that the summary and the
-- detail can return the whole `template` object without calling another service.
-- seq / prev_hash / hash are reserved for the P2 hash chain (HI-2) and stay NULL in P1.
CREATE TABLE encounter_records (
    encounter_id     uuid PRIMARY KEY,
    player_id        uuid NOT NULL,
    run_id           uuid NOT NULL,
    template_id      integer NOT NULL,
    template_name    varchar(60) NOT NULL,
    template_element varchar(20) NOT NULL,
    stage            smallint NOT NULL,
    is_boss          boolean NOT NULL,
    wild             jsonb NOT NULL,
    team_snapshot    jsonb NOT NULL,
    outcome          varchar(12) NOT NULL,
    reward           jsonb,
    xp_gained        integer NOT NULL DEFAULT 0,
    turns            jsonb NOT NULL,
    rng_seed         bigint,
    engine_version   smallint NOT NULL,
    started_at       timestamptz NOT NULL,
    ended_at         timestamptz NOT NULL,
    recorded_at      timestamptz NOT NULL DEFAULT now(),
    seq              bigint,                 -- P2: position in the player's chain
    prev_hash        char(64),               -- P2
    hash             char(64)                -- P2
);
CREATE INDEX ix_records_player_time ON encounter_records (player_id, ended_at DESC);
CREATE INDEX ix_records_player_run ON encounter_records (player_id, run_id);
