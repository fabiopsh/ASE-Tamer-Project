package it.unipi.ase.tamer.history.domain;

import com.fasterxml.jackson.databind.JsonNode;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * A finished encounter. Rows are inserted with a native {@code ON CONFLICT DO NOTHING} statement
 * (see HistoryService) and never updated: the entity is read-only. The JSON columns hold exactly
 * what the caller sent, as a tree, so naming strategies never touch their content. The P2 chain
 * columns (seq, prev_hash, hash) are not mapped yet.
 */
@Entity
@Immutable
@Table(name = "encounter_records")
public class EncounterRecord extends PanacheEntityBase {

    @Id
    @Column(name = "encounter_id")
    public UUID encounterId;

    @Column(name = "player_id", nullable = false)
    public UUID playerId;

    @Column(name = "run_id", nullable = false)
    public UUID runId;

    @Column(name = "template_id", nullable = false)
    public Integer templateId;

    @Column(name = "template_name", nullable = false)
    public String templateName;

    @Column(name = "template_element", nullable = false)
    public String templateElement;

    @Column(name = "stage", nullable = false)
    public Integer stage;

    @Column(name = "is_boss", nullable = false)
    public Boolean isBoss;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "wild", nullable = false)
    public JsonNode wild;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "team_snapshot", nullable = false)
    public JsonNode teamSnapshot;

    @Column(name = "outcome", nullable = false, length = 12)
    public String outcome;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "reward")
    public JsonNode reward;

    @Column(name = "xp_gained", nullable = false)
    public Integer xpGained;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "turns", nullable = false)
    public JsonNode turns;

    @Column(name = "rng_seed")
    public Long rngSeed;

    @Column(name = "engine_version", nullable = false)
    public Integer engineVersion;

    @Column(name = "started_at", nullable = false)
    public Instant startedAt;

    @Column(name = "ended_at", nullable = false)
    public Instant endedAt;

    @Column(name = "recorded_at", nullable = false, insertable = false, updatable = false)
    public Instant recordedAt;
}
