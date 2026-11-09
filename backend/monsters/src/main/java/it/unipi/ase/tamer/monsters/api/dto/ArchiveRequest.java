package it.unipi.ase.tamer.monsters.api.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record ArchiveRequest(@NotNull UUID runId) {
}
