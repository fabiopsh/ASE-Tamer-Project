package it.unipi.ase.tamer.auth;

import static io.restassured.RestAssured.given;
import static it.unipi.ase.tamer.auth.TestData.PASSWORD;
import static it.unipi.ase.tamer.auth.TestData.register;
import static it.unipi.ase.tamer.auth.TestData.uniqueUsername;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import io.quarkus.test.InjectMock;
import io.quarkus.test.common.WithTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import it.unipi.ase.tamer.auth.client.CreatePlayerRequest;
import it.unipi.ase.tamer.auth.client.PlayersClient;
import it.unipi.ase.tamer.auth.domain.Account;
import jakarta.ws.rs.ProcessingException;
import java.util.Map;
import java.util.UUID;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

@QuarkusTest
@WithTestResource(JwtKeysTestResource.class)
class RegistrationResourceTest {

    @InjectMock
    @RestClient
    PlayersClient players;

    @Test
    void validRegistrationCreatesAccountAndProfile() {
        String username = uniqueUsername();

        String id = register(username.toUpperCase(), PASSWORD)
                .then().statusCode(201)
                .body("player_id", notNullValue())
                .body("username", equalTo(username))
                .extract().path("player_id");

        ArgumentCaptor<CreatePlayerRequest> captor = ArgumentCaptor.forClass(CreatePlayerRequest.class);
        verify(players).create(captor.capture());
        assertEquals(UUID.fromString(id), captor.getValue().playerId());

        Account account = Account.findById(UUID.fromString(id));
        assertEquals(username, account.username);
        assertTrue(account.passwordHash.startsWith("$2"));
        assertNotEquals(PASSWORD, account.passwordHash);
    }

    @Test
    void duplicateUsernameIsRejected() {
        String username = uniqueUsername();
        register(username, PASSWORD).then().statusCode(201);

        register(username, PASSWORD).then().statusCode(409).body("error", equalTo("USERNAME_TAKEN"));
    }

    @Test
    void duplicateUsernameIsCaseInsensitive() {
        String username = uniqueUsername();
        register(username, PASSWORD).then().statusCode(201);

        register(username.toUpperCase(), PASSWORD).then().statusCode(409).body("error", equalTo("USERNAME_TAKEN"));
    }

    @Test
    void shortPasswordIsRejected() {
        register(uniqueUsername(), "1234567").then().statusCode(400)
                .body("error", equalTo("VALIDATION_ERROR"))
                .body("details.field", hasItem("password"));
        verify(players, never()).create(any());
    }

    @Test
    void passwordLongerThan72CharactersIsRejected() {
        register(uniqueUsername(), "a".repeat(73)).then().statusCode(400)
                .body("details.field", hasItem("password"));
    }

    @Test
    void passwordLongerThan72BytesIsRejected() {
        // 40 characters but 80 bytes in UTF-8: bcrypt would silently ignore the tail.
        register(uniqueUsername(), "è".repeat(40)).then().statusCode(400)
                .body("details.field", hasItem("password"));
    }

    @Test
    void passwordEqualToUsernameIsRejected() {
        String username = uniqueUsername();
        register(username, username.toUpperCase()).then().statusCode(400)
                .body("details.code", hasItem("EQUALS_USERNAME"));
    }

    @Test
    void invalidUsernameIsRejected() {
        register("ash!", PASSWORD).then().statusCode(400).body("details.field", hasItem("username"));
        register("ab", PASSWORD).then().statusCode(400).body("details.field", hasItem("username"));
        register("a".repeat(21), PASSWORD).then().statusCode(400).body("details.field", hasItem("username"));
    }

    @Test
    void unknownFieldIsRejected() {
        given().contentType(ContentType.JSON)
                .body(Map.of("username", uniqueUsername(), "password", PASSWORD, "player_id", UUID.randomUUID()))
                .post("/api/auth/register")
                .then().statusCode(400).body("error", equalTo("VALIDATION_ERROR"));
    }

    @Test
    void malformedJsonIsRejected() {
        given().contentType(ContentType.JSON).body("{\"username\": ")
                .post("/api/auth/register")
                .then().statusCode(400).body("error", equalTo("VALIDATION_ERROR"));
    }

    @Test
    void playersUnavailableRollsBackTheAccount() {
        doThrow(new ProcessingException("players down")).when(players).create(any());
        String username = uniqueUsername();

        register(username, PASSWORD).then().statusCode(503).body("error", equalTo("DEPENDENCY_UNAVAILABLE"));

        assertTrue(Account.findByUsername(username).isEmpty());
    }
}
