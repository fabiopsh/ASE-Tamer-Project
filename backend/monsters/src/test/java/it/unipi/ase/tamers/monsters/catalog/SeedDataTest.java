package it.unipi.ase.tamers.monsters.catalog;

import static org.junit.jupiter.api.Assertions.*;

import it.unipi.ase.tamers.monsters.catalog.SeedData.ElementCycle;
import it.unipi.ase.tamers.monsters.catalog.SeedData.SeedAbility;
import it.unipi.ase.tamers.monsters.catalog.SeedData.SeedMonster;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/** Parses the real seed files (copied on the classpath by the pom). */
class SeedDataTest {
    private final List<SeedMonster> monsters = SeedData.readMonsters(SeedData.open(SeedData.MONSTERS_RESOURCE));
    private final List<SeedAbility> abilities = SeedData.readAbilities(SeedData.open(SeedData.ABILITIES_RESOURCE));
    private final ElementCycle cycle = SeedData.readElements(SeedData.open(SeedData.ELEMENTS_RESOURCE));

    @Test
    void hundredMonstersAndTwentyEightAbilities() {
        assertEquals(100, monsters.size());
        assertEquals(28, abilities.size());
    }

    @Test
    void everyAbilityIdReferencedByAMonsterExists() {
        Set<Integer> ids = abilities.stream().map(SeedAbility::abilityId).collect(Collectors.toSet());
        assertEquals(28, ids.size());
        for (SeedMonster m : monsters) {
            assertTrue(ids.contains(m.abilityId()), "monster " + m.monsterId() + " -> ability " + m.abilityId());
        }
    }

    @Test
    void seedIsConsistent() {
        assertEquals(List.of(), SeedData.validate(monsters, abilities, cycle));
    }

    @Test
    void raritiesFollowTheDesign() {
        Map<String, Long> byRarity = monsters.stream()
                .collect(Collectors.groupingBy(SeedMonster::rarity, Collectors.counting()));
        assertEquals(Map.of("common", 60L, "rare", 30L, "legendary", 10L), byRarity);
    }

    @Test
    void monsterFieldsAreFilled() {
        SeedMonster first = monsters.get(0);
        assertEquals(1, first.monsterId());
        assertEquals("Snoozle", first.name());
        assertEquals("dream", first.element());
        assertEquals(38, first.baseHp());
        assertEquals(1.5, first.growthSpeed());
        assertEquals(3, first.abilityId());
        assertEquals("/images/monsters/001.png", first.imageUrl());
        for (SeedMonster m : monsters) {
            assertFalse(m.description().isBlank());
            assertTrue(m.baseHp() >= 1 && m.baseHp() <= 100);
            assertTrue(m.baseAttack() >= 1 && m.baseAttack() <= 100);
            assertTrue(m.baseDefense() >= 1 && m.baseDefense() <= 100);
            assertTrue(m.baseSpeed() >= 1 && m.baseSpeed() <= 100);
            assertTrue(m.growthHp() > 0 && m.growthAttack() > 0 && m.growthDefense() > 0 && m.growthSpeed() > 0);
        }
    }

    @Test
    void everyImageUrlPointsToAFileServedFromTheClasspath() {
        for (SeedMonster m : monsters) {
            assertEquals(String.format("/images/monsters/%03d.png", m.monsterId()), m.imageUrl());
            assertNotNull(SeedDataTest.class.getResource("/META-INF/resources" + m.imageUrl()),
                    "missing image for monster " + m.monsterId());
        }
        assertNull(SeedDataTest.class.getResource("/META-INF/resources/catalog_preview.png"));
        assertNull(SeedDataTest.class.getResource("/META-INF/resources/images/catalog_preview.png"));
    }

    @Test
    void elementCycleIsASingleCycleOfSevenElements() {
        assertEquals(7, cycle.cycle().size());
        for (int i = 0; i < 7; i++) {
            String current = cycle.cycle().get(i);
            String next = cycle.cycle().get((i + 1) % 7);
            assertEquals(next, cycle.strongOf(current));
            assertEquals(current, cycle.weakOf(next));
        }
    }

    @Test
    void validationCatchesBrokenReferences() {
        SeedMonster broken = new SeedMonster(1, "X", "fire", "epic", "d", "/images/monsters/001.png",
                1, 1, 1, 1, 1, 1, 1, 1, 999);
        List<String> problems = SeedData.validate(List.of(broken), abilities, cycle);
        assertEquals(3, problems.size(), problems.toString());
    }
}
