package it.unipi.ase.tamer.encounters.api;

import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.JsonNode;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.quarkus.test.security.jwt.Claim;
import io.quarkus.test.security.jwt.JwtSecurity;
import it.unipi.ase.tamer.encounters.client.CountResponse;
import it.unipi.ase.tamer.encounters.client.NewRunRequest;
import it.unipi.ase.tamer.encounters.client.OwnedMonster;
import it.unipi.ase.tamer.encounters.client.RemoteClientError;
import it.unipi.ase.tamer.encounters.domain.Run;
import it.unipi.ase.tamer.encounters.service.EncounterSweepScheduler;
import it.unipi.ase.tamer.encounters.support.TestTime;
import jakarta.inject.Inject;
import jakarta.ws.rs.ProcessingException;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** EN-5 scheduled sweep and reset-resilience tests. */
@QuarkusTest
@TestSecurity(user = "ash", roles = "player")
@JwtSecurity(claims = @Claim(key = "sub", value = ApiTestBase.PLAYER))
class EncounterSweepSchedulerTest extends ApiTestBase {

    @Inject
    EncounterSweepScheduler scheduler;

    @Test
    void sweepExpiresIdleBattleWithoutPlayerRequest() {
        slowTeamOfTwo();
        String encounterId = start(11).statusCode(201).extract().path("encounter_id");
        attack().statusCode(200);

        // Under 30 minutes: sweep leaves battle active.
        TestTime.advance(Duration.ofMinutes(25));
        scheduler.sweep();
        assertEquals(UUID.fromString(encounterId), run().activeBattleId);

        // Past 30 minutes: sweep closes it as expired and posts history.
        TestTime.advance(Duration.ofMinutes(6));
        scheduler.sweep();

        assertNull(run().activeBattleId);
        assertNull(registry.get(PLAYER_ID));
        ArgumentCaptor<JsonNode> captor = ArgumentCaptor.forClass(JsonNode.class);
        verify(history, times(1)).store(captor.capture());
        assertEquals(encounterId, captor.getValue().get("encounter_id").asText());
        assertEquals("expired", captor.getValue().get("outcome").asText());
        assertFalse(completion(encounterId).pending());
    }

    @Test
    void sweepRetriesPendingBattleCompletions() {
        doThrow(new ProcessingException("monsters down")).when(monsters).reward(any(), any());
        doThrow(new ProcessingException("monsters down")).when(monsters).xp(any(), any());
        String encounterId = start(11).statusCode(201).extract().path("encounter_id");
        attack().statusCode(200).body("result.reward.pending", equalTo(true));
        assertTrue(completion(encounterId).pending());

        // Downstream recovers; sweep completes reward, XP and history.
        doAnswer(inv -> new OwnedMonster(UUID.nameUUIDFromBytes(encounterId.getBytes()), 901, 2))
                .when(monsters).reward(any(), any());
        doReturn(List.of()).when(monsters).xp(any(), any());

        scheduler.sweep();

        assertFalse(completion(encounterId).pending());
        verify(history, times(1)).store(any());
    }

    @Test
    void sweepResumesPendingRunResets() {
        weakTeam();
        deadlyWild();
        doThrow(new ProcessingException("monsters down")).when(monsters).archive(any(), any());
        start(11).statusCode(201);
        attack().statusCode(200).body("result.outcome", equalTo("defeat"));
        assertTrue(run().resetPending);

        doReturn(new CountResponse(1)).when(monsters).archive(any(), any());

        scheduler.sweep();

        Run updated = run();
        assertFalse(updated.resetPending);
        assertTrue(updated.resetDone);
        verify(players).newRun(PLAYER_ID, new NewRunRequest(runId));
    }

    @Test
    void permanent4xxDuringRunResetDoesNotWedgeResetPendingForever() {
        weakTeam();
        deadlyWild();
        doThrow(new RemoteClientError(404, "NOT_FOUND", "Already archived")).when(monsters).archive(any(), any());
        start(11).statusCode(201);
        attack().statusCode(200).body("result.outcome", equalTo("defeat"));

        Run updated = run();
        assertFalse(updated.resetPending, "permanent 4xx should not wedge reset_pending=true forever");
        assertTrue(updated.resetDone);
        verify(players).newRun(PLAYER_ID, new NewRunRequest(runId));
    }
}
