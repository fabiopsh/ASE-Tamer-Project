CREATE TABLE players (
    player_id       uuid PRIMARY KEY,
    display_name    varchar(30) NOT NULL,
    avatar          varchar(30),
    current_run_id  uuid NOT NULL,
    starter_pool    integer[] NOT NULL,
    starters_chosen boolean NOT NULL DEFAULT false,
    created_at      timestamptz NOT NULL DEFAULT now(),
    version         bigint NOT NULL DEFAULT 0
);
