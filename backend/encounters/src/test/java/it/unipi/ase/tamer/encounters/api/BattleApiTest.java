package it.unipi.ase.tamer.encounters.api;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.quarkus.test.security.jwt.Claim;
import io.quarkus.test.security.jwt.JwtSecurity;
import io.restassured.path.json.JsonPath;
import it.unipi.ase.tamer.encounters.client.NewRunRequest;
import it.unipi.ase.tamer.encounters.client.RemoteClientError;
import it.unipi.ase.tamer.encounters.client.RewardRequest;
import it.unipi.ase.tamer.encounters.client.RunIdRequest;
import it.unipi.ase.tamer.encounters.client.XpRequest;
import it.unipi.ase.tamer.encounters.domain.BattleCompletion;
import it.unipi.ase.tamer.encounters.domain.Run;
import it.unipi.ase.tamer.encounters.support.TestTime;
import jakarta.ws.rs.ProcessingException;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** EN-3 battle API with mocked monsters / teams / players / history. */
@QuarkusTest
@TestSecurity(user = "ash", roles = "player")
@JwtSecurity(claims = @Claim(key = "sub", value = ApiTestBase.PLAYER))
class BattleApiTest extends ApiTestBase {

    JsonNode lastHistoryRecord(int expectedCalls) {
        ArgumentCaptor<JsonNode> captor = ArgumentCaptor.forClass(JsonNode.class);
        verify(history, times(expectedCalls)).store(captor.capture());
        return captor.getValue();
    }

    // ---- start -----------------------------------------------------------------------------------------------

    @Test
    void startReturnsTheInitialState() {
        JsonPath state = start(11).statusCode(201)
                .body("encounter_id", notNullValue())
                .body("status", equalTo("in_progress"))
                .body("turn", equalTo(0))
                .body("template.template_id", equalTo(11))
                .body("template.name", equalTo("Drowsy Meadow"))
                .body("template.is_boss", equalTo(false))
                .body("wild.image_url", matchesPattern("/images/monsters/\\d{3}\\.png"))
                .body("wild.hp", equalTo(45))
                .body("wild.max_hp", equalTo(45))
                .body("team", hasSize(3))
                .body("team[0].active", equalTo(true))
                .body("team[1].active", equalTo(false))
                .body("team[0].owned_id", equalTo(owned.get(0).toString()))
                .body("team[0].image_url", equalTo("/images/monsters/004.png"))
                .body("$", hasKey("result"))
                .body("result", nullValue())
                .body("last_round", nullValue())
                .extract().jsonPath();
        int level = state.getInt("wild.level");
        assertTrue(level >= 1 && level <= 3, "stage-1 level range");
        // the boss species are excluded from the wild pool
        verify(monsters).candidates(eq("dream"), eq("16,31,47,62,74,89,92,100"));

        Run run = run();
        assertEquals(UUID.fromString(state.getString("encounter_id")), run.activeBattleId);
        assertEquals(42L, run.activeBattleSeed);
        assertEquals("Wildling", run.activeBattleWild.get("name").asText());

        given().get("/api/battles/current").then().statusCode(200)
                .body("encounter_id", equalTo(state.getString("encounter_id")))
                .body("status", equalTo("in_progress"));
        given().get("/api/runs/current").then().statusCode(200).body("battle_in_progress", equalTo(true));
    }

    @Test
    void secondStartIs409() {
        start(11).statusCode(201);
        start(12).statusCode(409).body("error", equalTo("BATTLE_IN_PROGRESS"));
    }

    @Test
    void invalidOrMissingTeam() {
        when(teams.team(any(), any())).thenThrow(new RemoteClientError(409, "TEAM_INVALID", "Team belongs to an earlier run."));
        start(11).statusCode(409).body("error", equalTo("TEAM_INVALID"))
                .body("message", equalTo("Team belongs to an earlier run."));
        doThrow(new RemoteClientError(404, "NOT_FOUND", "Team not found.")).when(teams).team(any(), any());
        start(11).statusCode(404).body("error", equalTo("NOT_FOUND"));
        doThrow(new ProcessingException("teams down")).when(teams).team(any(), any());
        start(11).statusCode(503).body("error", equalTo("DEPENDENCY_UNAVAILABLE"));
        assertNull(run().activeBattleId);
    }

    @Test
    void teamOfAnotherRunIsInvalid() {
        when(teams.team(any(), any())).thenReturn(new it.unipi.ase.tamer.encounters.client.TeamSnapshot(TEAM, "Old",
                UUID.randomUUID(), List.of(member(1, owned.get(0), new it.unipi.ase.tamer.encounters.client.StatBlock(1, 1, 1, 1)))));
        start(11).statusCode(409).body("error", equalTo("TEAM_INVALID"));
    }

    @Test
    void unavailableTemplatesAre404() {
        start(14).statusCode(404).body("error", equalTo("NOT_FOUND")); // boss locked (0 wins)
        start(21).statusCode(404);                                     // stage 2 not reached
        start(999).statusCode(404);
        given().contentType("application/json").body("{\"template_id\":11}").post("/api/battles")
                .then().statusCode(400).body("error", equalTo("VALIDATION_ERROR"));
    }

    @Test
    void startWithoutActiveRunIs409() {
        updateRun(r -> r.status = Run.LOST);
        start(11).statusCode(409).body("error", equalTo("NO_ACTIVE_RUN"));
    }

    // ---- actions ---------------------------------------------------------------------------------------------

    @Test
    void invalidActionsAreRejected() {
        attack().statusCode(404).body("error", equalTo("NOT_FOUND"));
        slowTeamOfTwo();
        start(11).statusCode(201);

        act("{\"type\":\"dance\"}").statusCode(400).body("error", equalTo("VALIDATION_ERROR"));
        act("{}").statusCode(400).body("error", equalTo("VALIDATION_ERROR"));
        act("{\"type\":\"attack\",\"outcome\":\"victory\"}").statusCode(400)
                .body("error", equalTo("VALIDATION_ERROR")).body("details.field", hasItem("outcome"));
        act("{\"type\":\"attack\",\"hp\":999}").statusCode(400).body("details.field", hasItem("hp"));
        act("{\"type\":\"switch\",\"slot\":4}").statusCode(400).body("error", equalTo("VALIDATION_ERROR"));
        act("{\"type\":\"switch\"}").statusCode(400).body("error", equalTo("INVALID_ACTION"));
        act("{\"type\":\"switch\",\"slot\":1}").statusCode(400).body("error", equalTo("INVALID_ACTION"));
        act("{\"type\":\"switch\",\"slot\":3}").statusCode(400).body("error", equalTo("INVALID_ACTION"));
        act("{\"type\":\"attack\",\"slot\":2}").statusCode(400).body("error", equalTo("INVALID_ACTION"));
        // nothing was applied
        given().get("/api/battles/current").then().statusCode(200).body("turn", equalTo(0));
    }

    @Test
    void validActionsResolveRounds() {
        slowTeamOfTwo();
        start(11).statusCode(201);
        attack().statusCode(200)
                .body("status", equalTo("in_progress"))
                .body("turn", equalTo(1))
                .body("last_round.turn", equalTo(1))
                .body("last_round.events.type", everyItem(equalTo("attack")))
                .body("last_round.events", hasSize(2))
                .body("last_round.events[0]", hasKey("damage"))
                .body("last_round.events[0]", org.hamcrest.Matchers.not(hasKey("position")))
                .body("result", nullValue());
        act("{\"type\":\"switch\",\"slot\":2}").statusCode(200)
                .body("turn", equalTo(2))
                .body("last_round.events[0].type", equalTo("switch"))
                .body("last_round.events[0].from", equalTo(1))
                .body("last_round.events[0].to", equalTo(2))
                .body("team[1].active", equalTo(true));
        act("{\"type\":\"flee\"}").statusCode(200)
                .body("last_round.events[0].type", equalTo("flee"))
                .body("status", anyOf(equalTo("in_progress"), equalTo("finished")));
    }

    @Test
    void victoryGrantsRewardXpAndHistoryWithTheSameEncounterId() {
        JsonPath started = start(11).statusCode(201).extract().jsonPath();
        String encounterId = started.getString("encounter_id");
        int wildId = started.getInt("wild.monster_id");
        int wildLevel = started.getInt("wild.level");
        String rarity = started.getString("wild.rarity");
        int xp = (int) Math.round(10 * wildLevel * ("rare".equals(rarity) ? 1.5 : 1.0));
        UUID ownedReward = UUID.nameUUIDFromBytes(encounterId.getBytes());

        attack().statusCode(200)
                .body("status", equalTo("finished"))
                .body("encounter_id", equalTo(encounterId))
                .body("wild.hp", equalTo(0))
                .body("result.outcome", equalTo("victory"))
                .body("result.reward.owned_id", equalTo(ownedReward.toString()))
                .body("result.reward.monster_id", equalTo(wildId))
                .body("result.reward.level", equalTo(wildLevel))
                .body("result.reward.pending", equalTo(false))
                .body("result.xp_gained", equalTo(xp))
                .body("result.level_ups", hasSize(3))
                .body("result.level_ups[0].level_before", equalTo(5))
                .body("result.level_ups[0].level_after", equalTo(6))
                .body("result.run.stage", equalTo(1))
                .body("result.run.wins_in_stage", equalTo(1))
                .body("result.run.boss_available", equalTo(false))
                .body("result.run.status", equalTo("active"))
                .body("last_round.events.type", hasItem("end"));

        UUID encounter = UUID.fromString(encounterId);
        verify(monsters).reward(PLAYER_ID, new RewardRequest(runId, wildId, wildLevel, encounter, "encounter"));
        verify(monsters).xp(PLAYER_ID, new XpRequest(encounter, owned, xp));
        JsonNode record = lastHistoryRecord(1);
        assertEquals(encounterId, record.get("encounter_id").asText());
        assertEquals("victory", record.get("outcome").asText());
        assertEquals(ownedReward.toString(), record.get("reward").get("owned_id").asText());
        assertEquals(runId.toString(), record.get("run_id").asText());
        assertEquals("42", record.get("rng_seed").asText());
        assertEquals(3, record.get("team_snapshot").size());
        assertEquals(1, record.get("turns").size());

        BattleCompletion done = completion(encounterId);
        assertFalse(done.pending());
        assertEquals(ownedReward, done.rewardOwnedId);
        Run run = run();
        assertNull(run.activeBattleId);
        assertEquals(1, run.winsInStage);
        given().get("/api/battles/current").then().statusCode(404);
        attack().statusCode(404);
    }

    @Test
    void rewardServiceDownMeansPendingRewardRetriedOnNextRequest() {
        doThrow(new ProcessingException("monsters down")).when(monsters).reward(any(), any());
        doThrow(new ProcessingException("monsters down")).when(monsters).xp(any(), any());
        String encounterId = start(11).statusCode(201).extract().path("encounter_id");

        attack().statusCode(200)
                .body("result.outcome", equalTo("victory"))
                .body("result.reward.pending", equalTo(true))
                .body("result.reward.owned_id", nullValue())
                .body("result.level_ups", hasSize(0))
                .body("result.run.wins_in_stage", equalTo(1));
        verify(history, never()).store(any());
        assertTrue(completion(encounterId).pending());

        // monsters is back: the next request of the player completes the outbox
        doAnswer(inv -> new it.unipi.ase.tamer.encounters.client.OwnedMonster(
                UUID.nameUUIDFromBytes(encounterId.getBytes()), 901, 2)).when(monsters).reward(any(), any());
        doReturn(List.of()).when(monsters).xp(any(), any());
        given().get("/api/runs/current").then().statusCode(200);
        // first attempt (with its @Retry attempts: the fault tolerance interceptors wrap the mock too) + the retry
        verify(monsters, atLeast(2)).reward(eq(PLAYER_ID), any());
        JsonNode record = lastHistoryRecord(1);
        assertEquals(encounterId, record.get("encounter_id").asText());
        assertEquals(UUID.nameUUIDFromBytes(encounterId.getBytes()).toString(),
                record.get("reward").get("owned_id").asText());
        assertFalse(completion(encounterId).pending());
    }

    @Test
    void defeatLosesTheRunAndResetsIt() {
        weakTeam();
        deadlyWild();
        String encounterId = start(11).statusCode(201).extract().path("encounter_id");
        attack().statusCode(200)
                .body("status", equalTo("finished"))
                .body("result.outcome", equalTo("defeat"))
                .body("result.reward", nullValue())
                .body("result.xp_gained", equalTo(0))
                .body("result.run.status", equalTo("lost"))
                .body("team[0].knocked_out", equalTo(true));
        verify(monsters, never()).reward(any(), any());
        verify(monsters, never()).xp(any(), any());
        assertEquals("defeat", lastHistoryRecord(1).get("outcome").asText());
        verify(monsters).archive(PLAYER_ID, new RunIdRequest(runId));
        verify(teams).reset(PLAYER_ID, new RunIdRequest(runId));
        verify(players).newRun(PLAYER_ID, new NewRunRequest(runId));
        Run run = run();
        assertEquals(Run.LOST, run.status);
        assertTrue(run.resetDone);
        assertFalse(run.resetPending);
        assertFalse(completion(encounterId).pending());
        given().get("/api/runs/current").then().statusCode(404).body("error", equalTo("NO_ACTIVE_RUN"));
    }

    @Test
    void failedResetIsResumedLater() {
        weakTeam();
        deadlyWild();
        doThrow(new ProcessingException("monsters down")).when(monsters).archive(any(), any());
        start(11).statusCode(201);
        attack().statusCode(200).body("result.outcome", equalTo("defeat")).body("result.run.status", equalTo("lost"));
        assertTrue(run().resetPending);
        verify(players, never()).newRun(any(), any());

        start(11).statusCode(409).body("error", equalTo("RESET_PENDING"));
        given().get("/api/runs/" + runId).then().statusCode(200).body("reset_pending", equalTo(true));

        doReturn(new it.unipi.ase.tamer.encounters.client.CountResponse(1)).when(monsters).archive(any(), any());
        given().get("/api/runs/current").then().statusCode(404);
        assertTrue(run().resetDone);
        verify(players).newRun(PLAYER_ID, new NewRunRequest(runId));
    }

    // ---- recovery --------------------------------------------------------------------------------------------

    @Test
    void battleMissingFromMemoryIsClosedAsInterrupted() {
        String encounterId = start(11).statusCode(201).extract().path("encounter_id");
        registry.clear(); // service restart

        given().get("/api/battles/current").then().statusCode(404);
        JsonNode record = lastHistoryRecord(1);
        assertEquals(encounterId, record.get("encounter_id").asText());
        assertEquals("interrupted", record.get("outcome").asText());
        assertEquals(0, record.get("turns").size());
        assertEquals("Wildling", record.get("wild").get("name").asText());
        assertEquals(3, record.get("team_snapshot").size());
        assertTrue(record.get("reward").isNull());
        Run run = run();
        assertNull(run.activeBattleId);
        assertEquals(Run.ACTIVE, run.status);
        verify(monsters, never()).reward(any(), any());
        start(12).statusCode(201);
    }

    @Test
    void idleBattleExpires() {
        slowTeamOfTwo();
        String encounterId = start(11).statusCode(201).extract().path("encounter_id");
        attack().statusCode(200);
        TestTime.advance(Duration.ofMinutes(29));
        given().get("/api/battles/current").then().statusCode(200);
        TestTime.advance(Duration.ofMinutes(2));
        attack().statusCode(404);

        JsonNode record = lastHistoryRecord(1);
        assertEquals(encounterId, record.get("encounter_id").asText());
        assertEquals("expired", record.get("outcome").asText());
        assertEquals(1, record.get("turns").size());
        Run run = run();
        assertNull(run.activeBattleId);
        assertEquals(Run.ACTIVE, run.status);
        assertEquals(0, run.winsInStage);
    }

    // ---- progression -----------------------------------------------------------------------------------------

    @Test
    void bossVictoryMovesToTheNextStage() {
        updateRun(r -> r.winsInStage = 2);
        given().get("/api/encounters").then().statusCode(200).body("template_id", hasItem(14));
        start(14).statusCode(201).body("template.is_boss", equalTo(true)).body("wild.monster_id", equalTo(31))
                .body("wild.level", equalTo(5));
        verify(monsters, never()).candidates(anyString(), anyString());
        attack().statusCode(200)
                .body("result.outcome", equalTo("victory"))
                .body("result.run.stage", equalTo(2))
                .body("result.run.wins_in_stage", equalTo(0))
                .body("result.run.boss_available", equalTo(false))
                .body("result.run.status", equalTo("active"));
        ArgumentCaptor<RewardRequest> reward = ArgumentCaptor.forClass(RewardRequest.class);
        verify(monsters).reward(eq(PLAYER_ID), reward.capture());
        assertEquals("boss", reward.getValue().source());
        assertEquals(5, reward.getValue().level());
        given().get("/api/runs/current").then().statusCode(200).body("bosses_defeated", equalTo(List.of(1)));
        given().get("/api/encounters").then().statusCode(200)
                .body("template_id", equalTo(List.of(11, 12, 13, 21, 22, 23)));
    }

    @Test
    void stageEightBossWinsTheRunAndANewRunCanStart() {
        updateRun(r -> {
            r.stage = 8;
            r.winsInStage = 2;
            r.bossesDefeated = new Integer[]{1, 2, 3, 4, 5, 6, 7};
        });
        start(84).statusCode(201).body("wild.monster_id", equalTo(100));
        attack().statusCode(200)
                .body("result.outcome", equalTo("victory"))
                .body("result.run.status", equalTo("won"));
        Run won = run();
        assertEquals(Run.WON, won.status);
        assertTrue(won.endedAt != null);
        assertFalse(won.resetPending);
        verify(monsters, never()).archive(any(), any());

        given().get("/api/runs/current").then().statusCode(200).body("status", equalTo("won"))
                .body("bosses_defeated", hasSize(8));
        given().get("/api/encounters").then().statusCode(409).body("error", equalTo("NO_ACTIVE_RUN"));
        start(11).statusCode(409).body("error", equalTo("NO_ACTIVE_RUN"));

        given().post("/api/runs").then().statusCode(202).body("status", equalTo("choose_starters"));
        verify(monsters).archive(PLAYER_ID, new RunIdRequest(runId));
        verify(teams).reset(PLAYER_ID, new RunIdRequest(runId));
        verify(players).newRun(PLAYER_ID, new NewRunRequest(runId));
        assertTrue(run().resetDone);
        given().get("/api/runs/current").then().statusCode(404).body("error", equalTo("NO_ACTIVE_RUN"));
        // idempotent
        given().post("/api/runs").then().statusCode(202);
        verify(players, times(1)).newRun(any(), any());
    }

    @Test
    void newRunIsRefusedUnlessTheLatestRunIsWon() {
        given().post("/api/runs").then().statusCode(409).body("error", equalTo("NEW_RUN_NOT_ALLOWED"));
    }

    // ---- abandon ---------------------------------------------------------------------------------------------

    @Test
    void abandonClosesTheBattleAndResetsTheRun() {
        slowTeamOfTwo();
        String encounterId = start(11).statusCode(201).extract().path("encounter_id");
        attack().statusCode(200);
        given().post("/api/runs/current/abandon").then().statusCode(200)
                .body("run_id", equalTo(runId.toString()))
                .body("status", equalTo("abandoned"))
                .body("ended_at", notNullValue())
                .body("battle_in_progress", equalTo(false))
                .body("reset_pending", equalTo(false));
        JsonNode record = lastHistoryRecord(1);
        assertEquals(encounterId, record.get("encounter_id").asText());
        assertEquals("abandoned", record.get("outcome").asText());
        assertTrue(record.get("reward").isNull());
        verify(monsters, never()).reward(any(), any());
        verify(monsters).archive(PLAYER_ID, new RunIdRequest(runId));
        verify(teams).reset(PLAYER_ID, new RunIdRequest(runId));
        verify(players).newRun(PLAYER_ID, new NewRunRequest(runId));
        given().get("/api/battles/current").then().statusCode(404);
        given().post("/api/runs/current/abandon").then().statusCode(409).body("error", equalTo("NO_ACTIVE_RUN"));
    }

    @Test
    void abandonWithoutBattle() {
        given().post("/api/runs/current/abandon").then().statusCode(200).body("status", equalTo("abandoned"));
        verify(history, never()).store(any());
        verify(players).newRun(PLAYER_ID, new NewRunRequest(runId));
    }
}
