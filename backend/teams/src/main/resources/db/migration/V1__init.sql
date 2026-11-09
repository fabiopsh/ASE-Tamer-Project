CREATE TABLE teams (
    team_id     uuid PRIMARY KEY,
    player_id   uuid NOT NULL,
    run_id      uuid NOT NULL,
    name        varchar(30) NOT NULL,
    created_at  timestamptz NOT NULL DEFAULT now(),
    updated_at  timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX ix_teams_player_run ON teams(player_id, run_id);
CREATE TABLE team_slots (
    team_id   uuid NOT NULL REFERENCES teams(team_id) ON DELETE CASCADE,
    position  smallint NOT NULL CHECK (position BETWEEN 1 AND 3),
    owned_id  uuid NOT NULL,
    PRIMARY KEY(team_id, position),
    UNIQUE(team_id, owned_id)
);
