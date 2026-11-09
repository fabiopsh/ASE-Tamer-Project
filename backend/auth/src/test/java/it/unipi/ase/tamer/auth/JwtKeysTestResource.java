package it.unipi.ase.tamer.auth;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Map;

/**
 * Generates a throw-away RSA key pair for the tests (no key is ever committed) and points the
 * signing and verification config to it, like init-secrets does in the containers.
 */
public class JwtKeysTestResource implements QuarkusTestResourceLifecycleManager {

    private Path dir;

    @Override
    public Map<String, String> start() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            KeyPair pair = generator.generateKeyPair();
            dir = Files.createTempDirectory("tamer-jwt");
            Path privateKey = dir.resolve("jwt-private.pem");
            Path publicKey = dir.resolve("jwt-public.pem");
            String privatePem = pem("PRIVATE KEY", pair.getPrivate().getEncoded());
            String publicPem = pem("PUBLIC KEY", pair.getPublic().getEncoded());
            Files.writeString(privateKey, privatePem);
            Files.writeString(publicKey, publicPem);
            // Quarkus generates its own key pair in test mode and sets the inline properties,
            // which win over *.location: override both forms with the same pair.
            return Map.of(
                    "smallrye.jwt.sign.key.location", privateKey.toString(),
                    "mp.jwt.verify.publickey.location", publicKey.toString(),
                    "smallrye.jwt.sign.key", privatePem,
                    "mp.jwt.verify.publickey", publicPem);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public void stop() {
        if (dir == null) {
            return;
        }
        try (var files = Files.list(dir)) {
            for (Path file : files.toList()) {
                Files.deleteIfExists(file);
            }
            Files.deleteIfExists(dir);
        } catch (IOException ignored) {
            // temporary directory, best effort
        }
    }

    private static String pem(String type, byte[] der) {
        String body = Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(der);
        return "-----BEGIN " + type + "-----\n" + body + "\n-----END " + type + "-----\n";
    }
}
