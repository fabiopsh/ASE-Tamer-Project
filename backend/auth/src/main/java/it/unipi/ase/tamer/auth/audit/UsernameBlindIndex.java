package it.unipi.ase.tamer.auth.audit;

import it.unipi.ase.tamer.auth.service.UsernameNormalizer;
import jakarta.enterprise.context.ApplicationScoped;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.HexFormat;
import java.util.regex.Pattern;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Blind index of a username: {@code HMAC-SHA256(username-hmac-key, normalized username)} as 64
 * hex characters (decision P-27). The audit log records it instead of the plain username, so
 * failed attempts on one account can be counted without writing the username in the logs.
 * AUTH-8 reuses the same key and value for the {@code username_hmac} column.
 *
 * <p>The key comes from {@code tamer.crypto.username-hmac-key} (prod: secrets.properties,
 * written by init-secrets). Without it the service does not start.
 */
@ApplicationScoped
public class UsernameBlindIndex {

    /** Value recorded when the submitted username cannot be a valid username. */
    public static final String MALFORMED = "?";

    /** Same rule as the registration DTO, on the normalized (lowercase) value. */
    private static final Pattern VALID = Pattern.compile("[a-z0-9_]{3,20}");

    private final SecretKeySpec key;

    public UsernameBlindIndex(@ConfigProperty(name = "tamer.crypto.username-hmac-key") String key) {
        if (key == null || key.length() < 32) {
            throw new IllegalStateException("tamer.crypto.username-hmac-key must have at least 32 characters");
        }
        this.key = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }

    /** Index of a username as submitted by a client, or {@link #MALFORMED}. */
    public String of(String username) {
        String normalized = UsernameNormalizer.normalize(username);
        if (normalized == null || !VALID.matcher(normalized).matches()) {
            return MALFORMED;
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(key);
            return HexFormat.of().formatHex(mac.doFinal(normalized.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA256 not available", e);
        }
    }
}
