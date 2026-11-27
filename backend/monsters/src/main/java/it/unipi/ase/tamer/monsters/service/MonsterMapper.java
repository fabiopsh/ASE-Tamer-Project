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
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

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
        Ability ability = Ability.findById(monster.abilityId);
        return owned(owned, monster, ability);
    }

    public static OwnedMonsterResponse owned(OwnedMonster owned, Monster monster, Ability ability) {
        return new OwnedMonsterResponse(owned.ownedId, monster.monsterId, monster.name, owned.nickname,
                monster.element, monster.rarity, monster.imageUrl, owned.level, owned.xp,
                Leveling.xpToNextLevel(owned.level), StatCalculator.stats(monster, owned.level),
                AbilityResponse.from(ability), owned.source, owned.encounterId, owned.runId,
                owned.status, owned.obtainedAt);
    }

    /** Batch-loads monsters and abilities once to avoid N+1 queries on pages and instance lookups. */
    public static List<OwnedMonsterResponse> ownedBatch(List<OwnedMonster> ownedList) {
        if (ownedList.isEmpty()) {
            return List.of();
        }
        Set<Integer> monsterIds = ownedList.stream()
                .map(o -> o.monsterId)
                .collect(Collectors.toSet());
        Map<Integer, Monster> monstersById = Monster.<Monster>list("monsterId in ?1", monsterIds).stream()
                .collect(Collectors.toMap(m -> m.monsterId, Function.identity()));
        Set<Integer> abilityIds = monstersById.values().stream()
                .map(m -> m.abilityId)
                .collect(Collectors.toSet());
        Map<Integer, Ability> abilitiesById = Ability.<Ability>list("abilityId in ?1", abilityIds).stream()
                .collect(Collectors.toMap(a -> a.abilityId, Function.identity()));
        return ownedList.stream()
                .map(o -> {
                    Monster monster = monstersById.get(o.monsterId);
                    return owned(o, monster, abilitiesById.get(monster.abilityId));
                })
                .toList();
    }
}
