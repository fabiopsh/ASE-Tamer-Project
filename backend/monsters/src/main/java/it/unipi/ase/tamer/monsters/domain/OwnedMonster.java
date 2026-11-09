package it.unipi.ase.tamer.monsters.domain;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "owned_monsters")
public class OwnedMonster extends PanacheEntityBase {
    @Id
    @Column(name = "owned_id")
    public UUID ownedId;
    @Column(name = "player_id", nullable = false)
    public UUID playerId;
    @Column(name = "run_id", nullable = false)
    public UUID runId;
    @Column(name = "monster_id", nullable = false)
    public int monsterId;
    public short level;
    public int xp;
    public String source;
    @Column(name = "encounter_id")
    public UUID encounterId;
    public String status;
    @Column(name = "obtained_at")
    public Instant obtainedAt;
}
