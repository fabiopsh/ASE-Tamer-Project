package it.unipi.ase.tamers.monsters.catalog;

/** JSON shapes of the catalog (property names are turned into snake_case by Jackson). */
public final class CatalogDtos {
    private CatalogDtos() {}

    public record AbilityView(int abilityId, String name, String scope, String element, String effectType,
                              int effectValue, String trigger, String description) {}

    public record MonsterSummary(int monsterId, String name, String element, String rarity, String imageUrl) {}

    public record MonsterDetail(int monsterId, String name, String element, String rarity, String description,
                                String imageUrl, int baseHp, int baseAttack, int baseDefense, int baseSpeed,
                                double growthHp, double growthAttack, double growthDefense, double growthSpeed,
                                int abilityId, AbilityView ability, String strongAgainst, String weakAgainst) {}

    public record StatBlock(int hp, int attack, int defense, int speed) {}

    public record GrowthBlock(double hp, double attack, double defense, double speed) {}
}
