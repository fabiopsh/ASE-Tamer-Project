package it.unipi.ase.tamer.encounters.client;

import java.util.List;
import java.util.UUID;

/** Body of {@code POST /internal/collections/{pid}/xp}. */
public record XpRequest(UUID encounterId, List<UUID> ownedIds, int xp) {
}
