package it.unipi.ase.tamers.history.record;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.JsonNode;
import it.unipi.ase.tamers.history.common.ApiException;
import it.unipi.ase.tamers.history.record.EncounterPayload.Header;
import it.unipi.ase.tamers.history.record.HistoryDtos.EnemySummary;
import it.unipi.ase.tamers.history.record.HistoryDtos.HistorySummary;
import it.unipi.ase.tamers.history.record.HistoryDtos.TeamMemberSummary;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class EncounterPayloadTest {
    static final String PLAYER = "5b1f6c1e-0c3a-4d7e-9a57-2f1d8e6a0b11";
    static final String RUN = "9d2c4f7a-1b3e-4c5d-8e6f-7a8b9c0d1e2f";
    static final String ENCOUNTER = "e1a2b3c4-d5e6-4f70-8192-a3b4c5d6e7f8";

    /** A wild victory with one captured monster. */
    static final String WON_WITH_CAPTURE = """
            {"player_id":"%s","run_id":"%s","encounter_id":"%s","kind":"wild","stage":2,"outcome":"victory",
             "rounds":7,"seed":123,
             "team":[
               {"owned_id":"11111111-1111-4111-8111-111111111111","monster_id":1,"name":"Gearbit","element":"dream",
                "image_url":"/images/monsters/001.png","level":1,"max_hp":86,"stats":{"hp":86}},
               {"owned_id":"22222222-2222-4222-8222-222222222222","monster_id":9,"name":"Snoozle","element":"time",
                "image_url":"/images/monsters/009.png","level":4,"max_hp":120,"stats":{"hp":120}}],
             "enemy":{"monster_id":5,"name":"Tickly","element":"time","image_url":"/images/monsters/005.png",
                      "level":5,"max_hp":90,"stats":{"hp":90}},
             "log":[],
             "rewards":{"xp":[{"owned_id":"11111111-1111-4111-8111-111111111111","xp":50,"old_level":1,"new_level":2}],
                        "captured":{"owned_id":"33333333-3333-4333-8333-333333333333","monster_id":12,"name":"Dreamo",
                                    "element":"dream","image_url":"/images/monsters/012.png","level":1,"xp":0},
                        "stage_advanced":false,"new_stage":2},
             "occurred_at":"2026-10-14T10:15:30Z"}
            """.formatted(PLAYER, RUN, ENCOUNTER);

    /** A guardian defeat without capture. */
    static final String LOST_NO_CAPTURE = """
            {"player_id":"%s","run_id":"%s","encounter_id":"%s","kind":"guardian","stage":5,"outcome":"defeat",
             "rounds":100,"seed":9,
             "team":[{"owned_id":"11111111-1111-4111-8111-111111111111","monster_id":1,"name":"Gearbit",
                      "element":"dream","image_url":"/images/monsters/001.png","level":3,"max_hp":86,"stats":{}}],
             "enemy":{"monster_id":44,"name":"Guardian","element":"fire","image_url":"/images/monsters/044.png",
                      "level":17,"max_hp":400,"stats":{}},
             "log":[],
             "rewards":{"xp":[],"captured":null,"stage_advanced":false,"new_stage":5},
             "occurred_at":"2026-10-14T11:00:00Z"}
            """.formatted(PLAYER, RUN, ENCOUNTER);

    static JsonNode json(String text) {
        return EncounterPayload.parse(text);
    }

    @Test
    void headerReadsIncomingFields() {
        Header header = EncounterPayload.header(json(WON_WITH_CAPTURE));
        assertEquals(UUID.fromString(PLAYER), header.playerId());
        assertEquals(UUID.fromString(RUN), header.runId());
        assertEquals(UUID.fromString(ENCOUNTER), header.encounterId());
        assertEquals("wild", header.kind());
        assertEquals(2, header.stage());
        assertEquals("victory", header.outcome());
        assertEquals(Instant.parse("2026-10-14T10:15:30Z"), header.occurredAt());
    }

    @Test
    void headerListsEveryInvalidField() {
        ApiException e = assertThrows(ApiException.class,
                () -> EncounterPayload.header(json("""
                        {"player_id":"not-a-uuid","kind":"wild","stage":"two","outcome":"draw",
                         "rounds":7,"occurred_at":"yesterday","enemy":{},"team":{}}
                        """)));
        assertEquals(400, e.status());
        assertEquals("validation_error", e.error());
        assertTrue(e.details().contains("player_id: must be a UUID"), e.details().toString());
        assertTrue(e.details().contains("run_id: is required"), e.details().toString());
        assertTrue(e.details().contains("stage: must be an integer"), e.details().toString());
        assertTrue(e.details().contains("outcome: must be victory or defeat"), e.details().toString());
        assertTrue(e.details().contains("occurred_at: must be an ISO-8601 instant"), e.details().toString());
        assertTrue(e.details().contains("team: must be an array"), e.details().toString());
    }

    @Test
    void headerRejectsNonObjectBody() {
        assertEquals(400, assertThrows(ApiException.class, () -> EncounterPayload.header(null)).status());
        assertEquals(400, assertThrows(ApiException.class,
                () -> EncounterPayload.header(json("[1,2]"))).status());
    }

    @Test
    void headerRejectsMissingEnemy() {
        String withoutEnemy = WON_WITH_CAPTURE.replace("\"enemy\"", "\"foe\"");
        ApiException e = assertThrows(ApiException.class, () -> EncounterPayload.header(json(withoutEnemy)));
        assertTrue(e.details().contains("enemy: must be an object"), e.details().toString());
    }

    @Test
    void summaryExtractsFieldsAndCapture() {
        HistorySummary s = EncounterPayload.summary(4, json(WON_WITH_CAPTURE));
        assertEquals(UUID.fromString(RUN), s.runId());
        assertEquals(UUID.fromString(ENCOUNTER), s.encounterId());
        assertEquals(4L, s.sequence());
        assertEquals("wild", s.kind());
        assertEquals(2, s.stage());
        assertEquals("victory", s.outcome());
        assertEquals(7, s.rounds());
        assertEquals(new EnemySummary(5, "Tickly", "time", "/images/monsters/005.png", 5), s.enemy());
        assertEquals(2, s.team().size());
        assertEquals(new TeamMemberSummary(9, "Snoozle", "/images/monsters/009.png", 4), s.team().get(1));
        assertNotNull(s.captured());
        assertEquals(12, s.captured().monsterId());
        assertEquals("Dreamo", s.captured().name());
        assertEquals("/images/monsters/012.png", s.captured().imageUrl());
        assertEquals(Instant.parse("2026-10-14T10:15:30Z"), s.occurredAt());
    }

    @Test
    void summaryHasNullCapturedWhenNothingWasCaptured() {
        HistorySummary s = EncounterPayload.summary(1, json(LOST_NO_CAPTURE));
        assertEquals("defeat", s.outcome());
        assertEquals("guardian", s.kind());
        assertEquals(100, s.rounds());
        assertNull(s.captured());
        assertEquals(1, s.team().size());
    }

    @Test
    void serializeIsStableAndKeepsReceivedOrder() {
        JsonNode body = json(LOST_NO_CAPTURE);
        String once = EncounterPayload.serialize(body);
        assertEquals(once, EncounterPayload.serialize(EncounterPayload.parse(once)));
        assertTrue(once.startsWith("{\"player_id\":"), once);
        assertTrue(once.indexOf("\"run_id\"") < once.indexOf("\"occurred_at\""));
    }
}
