package it.unipi.ase.tamer.encounters.api.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/** Body of {@code POST /api/battles} (US-18). */
public record StartBattleRequest(@NotNull @Min(1) @Max(1000) Integer templateId, @NotNull UUID teamId) {
}
