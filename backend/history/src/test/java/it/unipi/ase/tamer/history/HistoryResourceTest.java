package it.unipi.ase.tamer.history;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.quarkus.test.security.jwt.Claim;
import io.quarkus.test.security.jwt.JwtSecurity;
import io.restassured.http.ContentType;
import io.restassured.response.ValidatableResponse;
import it.unipi.ase.tamer.history.domain.EncounterRecord;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * HI-1: store (internal), list and detail (public). A test user with both roles stands for the
 * encounters service and for the player at the same time; the 403 test uses a player-only user.
 */
@QuarkusTest
class HistoryResourceTest {

    static final String PLAYER_A = "7c0e8a52-3f43-4a8f-9a3e-2f1d1b2b9c10";
    static final String PLAYER_B = "0b9d4c1e-62f7-4d0a-8f61-5d6e9a1c2b33";
    static final String RUN_1 = "11111111-1111-4111-8111-111111111111";
    static final String RUN_2 = "22222222-2222-4222-8222-222222222222";
    static final Instant T0 = Instant.parse("2026-11-06T10:00:00Z");

    @BeforeEach
    void cleanTable() {
        QuarkusTransaction.requiringNew().run(() -> EncounterRecord.getEntityManager()
                .createNativeQuery("DELETE FROM encounter_records").executeUpdate());
    }

    // ---------- helpers ----------

    /** A valid record whose ended_at is T0 + minutesAfter minutes. */
    static Map<String, Object> record(String encounterId, String playerId, String runId, String outcome,
                                      int minutesAfter) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("encounter_id", encounterId);
        body.put("player_id", playerId);
        body.put("run_id", runId);
        body.put("template", Map.of("template_id", 12, "name", "Mossy Hollow", "stage", 1,
                "element", "echo", "is_boss", false));
        body.put("wild", Map.of("monster_id", 51, "name", "Mossling", "level", 2, "rarity", "common",
                "element", "echo"));
        body.put("team_snapshot", List.of(Map.of("position", 1, "owned_id", UUID.randomUUID().toString(),
                "monster_id", 3, "name", "Emberling", "level", 3, "stats", Map.of("max_hp", 30, "attack", 9))));
        body.put("outcome", outcome);
        body.put("reward", Map.of("owned_id", UUID.randomUUID().toString(), "monster_id", 51, "level", 2));
        body.put("xp_gained", 20);
        body.put("turns", List.of(Map.of("turn", 1, "events", List.of(Map.of("type", "damage", "target_side", "wild",
                "amount", 7)))));
        body.put("rng_seed", "-4512345678901234567");
        body.put("engine_version", 1);
        body.put("started_at", T0.plusSeconds(minutesAfter * 60L - 30).toString());
        body.put("ended_at", T0.plusSeconds(minutesAfter * 60L).toString());
        return body;
    }

    static ValidatableResponse post(Map<String, Object> body) {
        return given().contentType(ContentType.JSON).body(body).post("/internal/history").then();
    }

    static long rows() {
        return QuarkusTransaction.requiringNew().call(() -> ((Number) EncounterRecord.getEntityManager()
                .createNativeQuery("SELECT count(*) FROM encounter_records").getSingleResult()).longValue());
    }

    static String id() {
        return UUID.randomUUID().toString();
    }

    // ---------- store ----------

    @Test
    @TestSecurity(user = "encounters", roles = "service")
    void insertIsIdempotent() {
        String encounterId = id();
        Map<String, Object> body = record(encounterId, PLAYER_A, RUN_1, "victory", 5);

        post(body).statusCode(201)
                .body("encounter_id", equalTo(encounterId))
                .body("outcome", equalTo("victory"))
                .body("template.name", equalTo("Mossy Hollow"))
                .body("wild.name", equalTo("Mossling"));
        post(body).statusCode(200).body("encounter_id", equalTo(encounterId));
        // a retry with different content changes nothing: the first write wins
        Map<String, Object> other = new LinkedHashMap<>(body);
        other.put("outcome", "defeat");
        post(other).statusCode(200).body("outcome", equalTo("victory"));

        org.junit.jupiter.api.Assertions.assertEquals(1, rows());
    }

    @Test
    @TestSecurity(user = "encounters", roles = "service")
    void minimalInterruptedRecordIsAccepted() {
        Map<String, Object> body = record(id(), PLAYER_A, RUN_1, "interrupted", 1);
        body.remove("reward");
        body.remove("xp_gained");
        body.remove("rng_seed");
        body.remove("turns");
        body.remove("team_snapshot");

        post(body).statusCode(201).body("reward", nullValue()).body("xp_gained", equalTo(0));
    }

    @Test
    @TestSecurity(user = "ash", roles = {"player", "service"})
    @JwtSecurity(claims = @Claim(key = "sub", value = PLAYER_A))
    void storedJsonIsReturnedUntouched() {
        String encounterId = id();
        Map<String, Object> body = record(encounterId, PLAYER_A, RUN_1, "victory", 5);
        // keys that are not snake_case and nested structures must survive the round trip
        body.put("team_snapshot", List.of(Map.of("position", 1, "someCamelKey", Map.of("innerKey", List.of(1, 2)))));
        body.put("turns", List.of(Map.of("turn", 1, "events", List.of(Map.of("kindOfEvent", "x")))));
        post(body).statusCode(201);

        given().get("/api/history/" + encounterId).then().statusCode(200)
                .body("team_snapshot[0].someCamelKey.innerKey", contains(1, 2))
                .body("turns[0].events[0].kindOfEvent", equalTo("x"))
                .body("rng_seed", equalTo("-4512345678901234567"))
                .body("engine_version", equalTo(1))
                .body("template.is_boss", equalTo(false))
                .body("reward.monster_id", equalTo(51))
                .body("xp_gained", equalTo(20))
                .body("started_at", equalTo(T0.plusSeconds(270).toString()))
                .body("ended_at", equalTo(T0.plusSeconds(300).toString()))
                .body("player_id", nullValue());
    }

    @Test
    @TestSecurity(user = "ash", roles = "player")
    @JwtSecurity(claims = @Claim(key = "sub", value = PLAYER_A))
    void internalEndpointWithPlayerTokenIs403() {
        post(record(id(), PLAYER_A, RUN_1, "victory", 1)).statusCode(403).body("error", equalTo("FORBIDDEN"));
        org.junit.jupiter.api.Assertions.assertEquals(0, rows());
    }

    @Test
    void internalEndpointWithoutTokenIs401() {
        post(record(id(), PLAYER_A, RUN_1, "victory", 1)).statusCode(401).body("error", equalTo("UNAUTHORIZED"));
    }

    @Test
    void publicEndpointsWithoutTokenAre401() {
        given().get("/api/history").then().statusCode(401).body("error", equalTo("UNAUTHORIZED"));
        given().get("/api/history/" + id()).then().statusCode(401).body("error", equalTo("UNAUTHORIZED"));
    }

    @Test
    @TestSecurity(user = "encounters", roles = "service")
    @JwtSecurity(claims = @Claim(key = "sub", value = PLAYER_A))
    void servicePlayerEndpointsAreNotOpenToServiceTokens() {
        // the service role alone must not read the player API
        given().get("/api/history").then().statusCode(403);
    }

    @Test
    @TestSecurity(user = "encounters", roles = "service")
    void invalidOutcomeInBodyIs400() {
        Map<String, Object> body = record(id(), PLAYER_A, RUN_1, "draw", 1);
        post(body).statusCode(400).body("error", equalTo("VALIDATION_ERROR"));
        org.junit.jupiter.api.Assertions.assertEquals(0, rows());
    }

    @Test
    @TestSecurity(user = "encounters", roles = "service")
    void tooManyTurnsIs400() {
        Map<String, Object> body = record(id(), PLAYER_A, RUN_1, "victory", 1);
        List<Object> turns = new ArrayList<>();
        for (int i = 1; i <= 501; i++) {
            turns.add(Map.of("turn", i, "events", List.of()));
        }
        body.put("turns", turns);
        post(body).statusCode(400).body("error", equalTo("VALIDATION_ERROR"))
                .body("details.field", hasItem("turns"));

        turns.remove(500);
        post(body).statusCode(201);
    }

    @Test
    @TestSecurity(user = "encounters", roles = "service")
    void otherInvalidBodiesAre400() {
        Map<String, Object> missingWild = record(id(), PLAYER_A, RUN_1, "victory", 1);
        missingWild.remove("wild");
        post(missingWild).statusCode(400).body("details.field", hasItem("wild"));

        Map<String, Object> unknownField = record(id(), PLAYER_A, RUN_1, "victory", 1);
        unknownField.put("hp", 999);
        post(unknownField).statusCode(400).body("details.code", hasItem("UNKNOWN_FIELD"));

        Map<String, Object> badSeed = record(id(), PLAYER_A, RUN_1, "victory", 1);
        badSeed.put("rng_seed", "99999999999999999999");
        post(badSeed).statusCode(400).body("details.field", hasItem("rng_seed"));

        Map<String, Object> numericSeed = record(id(), PLAYER_A, RUN_1, "victory", 1);
        numericSeed.put("rng_seed", "abc");
        post(numericSeed).statusCode(400).body("details.field", hasItem("rng_seed"));

        Map<String, Object> reversedTime = record(id(), PLAYER_A, RUN_1, "victory", 1);
        reversedTime.put("started_at", T0.plusSeconds(3600).toString());
        post(reversedTime).statusCode(400).body("details.field", hasItem("ended_at"));

        Map<String, Object> badId = record(id(), PLAYER_A, RUN_1, "victory", 1);
        badId.put("encounter_id", "not-a-uuid");
        post(badId).statusCode(400).body("error", equalTo("VALIDATION_ERROR"));

        org.junit.jupiter.api.Assertions.assertEquals(0, rows());
    }

    // ---------- list ----------

    @Test
    @TestSecurity(user = "ash", roles = {"player", "service"})
    @JwtSecurity(claims = @Claim(key = "sub", value = PLAYER_A))
    void listIsNewestFirstSummariesOnly() {
        String oldest = id();
        String middle = id();
        String newest = id();
        post(record(oldest, PLAYER_A, RUN_1, "victory", 1)).statusCode(201);
        post(record(newest, PLAYER_A, RUN_1, "defeat", 30)).statusCode(201);
        post(record(middle, PLAYER_A, RUN_1, "fled", 10)).statusCode(201);

        given().get("/api/history").then().statusCode(200)
                .body("items.encounter_id", contains(newest, middle, oldest))
                .body("items.outcome", contains("defeat", "fled", "victory"))
                .body("total_items", equalTo(3))
                .body("total_pages", equalTo(1))
                .body("page", equalTo(0))
                .body("size", equalTo(20))
                .body("items[0].template.name", equalTo("Mossy Hollow"))
                .body("items[0].wild.monster_id", equalTo(51))
                .body("items[0].reward.monster_id", equalTo(51))
                .body("items[0].xp_gained", equalTo(20))
                .body("items[0].run_id", equalTo(RUN_1))
                .body("items[0].team_snapshot", nullValue())
                .body("items[0].turns", nullValue())
                .body("items[0].player_id", nullValue());
    }

    @Test
    @TestSecurity(user = "ash", roles = {"player", "service"})
    @JwtSecurity(claims = @Claim(key = "sub", value = PLAYER_A))
    void listFiltersByRunAndOutcome() {
        String a = id();
        String b = id();
        String c = id();
        post(record(a, PLAYER_A, RUN_1, "victory", 1)).statusCode(201);
        post(record(b, PLAYER_A, RUN_2, "victory", 2)).statusCode(201);
        post(record(c, PLAYER_A, RUN_2, "defeat", 3)).statusCode(201);

        given().queryParam("run_id", RUN_2).get("/api/history").then().statusCode(200)
                .body("items.encounter_id", contains(c, b)).body("total_items", equalTo(2));
        given().queryParam("outcome", "victory").get("/api/history").then().statusCode(200)
                .body("items.encounter_id", contains(b, a));
        given().queryParam("run_id", RUN_2).queryParam("outcome", "defeat").get("/api/history").then()
                .statusCode(200).body("items.encounter_id", contains(c));
        given().queryParam("outcome", "expired").get("/api/history").then().statusCode(200)
                .body("items", hasSize(0)).body("total_items", equalTo(0)).body("total_pages", equalTo(0));
    }

    @Test
    @TestSecurity(user = "ash", roles = "player")
    @JwtSecurity(claims = @Claim(key = "sub", value = PLAYER_A))
    void invalidFiltersAre400() {
        given().queryParam("outcome", "draw").get("/api/history").then().statusCode(400)
                .body("error", equalTo("VALIDATION_ERROR")).body("details.field", hasItem("outcome"));
        given().queryParam("outcome", "VICTORY").get("/api/history").then().statusCode(400);
        given().queryParam("run_id", "nope").get("/api/history").then().statusCode(400)
                .body("details.field", hasItem("run_id"));
    }

    @Test
    @TestSecurity(user = "ash", roles = {"player", "service"})
    @JwtSecurity(claims = @Claim(key = "sub", value = PLAYER_A))
    void paginationAndOutOfRange() {
        List<String> ids = new ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            String encounterId = id();
            ids.add(0, encounterId); // newest first
            post(record(encounterId, PLAYER_A, RUN_1, "victory", i)).statusCode(201);
        }

        given().queryParam("page", 0).queryParam("size", 2).get("/api/history").then().statusCode(200)
                .body("items.encounter_id", contains(ids.get(0), ids.get(1)))
                .body("total_items", equalTo(5)).body("total_pages", equalTo(3)).body("size", equalTo(2));
        given().queryParam("page", 2).queryParam("size", 2).get("/api/history").then().statusCode(200)
                .body("items.encounter_id", contains(ids.get(4)));
        given().queryParam("page", 9).queryParam("size", 2).get("/api/history").then().statusCode(200)
                .body("items", hasSize(0));

        given().queryParam("size", 51).get("/api/history").then().statusCode(400)
                .body("error", equalTo("VALIDATION_ERROR"));
        given().queryParam("size", 0).get("/api/history").then().statusCode(400);
        given().queryParam("page", -1).get("/api/history").then().statusCode(400);
        given().queryParam("size", 50).get("/api/history").then().statusCode(200);
    }

    // ---------- detail ----------

    @Test
    @TestSecurity(user = "ash", roles = {"player", "service"})
    @JwtSecurity(claims = @Claim(key = "sub", value = PLAYER_A))
    void detailReturnsFullRecord() {
        String encounterId = id();
        post(record(encounterId, PLAYER_A, RUN_1, "victory", 5)).statusCode(201);

        given().get("/api/history/" + encounterId).then().statusCode(200)
                .body("encounter_id", equalTo(encounterId))
                .body("run_id", equalTo(RUN_1))
                .body("outcome", equalTo("victory"))
                .body("template.template_id", equalTo(12))
                .body("template.stage", equalTo(1))
                .body("template.element", equalTo("echo"))
                .body("wild.rarity", equalTo("common"))
                .body("team_snapshot", hasSize(1))
                .body("team_snapshot[0].stats.max_hp", equalTo(30))
                .body("turns", hasSize(1))
                .body("turns[0].events[0].amount", equalTo(7))
                .body("reward.level", equalTo(2));
    }

    @Test
    @TestSecurity(user = "ash", roles = {"player", "service"})
    @JwtSecurity(claims = @Claim(key = "sub", value = PLAYER_A))
    void foreignOrMissingRecordIs404() {
        String foreign = id();
        post(record(foreign, PLAYER_B, RUN_1, "victory", 5)).statusCode(201);

        given().get("/api/history/" + foreign).then().statusCode(404).body("error", equalTo("NOT_FOUND"));
        given().get("/api/history/" + id()).then().statusCode(404).body("error", equalTo("NOT_FOUND"));
        given().get("/api/history/not-a-uuid").then().statusCode(404);
    }

    // ---------- isolation ----------

    @Test
    @TestSecurity(user = "ash", roles = {"player", "service"})
    @JwtSecurity(claims = @Claim(key = "sub", value = PLAYER_A))
    void playerAOnlySeesOwnRecords() {
        String mine = id();
        String theirs = id();
        post(record(mine, PLAYER_A, RUN_1, "victory", 1)).statusCode(201);
        post(record(theirs, PLAYER_B, RUN_1, "victory", 2)).statusCode(201);

        given().get("/api/history").then().statusCode(200)
                .body("items.encounter_id", contains(mine)).body("total_items", equalTo(1));
        // filtering by the other player's run does not leak anything either
        given().queryParam("run_id", RUN_1).get("/api/history").then().statusCode(200)
                .body("items.encounter_id", contains(mine));
        given().get("/api/history/" + theirs).then().statusCode(404);
    }

    @Test
    @TestSecurity(user = "gary", roles = {"player", "service"})
    @JwtSecurity(claims = @Claim(key = "sub", value = PLAYER_B))
    void playerBOnlySeesOwnRecords() {
        String mine = id();
        String theirs = id();
        post(record(theirs, PLAYER_A, RUN_1, "victory", 1)).statusCode(201);
        post(record(mine, PLAYER_B, RUN_1, "defeat", 2)).statusCode(201);

        given().get("/api/history").then().statusCode(200)
                .body("items.encounter_id", contains(mine)).body("total_items", equalTo(1));
        given().get("/api/history/" + mine).then().statusCode(200);
        given().get("/api/history/" + theirs).then().statusCode(404);
    }
}
