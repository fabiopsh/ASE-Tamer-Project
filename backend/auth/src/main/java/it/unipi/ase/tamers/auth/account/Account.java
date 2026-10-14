package it.unipi.ase.tamers.auth.account;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Entity
@Table(name = "accounts")
public class Account extends PanacheEntityBase {
    @Id
    @Column(name = "player_id")
    public UUID playerId;

    @Column(nullable = false, unique = true, length = 20)
    public String username;

    @Column(name = "password_hash", nullable = false)
    public String passwordHash;

    @Column(name = "created_at", nullable = false)
    public Instant createdAt;

    @Column(name = "last_login")
    public Instant lastLogin;

    @Column(name = "failed_attempts", nullable = false)
    public int failedAttempts;

    @Column(name = "locked_until")
    public Instant lockedUntil;

    public static Optional<Account> findByUsername(String username) {
        return find("username", username.toLowerCase()).firstResultOptional();
    }
}
