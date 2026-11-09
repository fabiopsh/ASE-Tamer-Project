package it.unipi.ase.tamer.encounters.service;

import com.fasterxml.jackson.databind.JsonNode;
import it.unipi.ase.tamer.encounters.engine.Battle;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;

/**
 * A battle in memory (R19): the engine aggregate plus what the end of the battle needs. Every action takes
 * {@link #lock()}, so two concurrent actions on the same battle cannot interleave.
 */
public final class BattleSession {

    private final UUID encounterId;
    private final UUID playerId;
    private final UUID runId;
    private final UUID teamId;
    private final Battle battle;
    private final JsonNode wildInfo;
    private final JsonNode teamSnapshot;
    private final Instant startedAt;
    private final ReentrantLock lock = new ReentrantLock();
    private volatile Instant lastActionAt;
    private volatile boolean closed;

    /**
     * @param wildInfo     {@code {monster_id, name, level, rarity, element}} (history {@code wild})
     * @param teamSnapshot history {@code team_snapshot}
     */
    public BattleSession(UUID encounterId, UUID playerId, UUID runId, UUID teamId, Battle battle, JsonNode wildInfo,
                         JsonNode teamSnapshot, Instant startedAt) {
        this.encounterId = Objects.requireNonNull(encounterId);
        this.playerId = Objects.requireNonNull(playerId);
        this.runId = Objects.requireNonNull(runId);
        this.teamId = teamId;
        this.battle = Objects.requireNonNull(battle);
        this.wildInfo = wildInfo;
        this.teamSnapshot = teamSnapshot;
        this.startedAt = Objects.requireNonNull(startedAt);
        this.lastActionAt = startedAt;
    }

    public UUID encounterId() {
        return encounterId;
    }

    public UUID playerId() {
        return playerId;
    }

    public UUID runId() {
        return runId;
    }

    public UUID teamId() {
        return teamId;
    }

    public Battle battle() {
        return battle;
    }

    public JsonNode wildInfo() {
        return wildInfo;
    }

    public JsonNode teamSnapshot() {
        return teamSnapshot;
    }

    public Instant startedAt() {
        return startedAt;
    }

    public ReentrantLock lock() {
        return lock;
    }

    public Instant lastActionAt() {
        return lastActionAt;
    }

    public void touch(Instant now) {
        lastActionAt = now;
    }

    /** Closed sessions (finished, expired, abandoned) accept no more actions. */
    public boolean closed() {
        return closed;
    }

    public void close() {
        closed = true;
    }
}
