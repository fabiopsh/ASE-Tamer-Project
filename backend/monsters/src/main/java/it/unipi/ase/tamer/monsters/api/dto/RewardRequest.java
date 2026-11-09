package it.unipi.ase.tamer.monsters.api.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record RewardRequest(@NotNull UUID runId, @NotNull Integer monsterId,
                            @NotNull Integer level, @NotNull UUID encounterId,
                            @NotNull String source) {
}
