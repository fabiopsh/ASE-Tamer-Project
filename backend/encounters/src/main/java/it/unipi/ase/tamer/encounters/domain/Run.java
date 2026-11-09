package it.unipi.ase.tamer.encounters.domain;

import com.fasterxml.jackson.databind.JsonNode;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import it.unipi.ase.tamer.encounters.service.RunRules;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** A roguelike run of a player. At most one {@code active} run per player (partial unique index). */
@Entity
@Table(name = "runs")
public class Run extends PanacheEntityBase {

    public static final String ACTIVE = "active";
    public static final String WON = "won";
    public static final String LOST = "lost";
    public static final String ABANDONED = "abandoned";

    @Id
    @Column(name = "run_id")
    public UUID runId;

    @Column(name = "player_id", nullable = false)
    public UUID playerId;

    @Column(name = "status", nullable = false, length = 10)
    public String status;

    @Column(name = "stage", nullable = false)
    public short stage;

    @Column(name = "wins_in_stage", nullable = false)
    public short winsInStage;

    @Column(name = "bosses_defeated", nullable = false, columnDefinition = "integer[]")
    public Integer[] bossesDefeated = new Integer[0];

    @Column(name = "active_battle_id")
    public UUID activeBattleId;

    @Column(name = "active_battle_template_id")
    public Integer activeBattleTemplateId;

    @Column(name = "active_battle_team_id")
    public UUID activeBattleTeamId;

    @Column(name = "active_battle_started_at")
    public Instant activeBattleStartedAt;

    @Column(name = "active_battle_seed")
    public Long activeBattleSeed;

    /** {@code {monster_id, name, level, rarity, element}} of the wild monster of the active battle. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "active_battle_wild")
    public JsonNode activeBattleWild;

    /** Team snapshot of the active battle (the history {@code team_snapshot}). */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "active_battle_team")
    public JsonNode activeBattleTeam;

    @Column(name = "reset_pending", nullable = false)
    public boolean resetPending;

    @Column(name = "reset_done", nullable = false)
    public boolean resetDone;

    @Column(name = "started_at", nullable = false, updatable = false)
    public Instant startedAt;

    @Column(name = "ended_at")
    public Instant endedAt;

    @Version
    @Column(name = "version", nullable = false)
    public long version;

    public RunRules.State state() {
        return new RunRules.State(status, stage, winsInStage,
                bossesDefeated == null ? List.of() : Arrays.asList(bossesDefeated));
    }

    /** Copies the progression fields of {@code state} into the entity. */
    public void apply(RunRules.State state) {
        status = state.status();
        stage = (short) state.stage();
        winsInStage = (short) state.winsInStage();
        bossesDefeated = state.bossesDefeated().toArray(Integer[]::new);
    }

    public void clearActiveBattle() {
        activeBattleId = null;
        activeBattleTemplateId = null;
        activeBattleTeamId = null;
        activeBattleStartedAt = null;
        activeBattleSeed = null;
        activeBattleWild = null;
        activeBattleTeam = null;
    }
}
