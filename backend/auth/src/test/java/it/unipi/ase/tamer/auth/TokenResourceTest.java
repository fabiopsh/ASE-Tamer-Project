package it.unipi.ase.tamer.auth;

import static io.restassured.RestAssured.given;
import static it.unipi.ase.tamer.auth.TestData.PASSWORD;
import static it.unipi.ase.tamer.auth.TestData.passwordGrant;
import static it.unipi.ase.tamer.auth.TestData.register;
import static it.unipi.ase.tamer.auth.TestData.uniqueUsername;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import io.quarkus.test.InjectMock;
import io.quarkus.test.common.WithTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.smallrye.jwt.auth.principal.JWTParser;
import it.unipi.ase.tamer.auth.client.PlayersClient;
import it.unipi.ase.tamer.auth.domain.Account;
import jakarta.inject.Inject;
import java.util.Map;
import java.util.Set;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.junit.jupiter.api.Test;

@QuarkusTest
@WithTestResource(JwtKeysTestResource.class)
class TokenResourceTest {

    @InjectMock
    @RestClient
    PlayersClient players;

    @Inject
    JWTParser parser;

    @Test
    void correctCredentialsReturnAPlayerToken() throws Exception {
        String username = uniqueUsername();
        String playerId = register(username, PASSWORD).then().statusCode(201).extract().path("player_id");

        String token = passwordGrant(username.toUpperCase(), PASSWORD)
                .then().statusCode(200)
                .header("Cache-Control", "no-store")
                .header("Pragma", "no-cache")
                .body("token_type", equalTo("Bearer"))
                .body("expires_in", equalTo(3600))
                .body("scope", equalTo("player"))
                .body("access_token", notNullValue())
                .extract().path("access_token");

        JsonWebToken jwt = parser.parse(token);
        assertEquals("tamer-auth", jwt.getIssuer());
        assertEquals(Set.of("tamer-api"), jwt.getAudience());
        assertEquals(playerId, jwt.getSubject());
        assertEquals(username, jwt.getName());
        assertEquals(Set.of("player"), jwt.getGroups());
        assertNotNull(jwt.getTokenID());
        assertEquals(3600, jwt.getExpirationTime() - jwt.getIssuedAtTime());
        assertNotNull(Account.findByUsername(username).orElseThrow().lastLogin);
    }

    @Test
    void wrongPasswordAndUnknownUserGiveTheSameError() {
        String username = uniqueUsername();
        register(username, PASSWORD).then().statusCode(201);

        Map<String, Object> wrongPassword = passwordGrant(username, "wrong-password")
                .then().statusCode(400).header("Cache-Control", "no-store").extract().as(Map.class);
        Map<String, Object> unknownUser = passwordGrant(uniqueUsername(), PASSWORD)
                .then().statusCode(400).extract().as(Map.class);

        assertEquals("invalid_grant", wrongPassword.get("error"));
        assertEquals(wrongPassword, unknownUser);
    }

    @Test
    void missingGrantTypeIsInvalidRequest() {
        given().contentType(ContentType.URLENC).formParam("username", "x").formParam("password", "y")
                .post("/api/auth/token")
                .then().statusCode(400).body("error", equalTo("invalid_request"));
    }

    @Test
    void missingPasswordIsInvalidRequest() {
        given().contentType(ContentType.URLENC).formParam("grant_type", "password").formParam("username", "x")
                .post("/api/auth/token")
                .then().statusCode(400).body("error", equalTo("invalid_request"));
    }

    @Test
    void unsupportedGrantTypeIsRejected() {
        given().contentType(ContentType.URLENC).formParam("grant_type", "client_credentials")
                .post("/api/auth/token")
                .then().statusCode(400).body("error", equalTo("unsupported_grant_type"));
    }

    @Test
    void jsonBodyIsRejected() {
        given().contentType(ContentType.JSON).body(Map.of("grant_type", "password"))
                .post("/api/auth/token")
                .then().statusCode(415);
    }

    @Test
    void passwordLongerThan72BytesNeverMatches() {
        String username = uniqueUsername();
        register(username, PASSWORD).then().statusCode(201);

        passwordGrant(username, PASSWORD + "x".repeat(80)).then().statusCode(400)
                .body("error", equalTo("invalid_grant"));
    }
}
