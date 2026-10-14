package it.unipi.ase.tamers.auth.account;

import java.time.Duration;
import java.time.Instant;

/** Brute-force protection: after maxAttempts consecutive failures the account is locked for a while. */
public final class LoginGuard {
    private final int maxAttempts;
    private final Duration lockDuration;

    public LoginGuard(int maxAttempts, Duration lockDuration) {
        this.maxAttempts = maxAttempts;
        this.lockDuration = lockDuration;
    }

    public boolean isLocked(Account account, Instant now) {
        return account.lockedUntil != null && now.isBefore(account.lockedUntil);
    }

    public void onFailure(Account account, Instant now) {
        account.failedAttempts++;
        if (account.failedAttempts >= maxAttempts) {
            account.lockedUntil = now.plus(lockDuration);
            account.failedAttempts = 0;
        }
    }

    public void onSuccess(Account account, Instant now) {
        account.failedAttempts = 0;
        account.lockedUntil = null;
        account.lastLogin = now;
    }
}
