package it.unipi.ase.tamer.encounters.api;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.quarkus.test.security.jwt.Claim;
import io.quarkus.test.security.jwt.JwtSecurity;
import io.restassured.http.ContentType;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** EN-1: internal run opening, encounters and runs read endpoints. */
@QuarkusTest
class RunsApiTest extends ApiTestBase {

    static final String OTHER = "0b9d4c1e-62f7-4d0a-8f61-5d6e9a1c2b33";

    static io.restassured.response.ValidatableResponse open(UUID runId, String playerId) {
        return given().contentType(ContentType.JSON).body(Map.of("run_id", runId.toString(), "player_id", playerId))
                .post("/internal/runs").then();
    }

    @Test
    @TestSecurity(user = "players", roles = "service")
    void openRunIsIdempotent() {
        UUID id = UUID.randomUUID();
        open(id, OTHER).statusCode(201)
                .body("run_id", equalTo(id.toString()))
                .body("status", equalTo("active"))
                .body("stage", equalTo(1))
                .body("wins_in_stage", equalTo(0))
                .body("bosses_defeated", hasSize(0))
                .body("boss_available", equalTo(false))
                .body("ended_at", nullValue());
        open(id, OTHER).statusCode(200).body("run_id", equalTo(id.toString()));
        // a second active run for the same player
        open(UUID.randomUUID(), OTHER).statusCode(409).body("error", equalTo("RUN_CONFLICT"));
        // the same run id for another player
        open(id, PLAYER).statusCode(409);
        given().contentType(ContentType.JSON).body("{\"run_id\":\"" + id + "\"}").post("/internal/runs")
                .then().statusCode(400).body("error", equalTo("VALIDATION_ERROR"));
    }

    @Test
    @TestSecurity(user = "ash", roles = "player")
    @JwtSecurity(claims = @Claim(key = "sub", value = PLAYER))
    void internalWithPlayerTokenIs403() {
        open(UUID.randomUUID(), OTHER).statusCode(403).body("error", equalTo("FORBIDDEN"));
    }

    @Test
    void publicEndpointsNeedAToken() {
        given().get("/api/encounters").then().statusCode(401).body("error", equalTo("UNAUTHORIZED"));
        given().get("/api/runs/current").then().statusCode(401);
        given().get("/api/battles/current").then().statusCode(401);
    }

    @Test
    @TestSecurity(user = "ash", roles = "player")
    @JwtSecurity(claims = @Claim(key = "sub", value = PLAYER))
    void encountersOfStageOne() {
        given().get("/api/encounters").then().statusCode(200)
                .body("template_id", equalTo(List.of(11, 12, 13)))
                .body("[0].name", equalTo("Drowsy Meadow"))
                .body("[0].stage", equalTo(1))
                .body("[0].element", equalTo("dream"))
                .body("[0].is_boss", equalTo(false))
                .body("[0].boss_monster_id", nullValue())
                .body("[0].level_min", equalTo(1))
                .body("[0].level_max", equalTo(3))
                .body("[0].rarity_weights.common", equalTo(100))
                .body("[0].rarity_weights.rare", equalTo(0));
        given().get("/api/encounters/12").then().statusCode(200).body("element", equalTo("echo"));
        given().get("/api/encounters/14").then().statusCode(404).body("error", equalTo("NOT_FOUND"));
        given().get("/api/encounters/21").then().statusCode(404);

        updateRun(r -> r.winsInStage = 2);
        given().get("/api/runs/current").then().statusCode(200).body("boss_available", equalTo(true));
        given().get("/api/encounters/14").then().statusCode(200)
                .body("is_boss", equalTo(true))
                .body("boss_monster_id", equalTo(31))
                .body("level_min", equalTo(5));
    }

    @Test
    @TestSecurity(user = "ash", roles = "player")
    @JwtSecurity(claims = @Claim(key = "sub", value = PLAYER))
    void runsAreScopedAndPaginated() {
        given().get("/api/runs/current").then().statusCode(200)
                .body("run_id", equalTo(runId.toString()))
                .body("status", equalTo("active"))
                .body("stage", equalTo(1))
                .body("started_at", notNullValue())
                .body("battle_in_progress", equalTo(false))
                .body("reset_pending", equalTo(false));
        given().get("/api/runs/" + runId).then().statusCode(200).body("run_id", equalTo(runId.toString()));
        given().get("/api/runs").then().statusCode(200)
                .body("items", hasSize(1))
                .body("page", equalTo(0))
                .body("size", equalTo(20))
                .body("total_items", equalTo(1))
                .body("total_pages", equalTo(1));
        given().queryParam("size", 51).get("/api/runs").then().statusCode(400).body("error", equalTo("VALIDATION_ERROR"));
        given().queryParam("page", -1).get("/api/runs").then().statusCode(400);

        UUID foreign = UUID.randomUUID();
        runService.open(foreign, UUID.fromString(OTHER));
        given().get("/api/runs/" + foreign).then().statusCode(404).body("error", equalTo("NOT_FOUND"));
        given().get("/api/runs/" + UUID.randomUUID()).then().statusCode(404);
        given().get("/api/runs").then().statusCode(200).body("items", hasSize(1));
    }

    @Test
    @TestSecurity(user = "ash", roles = "player")
    @JwtSecurity(claims = @Claim(key = "sub", value = PLAYER))
    void withoutActiveRun() {
        updateRun(r -> {
            r.status = "lost";
            r.resetDone = true;
        });
        given().get("/api/runs/current").then().statusCode(404).body("error", equalTo("NO_ACTIVE_RUN"));
        given().get("/api/encounters").then().statusCode(409).body("error", equalTo("NO_ACTIVE_RUN"));
        given().get("/api/runs").then().statusCode(200).body("items[0].status", equalTo("lost"));
    }
}
