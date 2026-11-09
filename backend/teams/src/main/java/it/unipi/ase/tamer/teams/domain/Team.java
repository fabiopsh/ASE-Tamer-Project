package it.unipi.ase.tamer.teams.domain;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "teams")
public class Team extends PanacheEntityBase {
    @Id
    @Column(name = "team_id")
    public UUID teamId;
    @Column(name = "player_id", nullable = false)
    public UUID playerId;
    @Column(name = "run_id", nullable = false)
    public UUID runId;
    @Column(name = "name", nullable = false, length = 30)
    public String name;
    @Column(name = "created_at", nullable = false)
    public Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    public Instant updatedAt;
}
