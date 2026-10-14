package it.unipi.ase.tamers.encounters.encounter;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "encounter_runs")
public class EncounterRun extends PanacheEntityBase {
    @Id
    @Column(name = "run_id")
    public UUID runId;

    @Column(name = "player_id", nullable = false)
    public UUID playerId;

    @Column(name = "encounter_id", nullable = false)
    public UUID encounterId;

    @Column(nullable = false, length = 10)
    public String outcome;

    @Column(nullable = false)
    public long seed;

    /** The full EncounterResult as JSON text. */
    @Column(nullable = false, columnDefinition = "text")
    public String result;

    @Column(name = "occurred_at", nullable = false)
    public Instant occurredAt;
}
