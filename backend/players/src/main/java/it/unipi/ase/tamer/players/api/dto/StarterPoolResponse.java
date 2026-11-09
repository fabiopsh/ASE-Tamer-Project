package it.unipi.ase.tamer.players.api.dto;

import java.util.List;
import java.util.UUID;

public record StarterPoolResponse(UUID runId, List<Integer> pool, boolean chosen) {
}
