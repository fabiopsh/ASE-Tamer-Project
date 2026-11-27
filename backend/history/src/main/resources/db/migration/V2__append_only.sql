-- Append-only hash chain per player (US-25, HI-2).
CREATE TABLE chain_heads (
    player_id  uuid PRIMARY KEY,
    seq        bigint NOT NULL DEFAULT 0,
    last_hash  char(64) NOT NULL
);

CREATE INDEX ix_records_player_seq ON encounter_records (player_id, seq ASC);

CREATE OR REPLACE FUNCTION prevent_encounter_records_mutation()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    RAISE EXCEPTION 'Encounter records are append-only';
END;
$$;

CREATE TRIGGER trg_encounter_records_append_only
    BEFORE UPDATE OR DELETE ON encounter_records
    FOR EACH ROW
    EXECUTE FUNCTION prevent_encounter_records_mutation();
