package it.unipi.ase.tamer.players.api;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.quarkus.test.security.jwt.Claim;
import io.quarkus.test.security.jwt.JwtSecurity;
import io.restassured.http.ContentType;
import it.unipi.ase.tamer.players.api.dto.StarterEntry;
import it.unipi.ase.tamer.players.client.EncountersClient;
import it.unipi.ase.tamer.players.client.MonsterCandidate;
import it.unipi.ase.tamer.players.client.MonstersClient;
import it.unipi.ase.tamer.players.client.StarterGrantRequest;
import it.unipi.ase.tamer.players.domain.Player;
import it.unipi.ase.tamer.players.service.PlayersService;
import jakarta.inject.Inject;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
class PlayersResourceTest {

    private static final String PLAYER_ID = "11111111-1111-4111-8111-111111111111";

    @Inject
    PlayersService playersService;

    @InjectMock
    @RestClient
    MonstersClient monsters;

    @InjectMock
    @RestClient
    EncountersClient encounters;

    @BeforeEach
    void setUp() {
        QuarkusTransaction.requiringNew().run(() -> Player.deleteAll());
        List<MonsterCandidate> candidates = new ArrayList<>();
        for (int id = 1; id <= 12; id++) {
            candidates.add(new MonsterCandidate(id, "dream", "common"));
        }
        when(monsters.candidates("common")).thenReturn(candidates);
        doNothing().when(encounters).openRun(any());
    }

    @Test
    @TestSecurity(user = "auth", roles = "service")
    void createProfileIsIdempotentAndPreservesPool() {
        UUID pid = UUID.fromString(PLAYER_ID);
        String body = "{\"player_id\": \"" + pid + "\"}";

        String firstRunId = given().contentType(ContentType.JSON).body(body)
                .post("/internal/players")
                .then().statusCode(201)
                .body("player_id", equalTo(pid.toString()))
                .body("starters_chosen", equalTo(false))
                .extract().path("current_run_id");

        List<Integer> firstPool = playersService.starterPool(pid).pool();
        assertEquals(10, firstPool.size());

        String secondRunId = given().contentType(ContentType.JSON).body(body)
                .post("/internal/players")
                .then().statusCode(200)
                .body("player_id", equalTo(pid.toString()))
                .extract().path("current_run_id");

        assertEquals(firstRunId, secondRunId);
        assertEquals(firstPool, playersService.starterPool(pid).pool());
    }

    @Test
    void profileWithoutTokenReturns401WithBearerChallenge() {
        given().get("/api/players/me")
                .then().statusCode(401)
                .header("WWW-Authenticate", containsString("Bearer"))
                .body("error", equalTo("UNAUTHORIZED"));
    }

    @Test
    @TestSecurity(user = "ash", roles = "player")
    @JwtSecurity(claims = @Claim(key = "sub", value = PLAYER_ID))
    void profileReturns404WhenMissingAnd200WhenCreated() {
        given().get("/api/players/me")
                .then().statusCode(404)
                .body("error", equalTo("NOT_FOUND"));

        playersService.createProfile(UUID.fromString(PLAYER_ID));

        given().get("/api/players/me")
                .then().statusCode(200)
                .body("player_id", equalTo(PLAYER_ID))
                .body("avatar", nullValue())
                .body("starters_chosen", equalTo(false));
    }

    @Test
    @TestSecurity(user = "ash", roles = "player")
    @JwtSecurity(claims = @Claim(key = "sub", value = PLAYER_ID))
    void patchProfileUpdatesAndValidatesDisplayNameAndAvatar() {
        playersService.createProfile(UUID.fromString(PLAYER_ID));

        given().contentType(ContentType.JSON)
                .body("{\"display_name\": \"  Ash_Ketchum-1  \", \"avatar\": \"ranger\"}")
                .patch("/api/players/me")
                .then().statusCode(200)
                .body("display_name", equalTo("Ash_Ketchum-1"))
                .body("avatar", equalTo("ranger"));

        // Blank avatar clears avatar while keeping display_name intact.
        given().contentType(ContentType.JSON)
                .body("{\"avatar\": \"   \"}")
                .patch("/api/players/me")
                .then().statusCode(200)
                .body("display_name", equalTo("Ash_Ketchum-1"))
                .body("avatar", nullValue());

        // Set avatar again, then clear with explicit null.
        given().contentType(ContentType.JSON)
                .body("{\"avatar\": \"sage\"}")
                .patch("/api/players/me")
                .then().statusCode(200)
                .body("avatar", equalTo("sage"));

        given().contentType(ContentType.JSON)
                .body("{\"avatar\": null}")
                .patch("/api/players/me")
                .then().statusCode(200)
                .body("display_name", equalTo("Ash_Ketchum-1"))
                .body("avatar", nullValue());

        // Invalid display_name (too short, invalid characters, null).
        given().contentType(ContentType.JSON)
                .body("{\"display_name\": \"ab\"}")
                .patch("/api/players/me")
                .then().statusCode(400)
                .body("error", equalTo("VALIDATION_ERROR"));

        given().contentType(ContentType.JSON)
                .body("{\"display_name\": \"bad!name\"}")
                .patch("/api/players/me")
                .then().statusCode(400)
                .body("error", equalTo("VALIDATION_ERROR"));

        given().contentType(ContentType.JSON)
                .body("{\"display_name\": null}")
                .patch("/api/players/me")
                .then().statusCode(400)
                .body("error", equalTo("VALIDATION_ERROR"));

        // Invalid avatar outside allowlist.
        given().contentType(ContentType.JSON)
                .body("{\"avatar\": \"wizard\"}")
                .patch("/api/players/me")
                .then().statusCode(400)
                .body("error", equalTo("VALIDATION_ERROR"));

        // Empty patch object.
        given().contentType(ContentType.JSON)
                .body("{}")
                .patch("/api/players/me")
                .then().statusCode(400)
                .body("error", equalTo("VALIDATION_ERROR"));
    }

    @Test
    @TestSecurity(user = "ash", roles = "player")
    @JwtSecurity(claims = @Claim(key = "sub", value = PLAYER_ID))
    void starterPoolReturnsTenDistinctCommonsAndIsStableAcrossCalls() {
        playersService.createProfile(UUID.fromString(PLAYER_ID));

        List<Integer> first = given().get("/api/players/me/starters")
                .then().statusCode(200)
                .body("pool", hasSize(10))
                .body("chosen", equalTo(false))
                .extract().jsonPath().getList("pool", Integer.class);

        List<Integer> second = given().get("/api/players/me/starters")
                .then().statusCode(200)
                .extract().jsonPath().getList("pool", Integer.class);

        assertEquals(10, new HashSet<>(first).size());
        assertTrue(first.stream().allMatch(id -> id >= 1 && id <= 12));
        assertEquals(first, second);
    }

    @Test
    @TestSecurity(user = "ash", roles = "player")
    @JwtSecurity(claims = @Claim(key = "sub", value = PLAYER_ID))
    void chooseStartersValidatesHandles503RecoveryAndBlocksSecondChoice() {
        UUID pid = UUID.fromString(PLAYER_ID);
        playersService.createProfile(pid);
        List<Integer> pool = playersService.starterPool(pid).pool();
        int a = pool.get(0);
        int b = pool.get(1);
        int c = pool.get(2);

        // Two ids -> 400
        given().contentType(ContentType.JSON)
                .body("{\"monster_ids\": [" + a + ", " + b + "]}")
                .post("/api/players/me/starters")
                .then().statusCode(400)
                .body("error", equalTo("INVALID_STARTERS"));

        // Duplicate ids -> 400
        given().contentType(ContentType.JSON)
                .body("{\"monster_ids\": [" + a + ", " + a + ", " + b + "]}")
                .post("/api/players/me/starters")
                .then().statusCode(400)
                .body("error", equalTo("INVALID_STARTERS"));

        // Outside pool -> 400
        given().contentType(ContentType.JSON)
                .body("{\"monster_ids\": [" + a + ", " + b + ", 999]}")
                .post("/api/players/me/starters")
                .then().statusCode(400)
                .body("error", equalTo("INVALID_STARTERS"));

        String validBody = "{\"monster_ids\": [" + a + ", " + b + ", " + c + "]}";

        // Monsters 503 leaves starters_chosen = false
        when(monsters.createStarters(eq(pid), any(StarterGrantRequest.class)))
                .thenThrow(new RuntimeException("monsters down"));

        given().contentType(ContentType.JSON).body(validBody)
                .post("/api/players/me/starters")
                .then().statusCode(503)
                .body("error", equalTo("DEPENDENCY_UNAVAILABLE"));

        given().get("/api/players/me")
                .then().statusCode(200)
                .body("starters_chosen", equalTo(false));

        // Retry after monsters recovers succeeds with 201
        when(monsters.createStarters(eq(pid), any(StarterGrantRequest.class)))
                .thenReturn(List.of(
                        new StarterEntry(UUID.randomUUID(), a, 1),
                        new StarterEntry(UUID.randomUUID(), b, 1),
                        new StarterEntry(UUID.randomUUID(), c, 1)));

        given().contentType(ContentType.JSON).body(validBody)
                .post("/api/players/me/starters")
                .then().statusCode(201)
                .body("starters", hasSize(3));

        given().get("/api/players/me")
                .then().statusCode(200)
                .body("starters_chosen", equalTo(true));

        // Second choice -> 409
        given().contentType(ContentType.JSON).body(validBody)
                .post("/api/players/me/starters")
                .then().statusCode(409)
                .body("error", equalTo("STARTERS_ALREADY_CHOSEN"));
    }

    @Test
    @TestSecurity(user = "encounters", roles = "service")
    void newRunRotatesOnCurrentRunAndNoOpsOnStaleRun() {
        UUID pid = UUID.fromString(PLAYER_ID);
        UUID initialRunId = playersService.createProfile(pid).player().currentRunId();

        // Stale run id -> no-op
        given().contentType(ContentType.JSON)
                .body("{\"ended_run_id\": \"" + UUID.randomUUID() + "\"}")
                .post("/internal/players/" + pid + "/new-run")
                .then().statusCode(200)
                .body("current_run_id", equalTo(initialRunId.toString()));

        // Current run id -> rotates to new run id
        String rotatedRunId = given().contentType(ContentType.JSON)
                .body("{\"ended_run_id\": \"" + initialRunId + "\"}")
                .post("/internal/players/" + pid + "/new-run")
                .then().statusCode(200)
                .body("current_run_id", not(equalTo(initialRunId.toString())))
                .extract().path("current_run_id");

        assertNotEquals(initialRunId.toString(), rotatedRunId);

        // Repeating with old run id is a no-op
        given().contentType(ContentType.JSON)
                .body("{\"ended_run_id\": \"" + initialRunId + "\"}")
                .post("/internal/players/" + pid + "/new-run")
                .then().statusCode(200)
                .body("current_run_id", equalTo(rotatedRunId));
    }

    @Test
    @TestSecurity(user = "ash", roles = "player")
    @JwtSecurity(claims = @Claim(key = "sub", value = PLAYER_ID))
    void playerTokenOnInternalEndpointsIs403() {
        given().contentType(ContentType.JSON)
                .body("{\"player_id\": \"" + PLAYER_ID + "\"}")
                .post("/internal/players")
                .then().statusCode(403)
                .body("error", equalTo("FORBIDDEN"));

        given().contentType(ContentType.JSON)
                .body("{\"ended_run_id\": \"" + UUID.randomUUID() + "\"}")
                .post("/internal/players/" + PLAYER_ID + "/new-run")
                .then().statusCode(403)
                .body("error", equalTo("FORBIDDEN"));
    }

    @Test
    @TestSecurity(user = "auth", roles = "service")
    void serviceTokenOnPlayerEndpointIs403() {
        given().get("/api/players/me")
                .then().statusCode(403)
                .body("error", equalTo("FORBIDDEN"));
    }
}
