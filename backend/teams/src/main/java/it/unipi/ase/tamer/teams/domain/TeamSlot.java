package it.unipi.ase.tamer.teams.domain;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "team_slots")
@IdClass(TeamSlotKey.class)
public class TeamSlot extends PanacheEntityBase {
    @Id
    @Column(name = "team_id")
    public UUID teamId;
    @Id
    @Column(name = "position")
    public short position;
    @Column(name = "owned_id", nullable = false)
    public UUID ownedId;
}
