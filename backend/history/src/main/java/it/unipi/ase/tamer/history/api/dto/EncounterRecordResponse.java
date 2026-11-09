package it.unipi.ase.tamer.history.api.dto;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.UUID;

/** Full record of one encounter ({@code GET /api/history/{encounter_id}}). */
public record EncounterRecordResponse(
        UUID encounterId,
        UUID runId,
        TemplateInfo template,
        JsonNode wild,
        JsonNode teamSnapshot,
        String outcome,
        JsonNode reward,
        int xpGained,
        JsonNode turns,
        String rngSeed,
        int engineVersion,
        Instant startedAt,
        Instant endedAt) {
}
