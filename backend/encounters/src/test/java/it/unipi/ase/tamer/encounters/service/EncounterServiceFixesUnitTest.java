package it.unipi.ase.tamer.encounters.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import it.unipi.ase.tamer.encounters.engine.Battle;
import it.unipi.ase.tamer.encounters.engine.Combatant;
import it.unipi.ase.tamer.encounters.engine.ElementChart;
import it.unipi.ase.tamer.encounters.engine.EncounterSpec;
import it.unipi.ase.tamer.encounters.engine.TemplateInfo;
import it.unipi.ase.tamer.encounters.error.ApiException;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;

/** Unit tests for encounters service fixes: unique-violation detection, striped locks, closed-session guard. */
class EncounterServiceFixesUnitTest {

    @Test
    void isUniqueViolationDistinguishes23505FromGenericDbFailures() {
        SQLException unique = new SQLException("duplicate key", "23505");
        assertTrue(RunService.isUniqueViolation(unique));
        assertTrue(RunService.isUniqueViolation(new RuntimeException("tx failed", unique)));
        assertTrue(RunService.isUniqueViolation(new ConstraintViolationException("dup", unique, "ux_runs_one_active")));

        SQLException connRefused = new SQLException("connection refused", "08001");
        assertFalse(RunService.isUniqueViolation(connRefused));
        assertFalse(RunService.isUniqueViolation(new RuntimeException("db down", connRefused)));
        assertFalse(RunService.isUniqueViolation(new IllegalStateException("pool exhausted")));
    }

    @Test
    void battleRegistryStripedLocksAreBoundedAndReentrant() {
        BattleRegistry registry = new BattleRegistry();
        for (int i = 0; i < 400; i++) {
            UUID player = UUID.randomUUID();
            int result = registry.withPlayerLock(player, () -> registry.withPlayerLock(player, () -> 42));
            assertEquals(42, result);
        }
    }

    @Test
    void battleServiceCurrentRejectsSessionClosedWhileWaitingOnLock() throws Exception {
        BattleRegistry registry = new BattleRegistry();
        UUID playerId = UUID.randomUUID();
        Combatant mon = new Combatant(UUID.randomUUID().toString(), 1, "Hero", "time", "common", 5, 20, 10, 10, 10,
                null);
        Battle battle = new Battle(1L, new EncounterSpec(new TemplateInfo(11, "T", 1, "dream", false), mon),
                List.of(mon), ElementChart.defaults());
        BattleSession session = new BattleSession(UUID.randomUUID(), playerId, UUID.randomUUID(), UUID.randomUUID(),
                battle, null, null, Instant.now());
        registry.put(session);

        BattleService service = new BattleService();
        service.registry = registry;
        service.recovery = new RecoveryService() {
            @Override
            public void recover(UUID pid, boolean mutating) {
                // no-op
            }
        };

        // Hold session lock so current() passes the outer check, blocks on session.lock(), then sees closed()==true.
        session.lock().lock();
        java.util.concurrent.atomic.AtomicReference<Throwable> thrown = new java.util.concurrent.atomic.AtomicReference<>();
        Thread reader = new Thread(() -> {
            try {
                service.current(playerId);
            } catch (Throwable t) {
                thrown.set(t);
            }
        });
        try {
            reader.start();
            long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(5);
            while (!session.lock().hasQueuedThreads() && System.nanoTime() < deadline) {
                Thread.yield();
            }
            assertTrue(session.lock().hasQueuedThreads(), "reader should be waiting on session.lock()");
            session.close();
        } finally {
            session.lock().unlock();
        }
        reader.join(5000);
        assertFalse(reader.isAlive());
        assertTrue(thrown.get() instanceof ApiException, "expected ApiException, got " + thrown.get());
        assertEquals(404, ((ApiException) thrown.get()).status());
    }
}
