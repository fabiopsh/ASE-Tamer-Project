package it.unipi.ase.tamer.teams.api.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record ResetTeamsRequest(@NotNull UUID runId) {
}
