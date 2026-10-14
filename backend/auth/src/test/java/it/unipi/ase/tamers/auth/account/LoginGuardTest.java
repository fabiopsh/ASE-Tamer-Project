package it.unipi.ase.tamers.auth.account;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.mindrot.jbcrypt.BCrypt;

class LoginGuardTest {
    private final LoginGuard guard = new LoginGuard(3, Duration.ofMinutes(5));
    private final Instant now = Instant.parse("2026-10-14T10:00:00Z");

    @Test
    void locksAfterMaxFailures() {
        Account account = new Account();
        guard.onFailure(account, now);
        guard.onFailure(account, now);
        assertFalse(guard.isLocked(account, now));
        guard.onFailure(account, now);
        assertTrue(guard.isLocked(account, now.plusSeconds(60)));
    }

    @Test
    void lockExpires() {
        Account account = new Account();
        for (int i = 0; i < 3; i++) guard.onFailure(account, now);
        assertFalse(guard.isLocked(account, now.plus(Duration.ofMinutes(6))));
    }

    @Test
    void successResetsCounterAndRecordsLogin() {
        Account account = new Account();
        guard.onFailure(account, now);
        guard.onFailure(account, now);
        guard.onSuccess(account, now);
        assertEquals(0, account.failedAttempts);
        assertEquals(now, account.lastLogin);
        guard.onFailure(account, now);
        assertFalse(guard.isLocked(account, now));
    }

    @Test
    void passwordHashIsSaltedAndVerifiable() {
        String a = BCrypt.hashpw("correct horse", BCrypt.gensalt(4));
        String b = BCrypt.hashpw("correct horse", BCrypt.gensalt(4));
        assertNotEquals(a, b, "same password must give different hashes");
        assertFalse(a.contains("correct horse"));
        assertTrue(BCrypt.checkpw("correct horse", a));
        assertFalse(BCrypt.checkpw("wrong", a));
    }
}
