package it.unipi.ase.tamer.players.api.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record CreatePlayerRequest(@NotNull UUID playerId) {
}
