package it.unipi.ase.tamer.auth.security;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/** Authenticates service clients (client credentials grant) in constant time. */
@ApplicationScoped
public class ServiceClientRegistry {

    /** Client used only by the component tests; accepted only when the test override enables it. */
    public static final String TEST_CLIENT_ID = "tests";

    @Inject
    ServiceClientsConfig config;

    @ConfigProperty(name = "tamer.test-client.enabled", defaultValue = "false")
    boolean testClientEnabled;

    public boolean authenticate(String clientId, String clientSecret) {
        String expected = config.secrets().get(clientId);
        if (TEST_CLIENT_ID.equals(clientId) && !testClientEnabled) {
            expected = null;
        }
        // Always compare, also for unknown clients, so timing does not reveal valid client ids.
        boolean equal = MessageDigest.isEqual(sha256(clientSecret),
                sha256(expected != null ? expected : "unknown-client"));
        return expected != null && equal;
    }

    private static byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
