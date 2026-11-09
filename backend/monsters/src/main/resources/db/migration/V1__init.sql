CREATE TABLE elements (
    element         varchar(10) PRIMARY KEY,
    strong_against  varchar(10) NOT NULL,
    weak_against    varchar(10) NOT NULL,
    cycle_position  smallint NOT NULL
);
CREATE TABLE abilities (
    ability_id    integer PRIMARY KEY,
    name          varchar(40) NOT NULL,
    scope         varchar(10) NOT NULL,
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
    rarity          varchar(10) NOT NULL,
    base_hp         integer NOT NULL,
    base_attack     integer NOT NULL,
    base_defense    integer NOT NULL,
    base_speed      integer NOT NULL,
    growth_hp       numeric(4,2) NOT NULL,
    growth_attack   numeric(4,2) NOT NULL,
    growth_defense  numeric(4,2) NOT NULL,
    growth_speed    numeric(4,2) NOT NULL,
    ability_id      integer NOT NULL REFERENCES abilities(ability_id),
    description     text NOT NULL,
    image_url       varchar(60) NOT NULL
);
CREATE INDEX ix_monsters_element_rarity ON monsters(element, rarity);
CREATE TABLE owned_monsters (
    owned_id     uuid PRIMARY KEY,
    player_id    uuid NOT NULL,
    run_id       uuid NOT NULL,
    monster_id   integer NOT NULL REFERENCES monsters(monster_id),
    level        smallint NOT NULL CHECK (level BETWEEN 1 AND 50),
    xp           integer NOT NULL DEFAULT 0 CHECK (xp >= 0),
    source       varchar(10) NOT NULL,
    encounter_id uuid,
    status       varchar(10) NOT NULL DEFAULT 'active',
    obtained_at  timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX ix_owned_player_status ON owned_monsters(player_id, status);
CREATE INDEX ix_owned_player_run ON owned_monsters(player_id, run_id);
CREATE UNIQUE INDEX ux_owned_encounter ON owned_monsters(encounter_id) WHERE encounter_id IS NOT NULL;
CREATE TABLE xp_grants (
    encounter_id uuid PRIMARY KEY,
    player_id    uuid NOT NULL,
    xp           integer NOT NULL,
    granted_at   timestamptz NOT NULL DEFAULT now()
);
