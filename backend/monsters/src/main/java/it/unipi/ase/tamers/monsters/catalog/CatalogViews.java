package it.unipi.ase.tamers.monsters.catalog;

import it.unipi.ase.tamers.monsters.catalog.CatalogDtos.AbilityView;
import it.unipi.ase.tamers.monsters.catalog.CatalogDtos.GrowthBlock;
import it.unipi.ase.tamers.monsters.catalog.CatalogDtos.MonsterDetail;
import it.unipi.ase.tamers.monsters.catalog.CatalogDtos.MonsterSummary;
import it.unipi.ase.tamers.monsters.catalog.CatalogDtos.StatBlock;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/** Maps catalog entities to their JSON views. */
@ApplicationScoped
public class CatalogViews {
    @Inject
    Catalog catalog;
    @Inject
    Elements elements;

    public AbilityView ability(Ability a) {
        return new AbilityView(a.abilityId, a.name, a.scope, a.element, a.effectType, a.effectValue, a.trigger,
                a.description);
    }

    public AbilityView abilityOf(Monster m) {
        return ability(catalog.get().abilityById().get(m.abilityId));
    }

    public MonsterSummary summary(Monster m) {
        return new MonsterSummary(m.monsterId, m.name, m.element, m.rarity, m.imageUrl);
    }

    public MonsterDetail detail(Monster m) {
        return new MonsterDetail(m.monsterId, m.name, m.element, m.rarity, m.description, m.imageUrl,
                m.baseHp, m.baseAttack, m.baseDefense, m.baseSpeed,
                m.growthHp, m.growthAttack, m.growthDefense, m.growthSpeed,
                m.abilityId, abilityOf(m), elements.strongAgainst(m.element), elements.weakAgainst(m.element));
    }

    public static StatBlock stats(Monster m, int level) {
        return new StatBlock(
                Growth.effectiveStat(m.baseHp, m.growthHp, level),
                Growth.effectiveStat(m.baseAttack, m.growthAttack, level),
                Growth.effectiveStat(m.baseDefense, m.growthDefense, level),
                Growth.effectiveStat(m.baseSpeed, m.growthSpeed, level));
    }

    public static StatBlock baseStats(Monster m) {
        return new StatBlock(m.baseHp, m.baseAttack, m.baseDefense, m.baseSpeed);
    }

    public static GrowthBlock growth(Monster m) {
        return new GrowthBlock(m.growthHp, m.growthAttack, m.growthDefense, m.growthSpeed);
    }
}
