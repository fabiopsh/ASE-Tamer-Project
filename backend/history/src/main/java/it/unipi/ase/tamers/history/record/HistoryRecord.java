package it.unipi.ase.tamers.history.record;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "history_records", uniqueConstraints = {
        @UniqueConstraint(name = "uk_history_player_sequence", columnNames = {"player_id", "sequence"})})
public class HistoryRecord extends PanacheEntityBase {
    @Id
    @Column(name = "record_id")
    public UUID recordId;

    @Column(name = "player_id", nullable = false)
    public UUID playerId;

    @Column(name = "run_id", nullable = false, unique = true)
    public UUID runId;

    @Column(nullable = false)
    public long sequence;

    @Column(name = "occurred_at", nullable = false)
    public Instant occurredAt;

    @Column(nullable = false, length = 16)
    public String outcome;

    @Column(nullable = false, length = 16)
    public String kind;

    @Column(nullable = false)
    public int stage;

    /** The EncounterResult JSON exactly as received, serialized once. */
    @Column(columnDefinition = "text", nullable = false)
    public String payload;

    @Column(name = "prev_hash", nullable = false, length = 64)
    public String prevHash;

    @Column(nullable = false, length = 64)
    public String hash;
}
