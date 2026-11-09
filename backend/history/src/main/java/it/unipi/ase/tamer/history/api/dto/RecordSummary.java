package it.unipi.ase.tamer.history.api.dto;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.UUID;

/** One entry of the history list: no team snapshot details and no turn log. */
public record RecordSummary(
        UUID encounterId,
        UUID runId,
        String outcome,
        TemplateInfo template,
        JsonNode wild,
        JsonNode reward,
        int xpGained,
        Instant startedAt,
        Instant endedAt) {
}
