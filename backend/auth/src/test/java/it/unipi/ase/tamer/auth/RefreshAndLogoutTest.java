package it.unipi.ase.tamer.auth;

import static io.restassured.RestAssured.given;
import static it.unipi.ase.tamer.auth.TestData.PASSWORD;
import static it.unipi.ase.tamer.auth.TestData.logout;
import static it.unipi.ase.tamer.auth.TestData.passwordGrant;
import static it.unipi.ase.tamer.auth.TestData.refreshGrant;
import static it.unipi.ase.tamer.auth.TestData.register;
import static it.unipi.ase.tamer.auth.TestData.uniqueUsername;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.InjectMock;
import io.quarkus.test.common.WithTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import io.smallrye.jwt.auth.principal.JWTParser;
import it.unipi.ase.tamer.auth.client.PlayersClient;
import it.unipi.ase.tamer.auth.domain.RefreshToken;
import it.unipi.ase.tamer.auth.service.RefreshTokenCleanup;
import it.unipi.ase.tamer.auth.service.TokenService;
import jakarta.inject.Inject;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.junit.jupiter.api.Test;

/** AUTH-7: 300 s access tokens, refresh token rotation, reuse detection, logout, and cleanup. */
@QuarkusTest
@WithTestResource(JwtKeysTestResource.class)
class RefreshAndLogoutTest {

    @InjectMock
    @RestClient
    PlayersClient players;

    @Inject
    JWTParser parser;

    @Inject
    RefreshTokenCleanup cleanup;

    @Test
    void passwordLoginReturns300sAccessTokenAndStoresOnlyRefreshHash() throws Exception {
        String username = uniqueUsername();
        String playerId = register(username, PASSWORD).then().statusCode(201).extract().path("player_id");

        Response login = passwordGrant(username, PASSWORD)
                .then().statusCode(200)
                .header("Cache-Control", equalTo("no-store"))
                .header("Pragma", equalTo("no-cache"))
                .body("token_type", equalTo("Bearer"))
                .body("expires_in", equalTo(300))
                .body("scope", equalTo("player"))
                .body("refresh_token", notNullValue())
                .extract().response();

        String accessToken = login.path("access_token");
        String refreshToken = login.path("refresh_token");

        JsonWebToken jwt = parser.parse(accessToken);
        assertEquals(300, jwt.getExpirationTime() - jwt.getIssuedAtTime());
        assertEquals(playerId, jwt.getSubject());
        assertEquals(username, jwt.getName());

        // Raw refresh token is never stored; only its 64-hex SHA-256 hash exists in DB
        assertTrue(RefreshToken.findByHash(refreshToken).isEmpty());
        String hash = TokenService.hashRefreshToken(refreshToken);
        assertTrue(hash.matches("[0-9a-f]{64}"));
        RefreshToken stored = RefreshToken.findByHash(hash).orElseThrow();
        assertEquals(playerId, stored.playerId.toString());
        assertFalse(stored.isRevoked());
        assertEquals(Duration.ofDays(7), Duration.between(stored.createdAt, stored.expiresAt));
    }

    @Test
    void clientCredentialsGrantDoesNotIssueRefreshToken() {
        given().contentType(ContentType.URLENC)
                .formParam("grant_type", "client_credentials")
                .formParam("client_id", "players")
                .formParam("client_secret", "test-only-secret")
                .post("/internal/auth/token")
                .then().statusCode(200)
                .body("expires_in", equalTo(300))
                .body("refresh_token", nullValue());
    }

    @Test
    void rotationIssuesNewPairRevokesOldAndPreservesFamilyAndExpiry() throws Exception {
        String username = uniqueUsername();
        String playerId = register(username, PASSWORD).then().statusCode(201).extract().path("player_id");
        String refresh1 = passwordGrant(username, PASSWORD).then().statusCode(200).extract().path("refresh_token");
        RefreshToken row1Before = RefreshToken.findByHash(TokenService.hashRefreshToken(refresh1)).orElseThrow();

        Response rotated = refreshGrant(refresh1)
                .then().statusCode(200)
                .header("Cache-Control", equalTo("no-store"))
                .header("Pragma", equalTo("no-cache"))
                .body("token_type", equalTo("Bearer"))
                .body("expires_in", equalTo(300))
                .body("scope", equalTo("player"))
                .body("access_token", notNullValue())
                .body("refresh_token", notNullValue())
                .extract().response();

        String access2 = rotated.path("access_token");
        String refresh2 = rotated.path("refresh_token");
        assertNotEquals(refresh1, refresh2);

        JsonWebToken jwt = parser.parse(access2);
        assertEquals(playerId, jwt.getSubject());
        assertEquals(username, jwt.getName());
        assertEquals(300, jwt.getExpirationTime() - jwt.getIssuedAtTime());

        RefreshToken.getEntityManager().clear();
        RefreshToken row1After = RefreshToken.findByHash(TokenService.hashRefreshToken(refresh1)).orElseThrow();
        RefreshToken row2 = RefreshToken.findByHash(TokenService.hashRefreshToken(refresh2)).orElseThrow();
        assertTrue(row1After.isRevoked());
        assertNotNull(row1After.revokedAt);
        assertFalse(row2.isRevoked());
        assertEquals(row1Before.familyId, row2.familyId);
        assertEquals(row1Before.expiresAt.truncatedTo(ChronoUnit.MILLIS),
                row2.expiresAt.truncatedTo(ChronoUnit.MILLIS));
    }

    @Test
    void reuseOfConsumedTokenRevokesEntireFamilyAndCommitsDespite400() {
        String username = uniqueUsername();
        register(username, PASSWORD).then().statusCode(201);
        String refresh1 = passwordGrant(username, PASSWORD).then().statusCode(200).extract().path("refresh_token");
        String refresh2 = refreshGrant(refresh1).then().statusCode(200).extract().path("refresh_token");

        // Replaying the already-consumed refresh1 triggers reuse detection and revokes refresh2
        refreshGrant(refresh1)
                .then().statusCode(400)
                .header("Cache-Control", equalTo("no-store"))
                .body("error", equalTo("invalid_grant"));

        // Verify the revocation was committed even though the previous call returned 400
        RefreshToken row2 = RefreshToken.findByHash(TokenService.hashRefreshToken(refresh2)).orElseThrow();
        assertTrue(row2.isRevoked());
        assertNotNull(row2.revokedAt);

        refreshGrant(refresh2)
                .then().statusCode(400)
                .body("error", equalTo("invalid_grant"));
    }

    @Test
    void independentSessionRemainsUsableWhenAnotherFamilyIsRevoked() {
        String username = uniqueUsername();
        register(username, PASSWORD).then().statusCode(201);

        String sessionA1 = passwordGrant(username, PASSWORD).then().statusCode(200).extract().path("refresh_token");
        String sessionB1 = passwordGrant(username, PASSWORD).then().statusCode(200).extract().path("refresh_token");

        // Trigger reuse on session A
        String sessionA2 = refreshGrant(sessionA1).then().statusCode(200).extract().path("refresh_token");
        refreshGrant(sessionA1).then().statusCode(400).body("error", equalTo("invalid_grant"));
        refreshGrant(sessionA2).then().statusCode(400).body("error", equalTo("invalid_grant"));

        // Session B is a separate family and still refreshes cleanly
        refreshGrant(sessionB1).then().statusCode(200).body("refresh_token", notNullValue());
    }

    @Test
    void unknownAndExpiredRefreshTokensProduceIdenticalInvalidGrant() {
        String username = uniqueUsername();
        register(username, PASSWORD).then().statusCode(201);
        String expiredToken = passwordGrant(username, PASSWORD).then().statusCode(200).extract().path("refresh_token");
        String expiredHash = TokenService.hashRefreshToken(expiredToken);

        QuarkusTransaction.requiringNew().run(() ->
                RefreshToken.update("expiresAt = ?1 where tokenHash = ?2",
                        Instant.now().minusSeconds(60), expiredHash));

        Map<String, Object> unknownError = refreshGrant("completely-unknown-token")
                .then().statusCode(400).extract().as(Map.class);
        Map<String, Object> expiredError = refreshGrant(expiredToken)
                .then().statusCode(400).extract().as(Map.class);

        assertEquals("invalid_grant", unknownError.get("error"));
        assertEquals(unknownError, expiredError);
    }

    @Test
    void logoutRevokesWholeFamilyIdempotentlyAndValidatesMissingParam() {
        String username = uniqueUsername();
        register(username, PASSWORD).then().statusCode(201);
        String refresh1 = passwordGrant(username, PASSWORD).then().statusCode(200).extract().path("refresh_token");
        String refresh2 = refreshGrant(refresh1).then().statusCode(200).extract().path("refresh_token");

        // Missing or blank refresh_token -> 400 invalid_request
        given().contentType(ContentType.URLENC).post("/api/auth/logout")
                .then().statusCode(400).body("error", equalTo("invalid_request"));
        logout("   ").then().statusCode(400).body("error", equalTo("invalid_request"));

        // Submitting an older rotated token still revokes the whole family (including refresh2)
        logout(refresh1).then().statusCode(204).header("Cache-Control", equalTo("no-store"));
        refreshGrant(refresh2).then().statusCode(400).body("error", equalTo("invalid_grant"));

        // Idempotent 204 for already-revoked, unknown, and expired tokens
        logout(refresh2).then().statusCode(204);
        logout("unknown-token-value").then().statusCode(204);

        String expiredToken = passwordGrant(username, PASSWORD).then().statusCode(200).extract().path("refresh_token");
        String expiredHash = TokenService.hashRefreshToken(expiredToken);
        QuarkusTransaction.requiringNew().run(() ->
                RefreshToken.update("expiresAt = ?1 where tokenHash = ?2",
                        Instant.now().minusSeconds(60), expiredHash));
        logout(expiredToken).then().statusCode(204);
    }

    @Test
    void concurrentRefreshOnSameTokenAllowsAtMostOneAndRevokesFamilyOnCollision() throws Exception {
        String username = uniqueUsername();
        register(username, PASSWORD).then().statusCode(201);
        String refresh1 = passwordGrant(username, PASSWORD).then().statusCode(200).extract().path("refresh_token");

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
            Future<Response> f1 = pool.submit(() -> {
                ready.countDown();
                start.await();
                return refreshGrant(refresh1);
            });
            Future<Response> f2 = pool.submit(() -> {
                ready.countDown();
                start.await();
                return refreshGrant(refresh1);
            });
            ready.await();
            start.countDown();

            Response r1 = f1.get();
            Response r2 = f2.get();
            int s1 = r1.statusCode();
            int s2 = r2.statusCode();

            assertTrue((s1 == 200 && s2 == 400) || (s1 == 400 && s2 == 200),
                    "Expected one 200 and one 400, got " + s1 + " and " + s2);

            String winnerRefresh = s1 == 200 ? r1.path("refresh_token") : r2.path("refresh_token");
            // Because the losing concurrent call hit reuse detection under the account lock, winner is revoked too
            refreshGrant(winnerRefresh).then().statusCode(400).body("error", equalTo("invalid_grant"));
        }
    }

    @Test
    void concurrentRefreshAndLogoutNeverLeavesUsableDescendant() throws Exception {
        String username = uniqueUsername();
        register(username, PASSWORD).then().statusCode(201);
        String refresh1 = passwordGrant(username, PASSWORD).then().statusCode(200).extract().path("refresh_token");

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
            Future<Response> refreshFuture = pool.submit(() -> {
                ready.countDown();
                start.await();
                return refreshGrant(refresh1);
            });
            Future<Response> logoutFuture = pool.submit(() -> {
                ready.countDown();
                start.await();
                return logout(refresh1);
            });
            ready.await();
            start.countDown();

            Response refreshRes = refreshFuture.get();
            Response logoutRes = logoutFuture.get();

            assertEquals(204, logoutRes.statusCode());
            assertTrue(refreshRes.statusCode() == 200 || refreshRes.statusCode() == 400);
            if (refreshRes.statusCode() == 200) {
                String descendant = refreshRes.path("refresh_token");
                refreshGrant(descendant).then().statusCode(400).body("error", equalTo("invalid_grant"));
            }
        }
    }

    @Test
    void scheduledCleanupDeletesExpiredRowsAndKeepsUnexpiredRevokedRows() {
        String username = uniqueUsername();
        register(username, PASSWORD).then().statusCode(201);

        // Unexpired revoked (r1) + unexpired active (r2)
        String r1 = passwordGrant(username, PASSWORD).then().statusCode(200).extract().path("refresh_token");
        String r2 = refreshGrant(r1).then().statusCode(200).extract().path("refresh_token");
        String h1 = TokenService.hashRefreshToken(r1);
        String h2 = TokenService.hashRefreshToken(r2);

        // Expired active (r3)
        String r3 = passwordGrant(username, PASSWORD).then().statusCode(200).extract().path("refresh_token");
        String h3 = TokenService.hashRefreshToken(r3);
        // Expired revoked (r4)
        String r4 = passwordGrant(username, PASSWORD).then().statusCode(200).extract().path("refresh_token");
        String h4 = TokenService.hashRefreshToken(r4);

        Instant past = Instant.now().minusSeconds(120);
        QuarkusTransaction.requiringNew().run(() -> {
            RefreshToken.update("expiresAt = ?1 where tokenHash = ?2", past, h3);
            RefreshToken.update("expiresAt = ?1, revoked = true, revokedAt = ?1 where tokenHash = ?2", past, h4);
        });

        long deleted = cleanup.purgeExpired();
        assertTrue(deleted >= 2);
        assertTrue(RefreshToken.findByHash(h3).isEmpty());
        assertTrue(RefreshToken.findByHash(h4).isEmpty());
        assertTrue(RefreshToken.findByHash(h1).isPresent(), "revoked unexpired token must stay for reuse detection");
        assertTrue(RefreshToken.findByHash(h2).isPresent());
    }
}
