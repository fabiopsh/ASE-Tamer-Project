package it.unipi.ase.tamer.players.api.dto;

import java.util.List;
import java.util.UUID;

public record ChosenStartersResponse(UUID runId, List<StarterEntry> starters) {
}
