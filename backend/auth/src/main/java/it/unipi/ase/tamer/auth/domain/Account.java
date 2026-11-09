package it.unipi.ase.tamer.auth.domain;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** A player's credentials. The username is stored normalised (see UsernameNormalizer). */
@Entity
@Table(name = "accounts")
public class Account extends PanacheEntityBase {

    @Id
    @Column(name = "player_id")
    public UUID playerId;

    @Column(name = "username", nullable = false, length = 20)
    public String username;

    @Column(name = "password_hash", nullable = false, length = 72)
    public String passwordHash;

    @Column(name = "created_at", nullable = false, updatable = false)
    public Instant createdAt;

    @Column(name = "last_login")
    public Instant lastLogin;

    public static Optional<Account> findByUsername(String normalizedUsername) {
        return find("username", normalizedUsername).firstResultOptional();
    }
}
