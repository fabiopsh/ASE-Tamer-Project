package it.unipi.ase.tamer.auth;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;

import io.quarkus.test.common.WithTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.jwt.build.Jwt;
import it.unipi.ase.tamer.auth.api.AuthorizeResource;
import it.unipi.ase.tamer.auth.service.TokenIssuer;
import jakarta.inject.Inject;
import java.time.Instant;
import java.util.Base64;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** INFRA-9: gateway auth_request adapter at GET /internal/auth/authorize. */
@QuarkusTest
@WithTestResource(JwtKeysTestResource.class)
class AuthorizeResourceTest {

    @Inject
    TokenIssuer issuer;

    @Test
    void validPlayerTokenOnPlayerRouteReturns204WithSubject() {
        UUID playerId = UUID.randomUUID();
        String token = issuer.playerToken(playerId, "ash_ketchum").value();

        given()
                .header("Authorization", "Bearer " + token)
                .header(AuthorizeResource.HEADER_ORIGINAL_METHOD, "GET")
                .header(AuthorizeResource.HEADER_ORIGINAL_URI, "/api/players/me")
                .get("/internal/auth/authorize")
                .then().statusCode(204)
                .header(AuthorizeResource.HEADER_AUTH_SUBJECT, equalTo(playerId.toString()))
                .header("Cache-Control", equalTo("no-store"))
                .header("Pragma", equalTo("no-cache"));
    }

    @Test
    void validPlayerTokenMatchesParameterizedRouteWithQueryString() {
        UUID playerId = UUID.randomUUID();
        String token = issuer.playerToken(playerId, "misty").value();

        given()
                .header("Authorization", "Bearer " + token)
                .header(AuthorizeResource.HEADER_ORIGINAL_METHOD, "GET")
                .header(AuthorizeResource.HEADER_ORIGINAL_URI, "/api/monsters/42?detail=full")
                .get("/internal/auth/authorize")
                .then().statusCode(204)
                .header(AuthorizeResource.HEADER_AUTH_SUBJECT, equalTo(playerId.toString()));
    }

    @Test
    void publicRouteReturns204WithoutSubject() {
        given()
                .header(AuthorizeResource.HEADER_ORIGINAL_METHOD, "GET")
                .header(AuthorizeResource.HEADER_ORIGINAL_URI, "/health")
                .get("/internal/auth/authorize")
                .then().statusCode(204)
                .header(AuthorizeResource.HEADER_AUTH_SUBJECT, nullValue())
                .header("Cache-Control", equalTo("no-store"));

        given()
                .header(AuthorizeResource.HEADER_ORIGINAL_METHOD, "GET")
                .header(AuthorizeResource.HEADER_ORIGINAL_URI, "/images/monsters/pyro.png")
                .get("/internal/auth/authorize")
                .then().statusCode(204)
                .header(AuthorizeResource.HEADER_AUTH_SUBJECT, nullValue());
    }

    @Test
    void missingOrMalformedAuthorizationHeaderIs401WithBearerChallenge() {
        given()
                .header(AuthorizeResource.HEADER_ORIGINAL_METHOD, "GET")
                .header(AuthorizeResource.HEADER_ORIGINAL_URI, "/api/players/me")
                .get("/internal/auth/authorize")
                .then().statusCode(401)
                .header("WWW-Authenticate", equalTo("Bearer"))
                .body("error", equalTo("UNAUTHORIZED"));

        given()
                .header("Authorization", "Basic dXNlcjpwYXNz")
                .header(AuthorizeResource.HEADER_ORIGINAL_METHOD, "GET")
                .header(AuthorizeResource.HEADER_ORIGINAL_URI, "/api/players/me")
                .get("/internal/auth/authorize")
                .then().statusCode(401)
                .header("WWW-Authenticate", equalTo("Bearer"))
                .body("error", equalTo("UNAUTHORIZED"));

        given()
                .header("Authorization", "Bearer not-a-jwt")
                .header(AuthorizeResource.HEADER_ORIGINAL_METHOD, "GET")
                .header(AuthorizeResource.HEADER_ORIGINAL_URI, "/api/players/me")
                .get("/internal/auth/authorize")
                .then().statusCode(401)
                .header("WWW-Authenticate", equalTo("Bearer"))
                .body("error", equalTo("UNAUTHORIZED"));
    }

    @Test
    void forgedExpiredUnsignedAndMissingSubTokensAre401() {
        String valid = issuer.playerToken(UUID.randomUUID(), "ash").value();
        String[] parts = valid.split("\\.");

        // Tampered payload
        String forgedPayload = Base64.getUrlEncoder().withoutPadding().encodeToString(
                ("{\"iss\":\"tamer-auth\",\"aud\":\"tamer-api\",\"sub\":\"" + UUID.randomUUID()
                        + "\",\"groups\":[\"player\"],\"exp\":" + (Instant.now().getEpochSecond() + 300) + "}")
                        .getBytes());
        authorize("Bearer " + parts[0] + "." + forgedPayload + "." + parts[2], "GET", "/api/players/me")
                .then().statusCode(401)
                .header("WWW-Authenticate", equalTo("Bearer"))
                .body("error", equalTo("UNAUTHORIZED"));

        // alg:none
        String noneHeader = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"alg\":\"none\",\"typ\":\"JWT\"}".getBytes());
        authorize("Bearer " + noneHeader + "." + parts[1] + ".", "GET", "/api/players/me")
                .then().statusCode(401)
                .header("WWW-Authenticate", equalTo("Bearer"))
                .body("error", equalTo("UNAUTHORIZED"));

        // Expired token
        String expired = Jwt.issuer(TokenIssuer.ISSUER)
                .audience(TokenIssuer.AUDIENCE_API)
                .subject(UUID.randomUUID().toString())
                .groups(Set.of("player"))
                .issuedAt(Instant.now().minusSeconds(600))
                .expiresAt(Instant.now().minusSeconds(300))
                .sign();
        authorize("Bearer " + expired, "GET", "/api/players/me")
                .then().statusCode(401)
                .header("WWW-Authenticate", equalTo("Bearer"))
                .body("error", equalTo("UNAUTHORIZED"));

        // Missing subject
        String missingSub = Jwt.issuer(TokenIssuer.ISSUER)
                .audience(TokenIssuer.AUDIENCE_API)
                .groups(Set.of("player"))
                .expiresIn(300)
                .sign();
        authorize("Bearer " + missingSub, "GET", "/api/players/me")
                .then().statusCode(401)
                .header("WWW-Authenticate", equalTo("Bearer"))
                .body("error", equalTo("UNAUTHORIZED"));
    }

    @Test
    void serviceTokenOrWrongAudienceOnPlayerRouteIs403() {
        String serviceToken = issuer.serviceToken("encounters").value();
        authorize("Bearer " + serviceToken, "GET", "/api/players/me")
                .then().statusCode(403)
                .body("error", equalTo("FORBIDDEN"));

        // Valid signature/issuer/role, but audience is tamer-internal instead of tamer-api
        String wrongAudPlayer = Jwt.issuer(TokenIssuer.ISSUER)
                .audience(TokenIssuer.AUDIENCE_INTERNAL)
                .subject(UUID.randomUUID().toString())
                .groups(Set.of(TokenIssuer.ROLE_PLAYER))
                .expiresIn(300)
                .sign();
        authorize("Bearer " + wrongAudPlayer, "GET", "/api/players/me")
                .then().statusCode(403)
                .body("error", equalTo("FORBIDDEN"));
    }

    @Test
    void internalManagementTraversalAndUnmappedRoutesAre403() {
        String playerToken = issuer.playerToken(UUID.randomUUID(), "ash").value();
        String serviceToken = issuer.serviceToken("encounters").value();

        authorize("Bearer " + serviceToken, "POST", "/internal/players")
                .then().statusCode(403).body("error", equalTo("FORBIDDEN"));
        authorize("Bearer " + playerToken, "GET", "/internal/auth/authorize")
                .then().statusCode(403).body("error", equalTo("FORBIDDEN"));
        authorize("Bearer " + playerToken, "GET", "/q/health")
                .then().statusCode(403).body("error", equalTo("FORBIDDEN"));
        authorize("Bearer " + playerToken, "GET", "/api/does-not-exist")
                .then().statusCode(403).body("error", equalTo("FORBIDDEN"));
        authorize("Bearer " + playerToken, "DELETE", "/api/players/me")
                .then().statusCode(403).body("error", equalTo("FORBIDDEN"));
        authorize("Bearer " + playerToken, "GET", "/api/players/me/../monsters")
                .then().statusCode(403).body("error", equalTo("FORBIDDEN"));
        authorize("Bearer " + playerToken, "GET", "/api/players/%2e%2e/monsters")
                .then().statusCode(403).body("error", equalTo("FORBIDDEN"));

        // Missing original headers
        given()
                .header("Authorization", "Bearer " + playerToken)
                .get("/internal/auth/authorize")
                .then().statusCode(403).body("error", equalTo("FORBIDDEN"));
    }

    private static io.restassured.response.Response authorize(String authHeader, String method, String uri) {
        return given()
                .header("Authorization", authHeader)
                .header(AuthorizeResource.HEADER_ORIGINAL_METHOD, method)
                .header(AuthorizeResource.HEADER_ORIGINAL_URI, uri)
                .get("/internal/auth/authorize");
    }
}
