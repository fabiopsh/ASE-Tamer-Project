package it.unipi.ase.tamer.auth.security;

import static io.restassured.RestAssured.given;
import static it.unipi.ase.tamer.auth.TestData.PASSWORD;
import static it.unipi.ase.tamer.auth.TestData.passwordGrant;
import static it.unipi.ase.tamer.auth.TestData.register;
import static it.unipi.ase.tamer.auth.TestData.uniqueUsername;
import static org.hamcrest.Matchers.equalTo;

import io.quarkus.test.InjectMock;
import io.quarkus.test.common.WithTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.jwt.build.Jwt;
import it.unipi.ase.tamer.auth.JwtKeysTestResource;
import it.unipi.ase.tamer.auth.client.PlayersClient;
import it.unipi.ase.tamer.auth.service.TokenIssuer;
import jakarta.inject.Inject;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.junit.jupiter.api.Test;

/** Tokens issued by auth are accepted, tampered/expired/wrong-role tokens are not (INFRA-7, AUTH-3). */
@QuarkusTest
@WithTestResource(JwtKeysTestResource.class)
class TokenVerificationTest {

    @InjectMock
    @RestClient
    PlayersClient players;

    @Inject
    TokenIssuer issuer;

    @Test
    void playerTokenIsAccepted() {
        String username = uniqueUsername();
        String playerId = register(username, PASSWORD).then().statusCode(201).extract().path("player_id");
        String token = passwordGrant(username, PASSWORD).then().statusCode(200).extract().path("access_token");

        given().auth().oauth2(token).get("/probe/player").then().statusCode(200).body(equalTo(playerId));
    }

    @Test
    void missingTokenIs401() {
        given().get("/probe/player").then().statusCode(401)
                .header("WWW-Authenticate", "Bearer")
                .body("error", equalTo("UNAUTHORIZED"));
    }

    @Test
    void tamperedTokenIs401() {
        String token = issuer.playerToken(UUID.randomUUID(), "ash").value();
        String[] parts = token.split("\\.");
        String forgedPayload = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(
                ("{\"iss\":\"tamer-auth\",\"aud\":\"tamer-api\",\"sub\":\"" + UUID.randomUUID()
                        + "\",\"groups\":[\"player\"],\"exp\":" + (Instant.now().getEpochSecond() + 600) + "}").getBytes());
        String tampered = parts[0] + "." + forgedPayload + "." + parts[2];

        given().auth().oauth2(tampered).get("/probe/player").then().statusCode(401)
                .body("error", equalTo("UNAUTHORIZED"));
    }

    @Test
    void unsignedTokenIs401() {
        String token = issuer.playerToken(UUID.randomUUID(), "ash").value();
        String[] parts = token.split("\\.");
        String none = java.util.Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"alg\":\"none\",\"typ\":\"JWT\"}".getBytes());

        given().auth().oauth2(none + "." + parts[1] + ".").get("/probe/player").then().statusCode(401);
    }

    @Test
    void expiredTokenIs401() {
        String expired = Jwt.issuer(TokenIssuer.ISSUER).audience(TokenIssuer.AUDIENCE_API)
                .subject(UUID.randomUUID().toString()).groups(Set.of("player"))
                .issuedAt(Instant.now().minusSeconds(7200)).expiresAt(Instant.now().minusSeconds(3600))
                .sign();

        given().auth().oauth2(expired).get("/probe/player").then().statusCode(401);
    }

    @Test
    void wrongIssuerIs401() {
        String token = Jwt.issuer("someone-else").audience(TokenIssuer.AUDIENCE_API)
                .subject(UUID.randomUUID().toString()).groups(Set.of("player")).sign();

        given().auth().oauth2(token).get("/probe/player").then().statusCode(401);
    }

    @Test
    void wrongAudienceIs401() {
        String token = Jwt.issuer(TokenIssuer.ISSUER).audience("other-api")
                .subject(UUID.randomUUID().toString()).groups(Set.of("player")).sign();

        given().auth().oauth2(token).get("/probe/player").then().statusCode(401);
    }

    @Test
    void playerTokenOnInternalEndpointIs403() {
        String token = issuer.playerToken(UUID.randomUUID(), "ash").value();

        given().auth().oauth2(token).get("/probe/service").then().statusCode(403)
                .body("error", equalTo("FORBIDDEN"));
    }

    @Test
    void serviceTokenOnPlayerEndpointIs403() {
        String token = issuer.serviceToken("encounters").value();

        given().auth().oauth2(token).get("/probe/player").then().statusCode(403);
        given().auth().oauth2(token).get("/probe/service").then().statusCode(200);
    }
}
