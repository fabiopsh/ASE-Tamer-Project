package it.unipi.ase.tamer.monsters.service;

import it.unipi.ase.tamer.monsters.api.dto.AbilityResponse;
import it.unipi.ase.tamer.monsters.api.dto.GrowthBlock;
import it.unipi.ase.tamer.monsters.api.dto.MonsterDetail;
import it.unipi.ase.tamer.monsters.api.dto.OwnedMonsterResponse;
import it.unipi.ase.tamer.monsters.api.dto.SpeciesStatsResponse;
import it.unipi.ase.tamer.monsters.api.dto.StatBlock;
import it.unipi.ase.tamer.monsters.domain.Ability;
import it.unipi.ase.tamer.monsters.domain.Monster;
import it.unipi.ase.tamer.monsters.domain.OwnedMonster;

/** Central DTO mapping: never expose Panache entities. */
public final class MonsterMapper {

    private MonsterMapper() {
    }

    private static AbilityResponse ability(Monster monster) {
        Ability ability = Ability.findById(monster.abilityId);
        return AbilityResponse.from(ability);
    }

    public static MonsterDetail detail(Monster monster) {
        return new MonsterDetail(monster.monsterId, monster.name, monster.element,
                monster.rarity, monster.imageUrl, monster.description,
                new StatBlock(monster.baseHp, monster.baseAttack, monster.baseDefense, monster.baseSpeed),
                new GrowthBlock(monster.growthHp, monster.growthAttack,
                        monster.growthDefense, monster.growthSpeed), ability(monster));
    }

    public static SpeciesStatsResponse stats(Monster monster, int level) {
        return new SpeciesStatsResponse(monster.monsterId, monster.name, monster.element,
                monster.rarity, level, StatCalculator.stats(monster, level), ability(monster));
    }

    public static OwnedMonsterResponse owned(OwnedMonster owned) {
        Monster monster = Monster.findById(owned.monsterId);
        return new OwnedMonsterResponse(owned.ownedId, monster.monsterId, monster.name,
                monster.element, monster.rarity, monster.imageUrl, owned.level, owned.xp,
                Leveling.xpToNextLevel(owned.level), StatCalculator.stats(monster, owned.level),
                ability(monster), owned.source, owned.encounterId, owned.runId,
                owned.status, owned.obtainedAt);
    }
}
