package it.unipi.ase.tamer.auth.service;

import io.quarkus.narayana.jta.QuarkusTransaction;
import it.unipi.ase.tamer.auth.api.dto.TokenResponse;
import it.unipi.ase.tamer.auth.domain.Account;
import it.unipi.ase.tamer.auth.error.OAuthException;
import it.unipi.ase.tamer.auth.security.ServiceClientRegistry;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.Instant;
import java.util.Optional;
import org.jboss.logging.Logger;

/** OAuth2 grants: {@code password} for players (US-02), {@code client_credentials} for services. */
@ApplicationScoped
public class TokenService {

    private static final Logger LOG = Logger.getLogger(TokenService.class);

    @Inject
    PasswordHasher hasher;

    @Inject
    TokenIssuer issuer;

    @Inject
    ServiceClientRegistry clients;

    public TokenResponse passwordGrant(String username, String password) {
        if (isBlank(username) || password == null || password.isEmpty()) {
            throw OAuthException.invalidRequest("username and password are required.");
        }
        Optional<Account> account = Account.findByUsername(UsernameNormalizer.normalize(username));
        boolean valid = account.isPresent()
                ? hasher.matches(password, account.get().passwordHash)
                : hasher.burn();
        if (!valid) {
            LOG.info("Failed login");
            throw OAuthException.invalidGrant();
        }

        Account found = account.get();
        QuarkusTransaction.requiringNew().run(() ->
                Account.update("lastLogin = ?1 where playerId = ?2", Instant.now(), found.playerId));

        IssuedToken token = issuer.playerToken(found.playerId, found.username);
        return TokenResponse.bearer(token.value(), token.expiresIn(), token.scope());
    }

    public TokenResponse clientCredentialsGrant(String clientId, String clientSecret) {
        if (isBlank(clientId) || clientSecret == null || clientSecret.isEmpty()) {
            throw OAuthException.invalidRequest("client_id and client_secret are required.");
        }
        if (!clients.authenticate(clientId, clientSecret)) {
            LOG.warnf("Failed client authentication for client_id=%s", sanitize(clientId));
            throw OAuthException.invalidClient();
        }
        IssuedToken token = issuer.serviceToken(clientId);
        return TokenResponse.bearer(token.value(), token.expiresIn(), token.scope());
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    /** Client ids end up in logs: keep only safe characters. */
    private static String sanitize(String value) {
        String safe = value.replaceAll("[^A-Za-z0-9_-]", "?");
        return safe.length() > 32 ? safe.substring(0, 32) : safe;
    }
}
