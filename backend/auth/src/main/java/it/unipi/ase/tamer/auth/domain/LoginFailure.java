package it.unipi.ase.tamer.auth.domain;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** Tracks consecutive failed password login attempts per {@code username_hmac} (AUTH-8). */
@Entity
@Table(name = "login_failures")
public class LoginFailure extends PanacheEntityBase {

    @Id
    @Column(name = "username_hmac", length = 64)
    public String usernameHmac;

    @Column(name = "failed_count", nullable = false)
    public int failedCount;

    @Column(name = "window_started_at", nullable = false)
    public Instant windowStartedAt;

    @Column(name = "locked_until")
    public Instant lockedUntil;
}
