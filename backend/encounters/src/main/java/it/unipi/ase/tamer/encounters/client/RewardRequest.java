package it.unipi.ase.tamer.encounters.client;

import java.util.UUID;

/** Body of {@code POST /internal/collections/{pid}/monsters}; {@code source} is {@code boss} or {@code encounter}. */
public record RewardRequest(UUID runId, int monsterId, int level, UUID encounterId, String source) {
}
