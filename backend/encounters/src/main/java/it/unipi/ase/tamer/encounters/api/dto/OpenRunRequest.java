package it.unipi.ase.tamer.encounters.api.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/** Body of {@code POST /internal/runs} (called by players after the starters were granted). */
public record OpenRunRequest(@NotNull UUID runId, @NotNull UUID playerId) {
}
