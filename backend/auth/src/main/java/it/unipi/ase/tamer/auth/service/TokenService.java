package it.unipi.ase.tamer.auth.service;

import io.quarkus.narayana.jta.QuarkusTransaction;
import it.unipi.ase.tamer.auth.api.dto.TokenResponse;
import it.unipi.ase.tamer.auth.audit.AuditLog;
import it.unipi.ase.tamer.auth.audit.AuditLog.Outcome;
import it.unipi.ase.tamer.auth.audit.UsernameBlindIndex;
import it.unipi.ase.tamer.auth.domain.Account;
import it.unipi.ase.tamer.auth.domain.LoginFailure;
import it.unipi.ase.tamer.auth.domain.RefreshToken;
import it.unipi.ase.tamer.auth.error.ApiException;
import it.unipi.ase.tamer.auth.error.OAuthException;
import it.unipi.ase.tamer.auth.security.ServiceClientRegistry;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.LockModeType;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * OAuth2 grants and session lifecycle: {@code password} (US-02), {@code refresh_token} (AUTH-7),
 * {@code logout} (AUTH-7) and {@code client_credentials} for services (AUTH-4).
 * Every credential check writes one audit line (SEC-3).
 */
@ApplicationScoped
public class TokenService {

    static final String LOGIN = "login";
    static final String REFRESH = "refresh";
    static final String REFRESH_REUSE = "refresh_reuse";
    static final String LOGOUT = "logout";
    static final String SERVICE_TOKEN = "service_token";

    private static final int REFRESH_TOKEN_BYTES = 32;
    private static final SecureRandom RANDOM = new SecureRandom();

    @Inject
    PasswordHasher hasher;

    @Inject
    TokenIssuer issuer;

    @Inject
    ServiceClientRegistry clients;

    @Inject
    UsernameBlindIndex blindIndex;

    @Inject
    UsernameCrypto usernameCrypto;

    @Inject
    LoginBackoffService loginBackoff;

    @ConfigProperty(name = "tamer.refresh-token-ttl", defaultValue = "604800")
    long refreshTokenTtl;

    public TokenResponse passwordGrant(String username, String password) {
        if (isBlank(username) || password == null || password.isEmpty()) {
            throw OAuthException.invalidRequest("username and password are required.");
        }
        String usernameHmac = blindIndex.of(username);
        if (UsernameBlindIndex.MALFORMED.equals(usernameHmac)) {
            hasher.burn();
            AuditLog.audit(LOGIN, Outcome.DENIED, UsernameBlindIndex.MALFORMED, Map.of("reason", "invalid_grant"));
            throw OAuthException.invalidGrant();
        }
        if (loginBackoff.isLocked(usernameHmac)) {
            hasher.burn();
            AuditLog.audit(LOGIN, Outcome.DENIED, usernameHmac, Map.of("reason", "locked"));
            throw OAuthException.invalidGrant();
        }

        Optional<Account> account = Account.findByUsernameHmac(usernameHmac);
        boolean valid = account.isPresent()
                ? hasher.matches(password, account.get().passwordHash)
                : hasher.burn();
        if (!valid) {
            loginBackoff.recordFailure(usernameHmac);
            AuditLog.audit(LOGIN, Outcome.DENIED, usernameHmac, Map.of("reason", "invalid_grant"));
            throw OAuthException.invalidGrant();
        }

        Account found = account.get();
        String normalizedUsername = usernameCrypto.decrypt(found.usernameEnc);
        String rawRefreshToken = generateRawRefreshToken();
        String refreshHash = hashRefreshToken(rawRefreshToken);

        QuarkusTransaction.requiringNew().run(() -> {
            Instant now = Instant.now();
            Account.update("lastLogin = ?1 where playerId = ?2", now, found.playerId);
            RefreshToken rt = new RefreshToken();
            rt.tokenHash = refreshHash;
            rt.playerId = found.playerId;
            rt.familyId = UUID.randomUUID();
            rt.createdAt = now;
            rt.expiresAt = now.plusSeconds(refreshTokenTtl);
            rt.revoked = false;
            rt.revokedAt = null;
            rt.persist();
            LoginFailure.delete("usernameHmac", usernameHmac);
        });

        IssuedToken access = issuer.playerToken(found.playerId, normalizedUsername);
        AuditLog.audit(LOGIN, Outcome.SUCCESS, found.playerId.toString());
        return TokenResponse.bearer(access.value(), access.expiresIn(), rawRefreshToken, access.scope());
    }

    public TokenResponse refreshGrant(String rawRefreshToken) {
        if (isBlank(rawRefreshToken)) {
            throw OAuthException.invalidRequest("refresh_token is required.");
        }
        String tokenHash = hashRefreshToken(rawRefreshToken);

        RefreshResult result;
        try {
            result = QuarkusTransaction.requiringNew().call(() -> rotateRefreshToken(tokenHash));
        } catch (RuntimeException e) {
            throw ApiException.dependencyUnavailable(e);
        }

        if (result.reuseDetected()) {
            AuditLog.audit(REFRESH_REUSE, Outcome.DENIED, result.playerId().toString(),
                    Map.of("reason", "token_reuse"));
            throw OAuthException.invalidRefreshGrant();
        }
        if (!result.success()) {
            String user = result.playerId() != null ? result.playerId().toString() : "-";
            AuditLog.audit(REFRESH, Outcome.DENIED, user, Map.of("reason", "invalid_grant"));
            throw OAuthException.invalidRefreshGrant();
        }

        AuditLog.audit(REFRESH, Outcome.SUCCESS, result.playerId().toString());
        return result.response();
    }

    public void logout(String rawRefreshToken) {
        if (isBlank(rawRefreshToken)) {
            throw OAuthException.invalidRequest("refresh_token is required.");
        }
        String tokenHash = hashRefreshToken(rawRefreshToken);

        UUID playerId;
        try {
            playerId = QuarkusTransaction.requiringNew().call(() -> revokeFamilyForToken(tokenHash));
        } catch (RuntimeException e) {
            throw ApiException.dependencyUnavailable(e);
        }

        AuditLog.audit(LOGOUT, Outcome.SUCCESS, playerId != null ? playerId.toString() : "-");
    }

    public TokenResponse clientCredentialsGrant(String clientId, String clientSecret) {
        if (isBlank(clientId) || clientSecret == null || clientSecret.isEmpty()) {
            throw OAuthException.invalidRequest("client_id and client_secret are required.");
        }
        if (!clients.authenticate(clientId, clientSecret)) {
            AuditLog.audit(SERVICE_TOKEN, Outcome.DENIED, sanitize(clientId), Map.of("reason", "invalid_client"));
            throw OAuthException.invalidClient();
        }
        IssuedToken token = issuer.serviceToken(clientId);
        AuditLog.audit(SERVICE_TOKEN, Outcome.SUCCESS, sanitize(clientId));
        return TokenResponse.bearer(token.value(), token.expiresIn(), token.scope());
    }

    private RefreshResult rotateRefreshToken(String tokenHash) {
        Optional<RefreshToken> initial = RefreshToken.findByHash(tokenHash);
        if (initial.isEmpty()) {
            return RefreshResult.denied(null);
        }
        UUID playerId = initial.get().playerId;
        // Clear persistence context before acquiring the account-row lock so we re-read fresh DB state.
        RefreshToken.getEntityManager().clear();

        Account account = Account.findById(playerId, LockModeType.PESSIMISTIC_WRITE);
        if (account == null) {
            return RefreshResult.denied(null);
        }
        RefreshToken current = RefreshToken.findByHash(tokenHash).orElse(null);
        if (current == null) {
            return RefreshResult.denied(playerId);
        }

        Instant now = Instant.now();
        if (current.isRevoked()) {
            // Reuse detection: revoke every remaining token in this family and let the tx commit.
            RefreshToken.revokeFamily(current.familyId, now);
            return RefreshResult.reuse(playerId);
        }
        if (!current.expiresAt.isAfter(now)) {
            return RefreshResult.denied(playerId);
        }

        current.revoked = true;
        current.revokedAt = now;

        String newRawToken = generateRawRefreshToken();
        RefreshToken rotated = new RefreshToken();
        rotated.tokenHash = hashRefreshToken(newRawToken);
        rotated.playerId = account.playerId;
        rotated.familyId = current.familyId;
        rotated.createdAt = now;
        rotated.expiresAt = current.expiresAt;
        rotated.revoked = false;
        rotated.revokedAt = null;
        rotated.persist();

        String normalizedUsername = usernameCrypto.decrypt(account.usernameEnc);
        IssuedToken access = issuer.playerToken(account.playerId, normalizedUsername);
        TokenResponse response = TokenResponse.bearer(access.value(), access.expiresIn(), newRawToken, access.scope());
        return RefreshResult.ok(account.playerId, response);
    }

    private UUID revokeFamilyForToken(String tokenHash) {
        Optional<RefreshToken> initial = RefreshToken.findByHash(tokenHash);
        if (initial.isEmpty()) {
            return null;
        }
        UUID playerId = initial.get().playerId;
        UUID familyId = initial.get().familyId;
        RefreshToken.getEntityManager().clear();

        // Serialize against concurrent refresh rotations for the same player.
        Account.findById(playerId, LockModeType.PESSIMISTIC_WRITE);
        RefreshToken.revokeFamily(familyId, Instant.now());
        return playerId;
    }

    static String generateRawRefreshToken() {
        byte[] bytes = new byte[REFRESH_TOKEN_BYTES];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public static String hashRefreshToken(String rawToken) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    /** Client ids end up in logs: keep only safe characters. */
    private static String sanitize(String value) {
        String safe = value.replaceAll("[^A-Za-z0-9_-]", "?");
        return safe.length() > 32 ? safe.substring(0, 32) : safe;
    }

    private record RefreshResult(boolean success, boolean reuseDetected, UUID playerId, TokenResponse response) {
        static RefreshResult ok(UUID playerId, TokenResponse response) {
            return new RefreshResult(true, false, playerId, response);
        }

        static RefreshResult denied(UUID playerId) {
            return new RefreshResult(false, false, playerId, null);
        }

        static RefreshResult reuse(UUID playerId) {
            return new RefreshResult(false, true, playerId, null);
        }
    }
}
