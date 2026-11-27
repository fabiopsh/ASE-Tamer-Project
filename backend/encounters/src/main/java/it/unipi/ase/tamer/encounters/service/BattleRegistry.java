package it.unipi.ase.tamer.encounters.service;

import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

/**
 * Battles in memory, one per player (R19): {@code ConcurrentHashMap<player_id, BattleSession>}. A restart loses
 * them; the lazy recovery then closes the battle recorded on the run as {@code interrupted}.
 *
 * <p>Also holds a fixed-size striped table of reentrant locks indexed by {@code playerId}: every request that
 * changes the run or the battle of a player (start, action, abandon, new run, recovery, sweep) runs under it,
 * so these flows never interleave for one player while keeping lock memory bounded O(1).
 */
@ApplicationScoped
public class BattleRegistry {

    private static final int LOCK_STRIPES = 256;

    private final Map<UUID, BattleSession> sessions = new ConcurrentHashMap<>();
    private final ReentrantLock[] playerLocks = new ReentrantLock[LOCK_STRIPES];

    public BattleRegistry() {
        for (int i = 0; i < LOCK_STRIPES; i++) {
            playerLocks[i] = new ReentrantLock();
        }
    }

    public BattleSession get(UUID playerId) {
        return sessions.get(playerId);
    }

    public void put(BattleSession session) {
        sessions.put(session.playerId(), session);
    }

    /** Removes the session only if it is still the registered one. */
    public void remove(BattleSession session) {
        sessions.remove(session.playerId(), session);
    }

    /** Snapshot of all currently registered battles (used by the idle-expiry sweep). */
    public List<BattleSession> sessionsSnapshot() {
        return List.copyOf(sessions.values());
    }

    /** Drops every battle (tests: simulates a restart). */
    public void clear() {
        sessions.clear();
    }

    public <T> T withPlayerLock(UUID playerId, Supplier<T> work) {
        ReentrantLock lock = playerLocks[Math.floorMod(playerId.hashCode(), LOCK_STRIPES)];
        lock.lock();
        try {
            return work.get();
        } finally {
            lock.unlock();
        }
    }
}
