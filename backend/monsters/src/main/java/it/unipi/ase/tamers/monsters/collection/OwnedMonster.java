package it.unipi.ase.tamers.monsters.collection;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** One instance of a species in a player's collection (duplicates allowed, level and XP per instance). */
@Entity
@Table(name = "owned_monsters", indexes = @Index(name = "idx_owned_player", columnList = "player_id"))
public class OwnedMonster extends PanacheEntityBase {
    @Id
    @Column(name = "owned_id")
    public UUID ownedId;

    @Column(name = "player_id", nullable = false)
    public UUID playerId;

    @Column(name = "monster_id", nullable = false)
    public int monsterId;

    @Column(nullable = false)
    public int level;

    @Column(nullable = false)
    public int xp;

    /** starter | encounter */
    @Column(nullable = false, length = 20)
    public String source;

    @Column(name = "encounter_id")
    public UUID encounterId;

    @Column(name = "obtained_at", nullable = false)
    public Instant obtainedAt;
}
