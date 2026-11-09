package it.unipi.ase.tamer.encounters.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Item of {@code GET /internal/monsters}. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record MonsterCandidate(int monsterId, String element, String rarity) {
}
