package it.unipi.ase.tamer.monsters.api.dto;

public record MonsterDetail(int monsterId, String name, String element, String rarity,
                            String imageUrl, String description, StatBlock baseStats,
                            GrowthBlock growth, AbilityResponse ability) {
}
