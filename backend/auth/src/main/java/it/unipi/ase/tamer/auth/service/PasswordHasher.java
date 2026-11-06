package it.unipi.ase.tamer.auth.service;

import io.quarkus.elytron.security.common.BcryptUtil;
import jakarta.enterprise.context.ApplicationScoped;
import java.nio.charset.StandardCharsets;

/** bcrypt hashing (cost 12). bcrypt ignores bytes beyond 72, so longer passwords are rejected. */
@ApplicationScoped
public class PasswordHasher {

    static final int COST = 12;
    public static final int MAX_PASSWORD_BYTES = 72;

    /** Compared against when the username does not exist, so timing does not reveal it. */
    private final String dummyHash = BcryptUtil.bcryptHash("timing-protection-only", COST);

    public String hash(String password) {
        return BcryptUtil.bcryptHash(password, COST);
    }

    public boolean matches(String password, String hash) {
        if (exceedsMaxLength(password)) {
            burn();
            return false;
        }
        return BcryptUtil.matches(password, hash);
    }

    /** Spends the same time as a real comparison and always fails. */
    public boolean burn() {
        BcryptUtil.matches("timing-protection-only-mismatch", dummyHash);
        return false;
    }

    public static boolean exceedsMaxLength(String password) {
        return password.getBytes(StandardCharsets.UTF_8).length > MAX_PASSWORD_BYTES;
    }
}
