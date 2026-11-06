package it.unipi.ase.tamer.auth;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;

import io.quarkus.test.common.WithTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import io.smallrye.jwt.auth.principal.JWTParser;
import jakarta.inject.Inject;
import java.util.Set;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.junit.jupiter.api.Test;

@QuarkusTest
@WithTestResource(JwtKeysTestResource.class)
class InternalTokenResourceTest {

    @Inject
    JWTParser parser;

    @Test
    void validClientGetsAServiceToken() throws Exception {
        String token = clientCredentials("players", "test-only-secret")
                .then().statusCode(200)
                .header("Cache-Control", "no-store")
                .body("token_type", equalTo("Bearer"))
                .body("expires_in", equalTo(300))
                .body("scope", equalTo("service"))
                .extract().path("access_token");

        JsonWebToken jwt = parser.parse(token);
        assertEquals("tamer-auth", jwt.getIssuer());
        assertEquals(Set.of("tamer-internal"), jwt.getAudience());
        assertEquals("players", jwt.getSubject());
        assertEquals(Set.of("service"), jwt.getGroups());
    }

    @Test
    void wrongSecretIsInvalidClient() {
        clientCredentials("players", "wrong").then().statusCode(401).body("error", equalTo("invalid_client"));
    }

    @Test
    void unknownClientIsInvalidClient() {
        clientCredentials("nobody", "test-only-secret").then().statusCode(401)
                .body("error", equalTo("invalid_client"));
    }

    @Test
    void testClientIsDisabledByDefault() {
        clientCredentials("tests", "test-tests-secret").then().statusCode(401)
                .body("error", equalTo("invalid_client"));
    }

    @Test
    void otherGrantTypesAreRejected() {
        given().contentType(ContentType.URLENC).formParam("grant_type", "password")
                .post("/internal/auth/token")
                .then().statusCode(400).body("error", equalTo("unsupported_grant_type"));
    }

    @Test
    void missingCredentialsAreInvalidRequest() {
        given().contentType(ContentType.URLENC).formParam("grant_type", "client_credentials")
                .post("/internal/auth/token")
                .then().statusCode(400).body("error", equalTo("invalid_request"));
    }

    private static Response clientCredentials(String clientId, String secret) {
        return given().contentType(ContentType.URLENC)
                .formParam("grant_type", "client_credentials")
                .formParam("client_id", clientId)
                .formParam("client_secret", secret)
                .post("/internal/auth/token");
    }
}
