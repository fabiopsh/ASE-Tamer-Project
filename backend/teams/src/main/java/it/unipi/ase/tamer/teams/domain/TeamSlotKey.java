package it.unipi.ase.tamer.teams.domain;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/** JPA composite primary key (team_id, position). */
public class TeamSlotKey implements Serializable {
    public UUID teamId;
    public short position;

    public TeamSlotKey() {
    }

    public TeamSlotKey(UUID teamId, short position) {
        this.teamId = teamId;
        this.position = position;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof TeamSlotKey key
                && Objects.equals(teamId, key.teamId) && position == key.position;
    }

    @Override
    public int hashCode() {
        return Objects.hash(teamId, position);
    }
}
