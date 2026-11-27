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
 * A player's credentials. The normalized username is never stored in plain text: {@code username_hmac}
 * is the keyed blind index used for lookup and uniqueness, and {@code username_enc} holds the
 * AES-256-GCM ciphertext (AUTH-8).
 */
@Entity
@Table(name = "accounts")
public class Account extends PanacheEntityBase {

    @Id
    @Column(name = "player_id")
    public UUID playerId;

    @Column(name = "username_hmac", nullable = false, length = 64)
    public String usernameHmac;

    @Column(name = "username_enc", nullable = false)
    public byte[] usernameEnc;

    @Column(name = "password_hash", nullable = false, length = 72)
    public String passwordHash;

    @Column(name = "created_at", nullable = false, updatable = false)
    public Instant createdAt;

    @Column(name = "last_login")
    public Instant lastLogin;

    public static Optional<Account> findByUsernameHmac(String usernameHmac) {
        return find("usernameHmac", usernameHmac).firstResultOptional();
    }
}
