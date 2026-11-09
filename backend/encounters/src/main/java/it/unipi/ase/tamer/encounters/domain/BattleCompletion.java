package it.unipi.ase.tamer.encounters.domain;

import com.fasterxml.jackson.databind.JsonNode;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Transactional outbox row of a finished battle: written in the same transaction as the run update, then
 * processed (reward → XP → history), each call idempotent on {@code encounter_id}.
 *
 * <p>{@code payload}: {@code {"reward": {...} | null, "xp": {...} | null, "history": {...}}}; the reward and XP
 * objects are the request bodies of the monsters internal endpoints, the history object is the body of
 * {@code POST /internal/history} (its {@code reward.owned_id} is filled in when the reward is granted).
 */
@Entity
@Table(name = "battle_completions")
public class BattleCompletion extends PanacheEntityBase {

    @Id
    @Column(name = "encounter_id")
    public UUID encounterId;

    @Column(name = "player_id", nullable = false)
    public UUID playerId;

    @Column(name = "run_id", nullable = false)
    public UUID runId;

    @Column(name = "outcome", nullable = false, length = 12)
    public String outcome;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", nullable = false)
    public JsonNode payload;

    @Column(name = "reward_owned_id")
    public UUID rewardOwnedId;

    @Column(name = "reward_done", nullable = false)
    public boolean rewardDone;

    @Column(name = "xp_done", nullable = false)
    public boolean xpDone;

    @Column(name = "history_done", nullable = false)
    public boolean historyDone;

    @Column(name = "created_at", nullable = false, updatable = false)
    public Instant createdAt;

    public boolean pending() {
        return !(rewardDone && xpDone && historyDone);
    }
}
