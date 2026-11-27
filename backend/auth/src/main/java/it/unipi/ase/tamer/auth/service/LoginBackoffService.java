package it.unipi.ase.tamer.auth.service;

import io.quarkus.narayana.jta.QuarkusTransaction;
import it.unipi.ase.tamer.auth.audit.UsernameBlindIndex;
import it.unipi.ase.tamer.auth.domain.LoginFailure;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceException;
import java.time.Instant;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Per-username login backoff (AUTH-8). After {@code maxAttempts} failed password logins for the same
 * {@code username_hmac} within {@code windowSeconds}, locks further login attempts for
 * {@code windowSeconds}. All writes run in their own committed transaction so thrown OAuth errors
 * cannot roll back failure tracking.
 */
@ApplicationScoped
public class LoginBackoffService {

    @ConfigProperty(name = "tamer.login-backoff.max-attempts", defaultValue = "5")
    int maxAttempts;

    @ConfigProperty(name = "tamer.login-backoff.window-seconds", defaultValue = "900")
    long windowSeconds;

    public boolean isLocked(String usernameHmac) {
        if (isIgnored(usernameHmac)) {
            return false;
        }
        return QuarkusTransaction.requiringNew().call(() -> {
            LoginFailure row = LoginFailure.findById(usernameHmac);
            return row != null && row.lockedUntil != null && row.lockedUntil.isAfter(Instant.now());
        });
    }

    public void recordFailure(String usernameHmac) {
        if (isIgnored(usernameHmac)) {
            return;
        }
        try {
            QuarkusTransaction.requiringNew().run(() -> upsertFailure(usernameHmac));
        } catch (PersistenceException e) {
            // Concurrent first-failure insert for the same username_hmac: retry once now that the row exists.
            QuarkusTransaction.requiringNew().run(() -> upsertFailure(usernameHmac));
        }
    }

    public void clearFailures(String usernameHmac) {
        if (isIgnored(usernameHmac)) {
            return;
        }
        QuarkusTransaction.requiringNew().run(() -> LoginFailure.delete("usernameHmac", usernameHmac));
    }

    private void upsertFailure(String usernameHmac) {
        Instant now = Instant.now();
        LoginFailure row = LoginFailure.findById(usernameHmac, LockModeType.PESSIMISTIC_WRITE);
        if (row == null) {
            row = new LoginFailure();
            row.usernameHmac = usernameHmac;
            row.failedCount = 1;
            row.windowStartedAt = now;
            row.lockedUntil = 1 >= maxAttempts ? now.plusSeconds(windowSeconds) : null;
            row.persistAndFlush();
            return;
        }
        if (!row.windowStartedAt.plusSeconds(windowSeconds).isAfter(now)) {
            row.failedCount = 1;
            row.windowStartedAt = now;
            row.lockedUntil = 1 >= maxAttempts ? now.plusSeconds(windowSeconds) : null;
        } else {
            row.failedCount++;
            if (row.failedCount >= maxAttempts) {
                row.lockedUntil = now.plusSeconds(windowSeconds);
            }
        }
    }

    private static boolean isIgnored(String usernameHmac) {
        return usernameHmac == null || usernameHmac.isBlank() || UsernameBlindIndex.MALFORMED.equals(usernameHmac);
    }
}
