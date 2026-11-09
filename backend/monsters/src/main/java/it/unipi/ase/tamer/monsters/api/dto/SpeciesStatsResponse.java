package it.unipi.ase.tamer.monsters.api.dto;

/** Used by encounters to generate the wild opponent. */
public record SpeciesStatsResponse(int monsterId, String name, String element,
                                   String rarity, int level, StatBlock stats, AbilityResponse ability) {
}
