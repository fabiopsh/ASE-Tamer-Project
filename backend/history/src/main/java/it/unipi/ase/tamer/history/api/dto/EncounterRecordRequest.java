package it.unipi.ase.tamer.history.api.dto;

import com.fasterxml.jackson.databind.JsonNode;
import it.unipi.ase.tamer.history.domain.Outcome;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Body of {@code POST /internal/history}. {@code team_snapshot}, {@code reward} and {@code turns}
 * are stored exactly as sent (free-form JSON owned by the encounters service).
 * Optional: {@code team_snapshot} and {@code turns} (default: empty), {@code reward} (default: none),
 * {@code xp_gained} (default 0) and {@code rng_seed}: an interrupted record has minimal data.
 */
public record EncounterRecordRequest(
        @NotNull UUID encounterId,
        @NotNull UUID playerId,
        @NotNull UUID runId,
        @NotNull @Valid TemplateInfo template,
        @NotNull @Valid WildInfo wild,
        @Size(max = 6) List<@NotNull JsonNode> teamSnapshot,
        @NotNull Outcome outcome,
        JsonNode reward,
        @Min(0) @Max(1_000_000) Integer xpGained,
        @Size(max = 500) List<@NotNull JsonNode> turns,
        @Pattern(regexp = "-?[0-9]{1,19}") String rngSeed,
        @NotNull @Min(1) @Max(32767) Integer engineVersion,
        @NotNull Instant startedAt,
        @NotNull Instant endedAt) {
}
