package it.unipi.ase.tamer.auth.service;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.scheduler.Scheduled;
import it.unipi.ase.tamer.auth.domain.RefreshToken;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.Instant;
import org.jboss.logging.Logger;

/**
 * Daily cleanup of expired refresh tokens (AUTH-7).
 * Consumed/revoked tokens are kept until family expiry so reuse detection continues to work.
 */
@ApplicationScoped
public class RefreshTokenCleanup {

    private static final Logger LOG = Logger.getLogger(RefreshTokenCleanup.class);

    @Scheduled(every = "24h")
    void scheduledPurge() {
        purgeExpired();
    }

    public long purgeExpired() {
        long deleted = QuarkusTransaction.requiringNew().call(() -> RefreshToken.deleteExpired(Instant.now()));
        if (deleted > 0) {
            LOG.infof("Purged %d expired refresh token row(s).", deleted);
        }
        return deleted;
    }
}
