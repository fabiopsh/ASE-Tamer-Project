package it.unipi.ase.tamer.monsters.api.dto;

import java.time.Instant;
import java.util.UUID;

/** US-09, US-11: a personal instance, never the mutable JPA entity. */
public record OwnedMonsterResponse(UUID ownedId, int monsterId, String name, String element,
                                   String rarity, String imageUrl, int level, int xp,
                                   int xpToNextLevel, StatBlock stats, AbilityResponse ability,
                                   String source, UUID encounterId, UUID runId, String status,
                                   Instant obtainedAt) {
}
