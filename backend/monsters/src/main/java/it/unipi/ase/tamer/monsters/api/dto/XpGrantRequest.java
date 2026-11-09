package it.unipi.ase.tamer.monsters.api.dto;

import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

public record XpGrantRequest(@NotNull UUID encounterId, @NotNull List<UUID> ownedIds,
                             @NotNull Integer xp) {
}
