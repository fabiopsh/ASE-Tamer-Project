package it.unipi.ase.tamer.encounters.service;

import jakarta.enterprise.context.ApplicationScoped;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

/**
 * Battles in memory, one per player (R19): {@code ConcurrentHashMap<player_id, BattleSession>}. A restart loses
 * them; the lazy recovery then closes the battle recorded on the run as {@code interrupted}.
 *
 * <p>Also holds one lock per player: every request that changes the run or the battle of a player (start,
 * action, abandon, new run, lazy recovery) runs under it, so these flows never interleave for one player.
 * Locks are reentrant: the recovery can run inside an operation that already holds the lock.
 */
@ApplicationScoped
public class BattleRegistry {

    private final Map<UUID, BattleSession> sessions = new ConcurrentHashMap<>();
    private final Map<UUID, ReentrantLock> playerLocks = new ConcurrentHashMap<>();

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

    /** Drops every battle (tests: simulates a restart). */
    public void clear() {
        sessions.clear();
    }

    public <T> T withPlayerLock(UUID playerId, Supplier<T> work) {
        ReentrantLock lock = playerLocks.computeIfAbsent(playerId, id -> new ReentrantLock());
        lock.lock();
        try {
            return work.get();
        } finally {
            lock.unlock();
        }
    }
}
