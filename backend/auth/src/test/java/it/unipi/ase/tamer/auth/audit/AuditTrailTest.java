package it.unipi.ase.tamer.auth.audit;

import static io.restassured.RestAssured.given;
import static it.unipi.ase.tamer.auth.TestData.PASSWORD;
import static it.unipi.ase.tamer.auth.TestData.passwordGrant;
import static it.unipi.ase.tamer.auth.TestData.register;
import static it.unipi.ase.tamer.auth.TestData.uniqueUsername;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.quarkus.test.InjectMock;
import io.quarkus.test.common.WithTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.smallrye.faulttolerance.api.CircuitBreakerMaintenance;
import it.unipi.ase.tamer.auth.JwtKeysTestResource;
import it.unipi.ase.tamer.auth.client.PlayersClient;
import jakarta.inject.Inject;
import java.util.List;
import java.util.Map;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/**
 * SEC-3: the login, the service token and the registration leave one audit line each, with
 * who and the outcome, and never a password, a token, a secret or the plain username.
 * Removing an {@code AuditLog.audit(...)} call in TokenService or RegistrationService turns
 * these tests red.
 */
@QuarkusTest
@WithTestResource(JwtKeysTestResource.class)
class AuditTrailTest {

    @InjectMock
    @RestClient
    PlayersClient players;

    @Inject
    UsernameBlindIndex blindIndex;

    @Inject
    CircuitBreakerMaintenance circuitBreakers;

    private AuditCapture capture;

    @BeforeEach
    void listen() {
        capture = AuditCapture.start();
    }

    @AfterEach
    void stop() {
        capture.close();
        // The failure test opens the PlayersClient circuit breaker: do not leak it to other tests
        circuitBreakers.resetAll();
    }

    @Test
    void aWrongPasswordIsRecordedAsDeniedWithTheBlindIndex() {
        String username = uniqueUsername();
        register(username, PASSWORD).then().statusCode(201);
        capture.clear();

        passwordGrant(username, "wrong-pw-123").then().statusCode(400);

        Map<String, String> event = single("login");
        assertEquals("denied", event.get("outcome"));
        assertEquals(blindIndex.of(username), event.get("user"));
        assertEquals("invalid_grant", event.get("reason"));
        assertNotNull(event.get("request_id"), "the line can be matched with the gateway line");
        assertFalse(capture.text().contains("wrong-pw-123"), "password in the audit log");
        assertFalse(capture.text().contains(username), "plain username in the audit log (P-27)");
    }

    @Test
    void anUnknownUserIsDeniedLikeAWrongPassword() {
        String username = uniqueUsername();

        passwordGrant(username, PASSWORD).then().statusCode(400);

        Map<String, String> event = single("login");
        assertEquals("denied", event.get("outcome"));
        assertEquals(blindIndex.of(username), event.get("user"));
    }

    @Test
    void aMalformedUsernameIsAQuestionMarkAndStaysOnOneLine() {
        passwordGrant("ada\n{\"user\":\"root\"}", PASSWORD).then().statusCode(400);

        assertEquals(1, capture.messages().size());
        assertEquals("?", single("login").get("user"));
        assertFalse(capture.text().contains("root"));
    }

    @Test
    void aCorrectLoginIsRecordedWithThePlayerIdAndNoToken() {
        String username = uniqueUsername();
        String playerId = register(username, PASSWORD).then().statusCode(201).extract().path("player_id");
        capture.clear();

        String token = passwordGrant(username, PASSWORD).then().statusCode(200).extract().path("access_token");

        Map<String, String> event = single("login");
        assertEquals("success", event.get("outcome"));
        assertEquals(playerId, event.get("user"));
        assertFalse(capture.text().contains(PASSWORD), "password in the audit log");
        assertFalse(capture.text().contains(token), "access token in the audit log");
        assertFalse(capture.text().contains(username), "plain username in the audit log (P-27)");
    }

    @Test
    void aWrongClientSecretIsRecordedWithoutTheSecret() {
        clientCredentials("players", "a-wrong-client-secret").then().statusCode(401);

        Map<String, String> event = single("service_token");
        assertEquals("denied", event.get("outcome"));
        assertEquals("players", event.get("user"));
        assertEquals("invalid_client", event.get("reason"));
        assertFalse(capture.text().contains("a-wrong-client-secret"));
    }

    @Test
    void aServiceTokenIsRecordedWithoutTheTokenOrTheSecret() {
        String token = clientCredentials("players", "test-only-secret")
                .then().statusCode(200).extract().path("access_token");

        Map<String, String> event = single("service_token");
        assertEquals("success", event.get("outcome"));
        assertEquals("players", event.get("user"));
        assertFalse(capture.text().contains("test-only-secret"));
        assertFalse(capture.text().contains(token));
    }

    @Test
    void aRegistrationIsRecordedAndADuplicateIsDenied() {
        String username = uniqueUsername();
        String playerId = register(username, PASSWORD).then().statusCode(201).extract().path("player_id");
        register(username, PASSWORD).then().statusCode(409);

        List<Map<String, String>> events = capture.records("register");
        assertEquals(2, events.size());
        assertEquals("success", events.get(0).get("outcome"));
        assertEquals(playerId, events.get(0).get("user"));
        assertEquals("denied", events.get(1).get("outcome"));
        assertEquals(blindIndex.of(username), events.get(1).get("user"));
        assertEquals("USERNAME_TAKEN", events.get(1).get("reason"));
        assertFalse(capture.text().contains(PASSWORD));
        assertFalse(capture.text().contains(username));
    }

    @Test
    void aRegistrationThatCannotCreateTheProfileIsAFailure() {
        Mockito.doThrow(new RuntimeException("players down")).when(players).create(Mockito.any());

        register(uniqueUsername(), PASSWORD).then().statusCode(503);

        Map<String, String> event = single("register");
        assertEquals("failure", event.get("outcome"));
        assertEquals("DEPENDENCY_UNAVAILABLE", event.get("reason"));
    }

    @Test
    void everyLineIsOneJsonObject() {
        passwordGrant(uniqueUsername(), PASSWORD).then().statusCode(400);
        clientCredentials("nobody", "x").then().statusCode(401);

        assertTrue(capture.messages().size() >= 2);
        capture.messages().forEach(line -> {
            assertFalse(line.contains("\n"), line);
            assertTrue(line.startsWith("{") && line.endsWith("}"), line);
        });
        capture.records(); // fails if any line is not valid JSON
    }

    private Map<String, String> single(String event) {
        List<Map<String, String>> events = capture.records(event);
        assertEquals(1, events.size(), "audit lines for " + event + ": " + capture.messages());
        return events.get(0);
    }

    private static io.restassured.response.Response clientCredentials(String id, String secret) {
        return given().contentType(ContentType.URLENC)
                .formParam("grant_type", "client_credentials")
                .formParam("client_id", id)
                .formParam("client_secret", secret)
                .post("/internal/auth/token");
    }
}
