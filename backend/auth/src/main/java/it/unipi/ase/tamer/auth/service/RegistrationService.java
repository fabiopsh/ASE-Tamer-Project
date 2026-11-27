package it.unipi.ase.tamer.auth.service;

import io.quarkus.narayana.jta.QuarkusTransaction;
import it.unipi.ase.tamer.auth.api.dto.RegisterRequest;
import it.unipi.ase.tamer.auth.api.dto.RegisterResponse;
import it.unipi.ase.tamer.auth.audit.AuditLog;
import it.unipi.ase.tamer.auth.audit.AuditLog.Outcome;
import it.unipi.ase.tamer.auth.audit.UsernameBlindIndex;
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
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.hibernate.exception.ConstraintViolationException;

/**
 * US-01. The account insert and the creation of the player profile happen in one local
 * transaction: if {@code players} fails, the account is rolled back and the client gets 503.
 * If the commit fails after {@code players} succeeded, an orphan profile may remain: it is
 * harmless (unreachable without an account).
 */
@ApplicationScoped
public class RegistrationService {

    public static final String USERNAME_TAKEN = "USERNAME_TAKEN";
    static final String REGISTER = "register";

    /** Curated common weak passwords rejected at registration (AUTH-8). */
    private static final Set<String> COMMON_PASSWORDS = Set.of(
            "password", "password1", "password12", "password123", "password1234", "passw0rd",
            "p@ssword", "p@ssw0rd", "pa$$word", "12345678", "123456789", "1234567890",
            "00000000", "11111111", "87654321", "987654321", "qwerty123", "qwertyui",
            "qwertyuiop", "asdfghjkl", "zxcvbnm1", "admin123", "admin1234", "administrator",
            "letmein1", "letmein123", "welcome1", "welcome123", "iloveyou", "iloveyou1",
            "sunshine", "princess", "football", "baseball", "dragon123", "monkey123",
            "master123", "shadow123", "trustno1", "superman", "abc12345", "abcd1234",
            "aaaaaaaa", "1q2w3e4r", "1qaz2wsx", "qazwsxedc", "changeme", "changeme1",
            "test1234", "default1", "guest123"
    );

    @Inject
    PasswordHasher hasher;

    @Inject
    @RestClient
    PlayersClient players;

    @Inject
    UsernameBlindIndex blindIndex;

    @Inject
    UsernameCrypto usernameCrypto;

    public RegisterResponse register(RegisterRequest request) {
        String username = UsernameNormalizer.normalize(request.username());
        String usernameHmac = blindIndex.of(username);
        if (UsernameBlindIndex.MALFORMED.equals(usernameHmac)) {
            throw new ApiException(400, ErrorCodes.VALIDATION_ERROR, "Request validation failed.",
                    List.of(new ErrorDetail("username", "PATTERN")));
        }
        validatePassword(username, request.password());

        // Hash and encrypt outside the transaction: bcrypt is slow and must not hold a DB connection.
        String hash = hasher.hash(request.password());
        byte[] usernameEnc = usernameCrypto.encrypt(username);
        UUID playerId = UUID.randomUUID();

        try {
            QuarkusTransaction.requiringNew().run(() -> {
                if (Account.findByUsernameHmac(usernameHmac).isPresent()) {
                    throw usernameTaken();
                }
                Account account = new Account();
                account.playerId = playerId;
                account.usernameHmac = usernameHmac;
                account.usernameEnc = usernameEnc;
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
        } catch (ApiException e) {
            // 409: the username exists (denied); 503: players unavailable (failure)
            AuditLog.audit(REGISTER, e.status() == 409 ? Outcome.DENIED : Outcome.FAILURE,
                    usernameHmac, Map.of("reason", e.code()));
            throw e;
        }

        AuditLog.audit(REGISTER, Outcome.SUCCESS, playerId.toString());
        return new RegisterResponse(playerId, usernameCrypto.decrypt(usernameEnc));
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
        if (COMMON_PASSWORDS.contains(password.toLowerCase(Locale.ROOT))) {
            throw new ApiException(400, ErrorCodes.VALIDATION_ERROR, "Password is too common.",
                    List.of(new ErrorDetail("password", "COMMON_PASSWORD")));
        }
    }

    private static ApiException usernameTaken() {
        return new ApiException(409, USERNAME_TAKEN, "Username already taken.");
    }
}
