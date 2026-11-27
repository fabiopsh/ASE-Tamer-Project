package it.unipi.ase.tamer.auth.domain;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Opaque refresh token stored only as its SHA-256 hex digest (AUTH-7).
 * Tokens belonging to the same login session share {@code family_id} and {@code expires_at}.
 * Consumed/revoked rows are retained until family expiry so reuse detection works.
 */
@Entity
@Table(name = "refresh_tokens")
public class RefreshToken extends PanacheEntityBase {

    @Id
    @Column(name = "token_hash", length = 64)
    public String tokenHash;

    @Column(name = "player_id", nullable = false)
    public UUID playerId;

    @Column(name = "family_id", nullable = false)
    public UUID familyId;

    @Column(name = "created_at", nullable = false, updatable = false)
    public Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    public Instant expiresAt;

    @Column(name = "revoked", nullable = false)
    public boolean revoked;

    @Column(name = "revoked_at")
    public Instant revokedAt;

    public boolean isRevoked() {
        return revoked || revokedAt != null;
    }

    public static Optional<RefreshToken> findByHash(String tokenHash) {
        return find("tokenHash", tokenHash).firstResultOptional();
    }

    public static long revokeFamily(UUID familyId, Instant now) {
        return update("revoked = true, revokedAt = ?1 where familyId = ?2 and revokedAt is null", now, familyId);
    }

    public static long deleteExpired(Instant now) {
        return delete("expiresAt < ?1", now);
    }
}
