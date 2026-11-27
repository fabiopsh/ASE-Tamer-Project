package it.unipi.ase.tamer.history.api.dto;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.UUID;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/** Full record of one encounter ({@code GET /api/history/{encounter_id}}). */
public record EncounterRecordResponse(
        UUID encounterId,
        UUID runId,
        TemplateInfo template,
        @Schema(type = SchemaType.OBJECT, implementation = Object.class) JsonNode wild,
        @Schema(type = SchemaType.ARRAY, implementation = Object[].class) JsonNode teamSnapshot,
        String outcome,
        @Schema(type = SchemaType.OBJECT, implementation = Object.class) JsonNode reward,
        int xpGained,
        @Schema(type = SchemaType.ARRAY, implementation = Object[].class) JsonNode turns,
        String rngSeed,
        int engineVersion,
        Instant startedAt,
        Instant endedAt) {
}
