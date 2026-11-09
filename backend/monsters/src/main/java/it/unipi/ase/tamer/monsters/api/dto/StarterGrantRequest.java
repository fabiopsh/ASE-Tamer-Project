package it.unipi.ase.tamer.monsters.api.dto;

import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

public record StarterGrantRequest(@NotNull UUID runId, @NotNull List<Integer> monsterIds) {
}
