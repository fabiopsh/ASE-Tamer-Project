package it.unipi.ase.tamer.auth;

import static it.unipi.ase.tamer.auth.TestData.PASSWORD;
import static it.unipi.ase.tamer.auth.TestData.passwordGrant;
import static it.unipi.ase.tamer.auth.TestData.register;
import static it.unipi.ase.tamer.auth.TestData.uniqueUsername;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;

import io.quarkus.test.InjectMock;
import io.quarkus.test.common.WithTestResource;
import io.quarkus.test.junit.QuarkusTest;
import it.unipi.ase.tamer.auth.audit.AuditCapture;
import it.unipi.ase.tamer.auth.audit.UsernameBlindIndex;
import it.unipi.ase.tamer.auth.client.PlayersClient;
import jakarta.inject.Inject;
import java.util.List;
import java.util.Map;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.junit.jupiter.api.Test;

/** AUTH-8: per-username login backoff after 5 failed password attempts within 15 minutes. */
@QuarkusTest
@WithTestResource(JwtKeysTestResource.class)
class LoginBackoffTest {

    @InjectMock
    @RestClient
    PlayersClient players;

    @Inject
    UsernameBlindIndex blindIndex;

    @Test
    void fiveWrongAttemptsLockSixthEvenWithCorrectPassword() {
        String username = uniqueUsername();
        register(username, PASSWORD).then().statusCode(201);

        Map<String, Object> firstError = passwordGrant(username, "wrong-pass-1")
                .then().statusCode(400).extract().as(Map.class);
        assertEquals("invalid_grant", firstError.get("error"));

        for (int i = 2; i <= 5; i++) {
            passwordGrant(username, "wrong-pass-" + i)
                    .then().statusCode(400).body("error", equalTo("invalid_grant"));
        }

        // 6th attempt even with the right password is rejected with the exact same invalid_grant
        try (AuditCapture capture = AuditCapture.start()) {
            Map<String, Object> lockedError = passwordGrant(username, PASSWORD)
                    .then().statusCode(400).extract().as(Map.class);

            assertEquals(firstError, lockedError);
            List<Map<String, String>> events = capture.records("login");
            assertEquals(1, events.size());
            assertEquals("denied", events.get(0).get("outcome"));
            assertEquals(blindIndex.of(username), events.get(0).get("user"));
            assertEquals("locked", events.get(0).get("reason"));
        }
    }

    @Test
    void successfulLoginClearsFailureCounter() {
        String username = uniqueUsername();
        register(username, PASSWORD).then().statusCode(201);

        // 4 failures (below threshold of 5)
        for (int i = 1; i <= 4; i++) {
            passwordGrant(username, "wrong-pass-" + i)
                    .then().statusCode(400).body("error", equalTo("invalid_grant"));
        }

        // Correct login succeeds and clears the failure counter
        passwordGrant(username, PASSWORD).then().statusCode(200);

        // 4 more failures still do not lock the account
        for (int i = 1; i <= 4; i++) {
            passwordGrant(username, "wrong-pass-again-" + i)
                    .then().statusCode(400).body("error", equalTo("invalid_grant"));
        }
        passwordGrant(username, PASSWORD).then().statusCode(200);
    }
}
