package it.unipi.ase.tamer.players.client;

import java.util.List;
import java.util.UUID;

public record StarterGrantRequest(UUID runId, List<Integer> monsterIds) {
}
