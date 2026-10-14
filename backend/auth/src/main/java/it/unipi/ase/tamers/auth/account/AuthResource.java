package it.unipi.ase.tamers.auth.account;

import io.smallrye.jwt.build.Jwt;
import it.unipi.ase.tamers.auth.account.AuthDtos.*;
import it.unipi.ase.tamers.auth.common.ApiException;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.security.PermitAll;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Response;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.logging.Logger;
import org.mindrot.jbcrypt.BCrypt;

@Path("/auth")
public class AuthResource {
    private static final Logger LOG = Logger.getLogger(AuthResource.class);
    private static final int BCRYPT_COST = 12;
    // Checked when the username does not exist, so response time does not reveal valid usernames.
    private static final String DUMMY_HASH = BCrypt.hashpw("dummy-password", BCrypt.gensalt(BCRYPT_COST));

    @ConfigProperty(name = "auth.token.ttl-seconds")
    long tokenTtl;
    @ConfigProperty(name = "auth.lockout.max-attempts")
    int maxAttempts;
    @ConfigProperty(name = "auth.lockout.minutes")
    long lockMinutes;

    @Inject
    JsonWebToken jwt;
    @Inject
    @RestClient
    PlayersClient players;

    private LoginGuard guard;

    @PostConstruct
    void init() {
        guard = new LoginGuard(maxAttempts, Duration.ofMinutes(lockMinutes));
    }

    @POST
    @Path("/register")
    @PermitAll
    @Transactional
    public Response register(@Valid @NotNull RegisterRequest request) {
        String username = request.username().toLowerCase();
        if (Account.findByUsername(username).isPresent()) {
            throw ApiException.conflict("username already taken");
        }
        Account account = new Account();
        account.playerId = UUID.randomUUID();
        account.username = username;
        account.passwordHash = BCrypt.hashpw(request.password(), BCrypt.gensalt(BCRYPT_COST));
        account.createdAt = Instant.now();
        account.persistAndFlush();
        try {
            players.create(new CreatePlayerRequest(account.playerId, username));
        } catch (RuntimeException e) {
            LOG.errorf("players service unavailable during registration: %s", e.getMessage());
            throw ApiException.upstream("could not create the player profile, try again later");
        }
        return Response.status(201).entity(new RegisterResponse(account.playerId, username)).build();
    }

    @POST
    @Path("/login")
    @PermitAll
    @Transactional(dontRollbackOn = ApiException.class)
    public TokenResponse login(@Valid @NotNull LoginRequest request) {
        Instant now = Instant.now();
        Account account = Account.findByUsername(request.username()).orElse(null);
        if (account == null) {
            BCrypt.checkpw(request.password(), DUMMY_HASH);
            throw invalidCredentials();
        }
        if (guard.isLocked(account, now)) {
            throw new ApiException(429, "too_many_requests", "too many failed attempts, try again later");
        }
        if (!BCrypt.checkpw(request.password(), account.passwordHash)) {
            guard.onFailure(account, now);
            throw invalidCredentials();
        }
        guard.onSuccess(account, now);
        String token = Jwt.issuer("monster-tamers")
                .subject(account.playerId.toString())
                .upn(account.username)
                .groups(Set.of("player"))
                .expiresIn(Duration.ofSeconds(tokenTtl))
                .sign();
        return new TokenResponse(token, "Bearer", tokenTtl, account.playerId, account.username);
    }

    @GET
    @Path("/me")
    @RolesAllowed("player")
    public MeResponse me() {
        Account account = Account.<Account>findByIdOptional(UUID.fromString(jwt.getSubject()))
                .orElseThrow(() -> ApiException.notFound("account not found"));
        return new MeResponse(account.playerId, account.username, account.createdAt, account.lastLogin);
    }

    private static ApiException invalidCredentials() {
        return new ApiException(401, "unauthorized", "invalid username or password");
    }
}
