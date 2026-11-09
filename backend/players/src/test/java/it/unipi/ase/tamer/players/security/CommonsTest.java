package it.unipi.ase.tamer.players.security;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.notNullValue;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.quarkus.test.security.jwt.Claim;
import io.quarkus.test.security.jwt.JwtSecurity;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

/** Error format, roles and request id (INFRA-7). Reference for the other services. */
@QuarkusTest
class CommonsTest {

    private static final String PLAYER_ID = "7c0e8a52-3f43-4a8f-9a3e-2f1d1b2b9c10";

    @Test
    void noTokenIs401WithErrorBody() {
        given().get("/probe/player").then().statusCode(401)
                .body("error", equalTo("UNAUTHORIZED"));
    }

    @Test
    @TestSecurity(user = "ash", roles = "player")
    @JwtSecurity(claims = @Claim(key = "sub", value = PLAYER_ID))
    void playerIdComesFromSubject() {
        given().get("/probe/player").then().statusCode(200).body(equalTo(PLAYER_ID));
    }

    @Test
    @TestSecurity(user = "ash", roles = "player")
    @JwtSecurity(claims = @Claim(key = "sub", value = PLAYER_ID))
    void playerOnInternalIs403() {
        given().get("/probe/service").then().statusCode(403).body("error", equalTo("FORBIDDEN"));
    }

    @Test
    @TestSecurity(user = "encounters", roles = "service")
    void serviceOnInternalIsAllowed() {
        given().get("/probe/service").then().statusCode(200);
    }

    @Test
    @TestSecurity(user = "ash", roles = "player")
    @JwtSecurity(claims = @Claim(key = "sub", value = PLAYER_ID))
    void validationErrorsUseTheCommonFormat() {
        given().contentType(ContentType.JSON).body("{\"name\": \"\"}").post("/probe/body")
                .then().statusCode(400)
                .body("error", equalTo("VALIDATION_ERROR"))
                .body("details.field", hasItem("name"))
                .body("details.code", hasItem("NOT_BLANK"));

        given().contentType(ContentType.JSON).body("{\"name\": \"x\"}").queryParam("size", 51).post("/probe/body")
                .then().statusCode(400).body("details.field", hasItem("size"));
    }

    @Test
    @TestSecurity(user = "ash", roles = "player")
    @JwtSecurity(claims = @Claim(key = "sub", value = PLAYER_ID))
    void unknownFieldsAndMalformedJsonAre400() {
        given().contentType(ContentType.JSON).body("{\"name\": \"x\", \"hp\": 999}").post("/probe/body")
                .then().statusCode(400)
                .body("error", equalTo("VALIDATION_ERROR"))
                .body("details.field", hasItem("hp"));

        given().contentType(ContentType.JSON).body("{\"name\": ").post("/probe/body")
                .then().statusCode(400).body("error", equalTo("VALIDATION_ERROR"));
    }

    @Test
    @TestSecurity(user = "ash", roles = "player")
    @JwtSecurity(claims = @Claim(key = "sub", value = PLAYER_ID))
    void apiExceptionKeepsItsCode() {
        given().get("/probe/conflict").then().statusCode(409).body("error", equalTo("BATTLE_IN_PROGRESS"));
    }

    @Test
    @TestSecurity(user = "ash", roles = "player")
    @JwtSecurity(claims = @Claim(key = "sub", value = PLAYER_ID))
    void unexpectedErrorsDoNotLeakDetails() {
        given().get("/probe/boom").then().statusCode(500)
                .body("error", equalTo("INTERNAL_ERROR"))
                .body(not(containsString("secret internal detail")));
    }

    @Test
    void unknownPathIs404() {
        given().get("/does-not-exist").then().statusCode(404).body("error", equalTo("NOT_FOUND"));
    }

    @Test
    void requestIdIsEchoedOrGenerated() {
        given().header("X-Request-Id", "abc-123").get("/probe/player")
                .then().header("X-Request-Id", "abc-123");
        given().get("/probe/player").then().header("X-Request-Id", notNullValue());
        given().header("X-Request-Id", "bad\nvalue").get("/probe/player")
                .then().header("X-Request-Id", not(containsString("bad")));
    }
}
