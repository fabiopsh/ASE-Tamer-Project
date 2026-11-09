package it.unipi.ase.tamer.encounters.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** {@code GET /internal/monsters/{id}/stats?level=}. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SpeciesStats(int monsterId, String name, String element, String rarity, int level, StatBlock stats,
                           AbilityInfo ability) {
}
