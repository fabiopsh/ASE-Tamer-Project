package it.unipi.ase.tamer.auth.service;

import io.smallrye.jwt.build.Jwt;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.Set;
import java.util.UUID;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Signs player and service JWTs (RS256) with the private key that only {@code auth} holds.
 * Claims: see docs/plan/03-auth.md#jwt-claims.
 */
@ApplicationScoped
public class TokenIssuer {

    public static final String ISSUER = "tamer-auth";
    public static final String AUDIENCE_API = "tamer-api";
    public static final String AUDIENCE_INTERNAL = "tamer-internal";
    public static final String ROLE_PLAYER = "player";
    public static final String ROLE_SERVICE = "service";
    public static final String KEY_ID = "tamer-1";
    /** client_id that {@code auth} uses for its own outgoing calls. */
    public static final String SELF_CLIENT_ID = "auth";

    @ConfigProperty(name = "tamer.access-token-ttl", defaultValue = "3600")
    long accessTokenTtl;

    @ConfigProperty(name = "tamer.service-token-ttl", defaultValue = "300")
    long serviceTokenTtl;

    public IssuedToken playerToken(UUID playerId, String username) {
        String token = Jwt.issuer(ISSUER)
                .audience(AUDIENCE_API)
                .subject(playerId.toString())
                .upn(username)
                .groups(Set.of(ROLE_PLAYER))
                .expiresIn(accessTokenTtl)
                .jws().keyId(KEY_ID)
                .sign();
        return new IssuedToken(token, accessTokenTtl, ROLE_PLAYER);
    }

    public IssuedToken serviceToken(String clientId) {
        String token = Jwt.issuer(ISSUER)
                .audience(AUDIENCE_INTERNAL)
                .subject(clientId)
                .upn(clientId)
                .groups(Set.of(ROLE_SERVICE))
                .expiresIn(serviceTokenTtl)
                .jws().keyId(KEY_ID)
                .sign();
        return new IssuedToken(token, serviceTokenTtl, ROLE_SERVICE);
    }
}
