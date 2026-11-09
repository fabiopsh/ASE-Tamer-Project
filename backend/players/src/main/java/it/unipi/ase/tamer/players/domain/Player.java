package it.unipi.ase.tamer.players.domain;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

/** Persistent profile; credentials are owned by auth, never duplicated here. */
@Entity
@Table(name = "players")
public class Player extends PanacheEntityBase {

    @Id
    @Column(name = "player_id")
    public UUID playerId;

    @Column(name = "display_name", nullable = false, length = 30)
    public String displayName;

    @Column(name = "avatar", length = 30)
    public String avatar;

    @Column(name = "current_run_id", nullable = false)
    public UUID currentRunId;

    @Column(name = "starter_pool", nullable = false, columnDefinition = "integer[]")
    public Integer[] starterPool;

    @Column(name = "starters_chosen", nullable = false)
    public boolean startersChosen;

    @Column(name = "created_at", nullable = false, updatable = false)
    public Instant createdAt;

    @Version
    @Column(name = "version", nullable = false)
    public long version;
}
