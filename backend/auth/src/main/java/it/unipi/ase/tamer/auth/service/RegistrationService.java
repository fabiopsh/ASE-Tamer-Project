package it.unipi.ase.tamer.auth.service;

import io.quarkus.narayana.jta.QuarkusTransaction;
import it.unipi.ase.tamer.auth.api.dto.RegisterRequest;
import it.unipi.ase.tamer.auth.api.dto.RegisterResponse;
import it.unipi.ase.tamer.auth.client.CreatePlayerRequest;
import it.unipi.ase.tamer.auth.client.PlayersClient;
import it.unipi.ase.tamer.auth.domain.Account;
import it.unipi.ase.tamer.auth.error.ApiException;
import it.unipi.ase.tamer.auth.error.ErrorCodes;
import it.unipi.ase.tamer.auth.error.ErrorDetail;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.PersistenceException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.hibernate.exception.ConstraintViolationException;
import org.jboss.logging.Logger;

/**
 * US-01. The account insert and the creation of the player profile happen in one local
 * transaction: if {@code players} fails, the account is rolled back and the client gets 503.
 * If the commit fails after {@code players} succeeded, an orphan profile may remain: it is
 * harmless (unreachable without an account).
 */
@ApplicationScoped
public class RegistrationService {

    public static final String USERNAME_TAKEN = "USERNAME_TAKEN";
    private static final Logger LOG = Logger.getLogger(RegistrationService.class);

    @Inject
    PasswordHasher hasher;

    @Inject
    @RestClient
    PlayersClient players;

    public RegisterResponse register(RegisterRequest request) {
        String username = UsernameNormalizer.normalize(request.username());
        validatePassword(username, request.password());

        // Hash outside the transaction: bcrypt is slow and must not hold a DB connection.
        String hash = hasher.hash(request.password());
        UUID playerId = UUID.randomUUID();

        QuarkusTransaction.requiringNew().run(() -> {
            if (Account.findByUsername(username).isPresent()) {
                throw usernameTaken();
            }
            Account account = new Account();
            account.playerId = playerId;
            account.username = username;
            account.passwordHash = hash;
            account.createdAt = Instant.now();
            try {
                account.persistAndFlush();
            } catch (ConstraintViolationException e) {
                throw usernameTaken(); // concurrent registration of the same username
            } catch (PersistenceException e) {
                if (e.getCause() instanceof ConstraintViolationException) {
                    throw usernameTaken();
                }
                throw e;
            }
            try {
                players.create(new CreatePlayerRequest(playerId));
            } catch (RuntimeException e) {
                throw ApiException.dependencyUnavailable(e);
            }
        });

        LOG.infof("Account created, player_id=%s", playerId);
        return new RegisterResponse(playerId, username);
    }

    private static void validatePassword(String normalizedUsername, String password) {
        if (PasswordHasher.exceedsMaxLength(password)) {
            throw new ApiException(400, ErrorCodes.VALIDATION_ERROR, "Request validation failed.",
                    List.of(new ErrorDetail("password", "SIZE")));
        }
        if (password.equalsIgnoreCase(normalizedUsername)) {
            throw new ApiException(400, ErrorCodes.VALIDATION_ERROR, "Password must differ from the username.",
                    List.of(new ErrorDetail("password", "EQUALS_USERNAME")));
        }
    }

    private static ApiException usernameTaken() {
        return new ApiException(409, USERNAME_TAKEN, "Username already taken.");
    }
}
