package it.unipi.ase.tamers.encounters.encounter;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "encounter_offers", indexes = @Index(name = "idx_offer_player_status", columnList = "player_id,status"))
public class EncounterOffer extends PanacheEntityBase {
    public static final String OPEN = "open";
    public static final String COMPLETED = "completed";
    public static final String WILD = "wild";
    public static final String GUARDIAN = "guardian";

    @Id
    @Column(name = "encounter_id")
    public UUID encounterId;

    @Column(name = "player_id", nullable = false)
    public UUID playerId;

    @Column(nullable = false, length = 10)
    public String kind;

    @Column(nullable = false)
    public int stage;

    @Column(nullable = false, length = 10)
    public String status;

    @Column(name = "monster_id", nullable = false)
    public int monsterId;

    @Column(nullable = false)
    public int level;

    @Column(name = "created_at", nullable = false)
    public Instant createdAt;

    public boolean isGuardian() {
        return GUARDIAN.equals(kind);
    }

    public boolean isOpen() {
        return OPEN.equals(status);
    }

    public static List<EncounterOffer> openFor(UUID playerId) {
        return list("playerId = ?1 and status = ?2 order by createdAt", playerId, OPEN);
    }

    /** Most recent wild offers of the player on a stage (any status), used to avoid repeating species. */
    public static List<EncounterOffer> recentWild(UUID playerId, int stage, int limit) {
        return find("playerId = ?1 and stage = ?2 and kind = ?3 order by createdAt desc", playerId, stage, WILD)
                .page(0, limit).list();
    }
}
