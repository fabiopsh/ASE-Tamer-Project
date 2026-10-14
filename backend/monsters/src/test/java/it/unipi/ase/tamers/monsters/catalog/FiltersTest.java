package it.unipi.ase.tamers.monsters.catalog;

import static org.junit.jupiter.api.Assertions.*;

import it.unipi.ase.tamers.monsters.common.ApiException;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class FiltersTest {

    private static Monster monster(int id, String name, String element, String rarity) {
        Monster m = new Monster();
        m.monsterId = id;
        m.name = name;
        m.element = element;
        m.rarity = rarity;
        return m;
    }

    @Test
    void elementIsCaseInsensitiveAndBlankMeansNoFilter() {
        assertEquals(Element.DREAM, Filters.element("dream"));
        assertEquals(Element.CHAOS, Filters.element(" Chaos "));
        assertNull(Filters.element(null));
        assertNull(Filters.element("  "));
    }

    @Test
    void unknownElementIsA400ListingTheAllowedValues() {
        ApiException e = assertThrows(ApiException.class, () -> Filters.element("fire"));
        assertEquals(400, e.status());
        assertEquals("validation_error", e.error());
        assertTrue(e.getMessage().contains("fire"));
        assertTrue(e.getMessage().contains("gravity"));
    }

    @Test
    void rarityIsValidatedAgainstTheEnum() {
        assertEquals(Rarity.LEGENDARY, Filters.rarity("LEGENDARY"));
        assertEquals(Rarity.RARE, Filters.rarity("rare"));
        assertEquals(400, assertThrows(ApiException.class, () -> Filters.rarity("epic")).status());
    }

    @Test
    void intIdsParsesCommaSeparatedList() {
        assertEquals(List.of(1, 2, 3), List.copyOf(Filters.intIds("1,2, 3")));
        assertEquals(List.of(7), List.copyOf(Filters.intIds("7,,7")));
        assertNull(Filters.intIds(null));
        assertNull(Filters.intIds(""));
    }

    @Test
    void badIdsAre400() {
        assertEquals(400, assertThrows(ApiException.class, () -> Filters.intIds("1,two")).status());
        String tooMany = IntStream.range(0, Filters.MAX_IDS + 1).mapToObj(String::valueOf).collect(Collectors.joining(","));
        assertEquals(400, assertThrows(ApiException.class, () -> Filters.intIds(tooMany)).status());
    }

    @Test
    void uuidIdsKeepOrderAndDropDuplicates() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        assertEquals(List.of(b, a), List.copyOf(Filters.uuidIds(b + "," + a + "," + b)));
        assertTrue(Filters.uuidIds(null).isEmpty());
    }

    @Test
    void malformedUuidIs400() {
        assertEquals(400, assertThrows(ApiException.class, () -> Filters.uuid("not-a-uuid")).status());
        assertEquals(400, assertThrows(ApiException.class, () -> Filters.uuidIds(UUID.randomUUID() + ",xyz")).status());
        assertEquals(400, assertThrows(ApiException.class, () -> Filters.intId("abc")).status());
        assertEquals(12, Filters.intId("12"));
    }

    @Test
    void monsterFilterCombinesAllCriteria() {
        Monster a = monster(1, "Snoozle", "dream", "common");
        Monster b = monster(2, "Pillowpup", "dream", "rare");
        Monster c = monster(3, "Gearbit", "time", "common");

        assertTrue(MonsterFilter.parse(null, null, null, null).matches(a));
        assertEquals(List.of(a, b), List.of(a, b, c).stream().filter(MonsterFilter.parse("dream", null, null, null)::matches).toList());
        assertEquals(List.of(a, c), List.of(a, b, c).stream().filter(MonsterFilter.parse(null, "common", null, null)::matches).toList());
        assertEquals(List.of(a), List.of(a, b, c).stream().filter(MonsterFilter.parse("dream", "common", null, null)::matches).toList());
        assertEquals(List.of(b, c), List.of(a, b, c).stream().filter(MonsterFilter.parse(null, null, "2,3", null)::matches).toList());
        assertEquals(List.of(b), List.of(a, b, c).stream().filter(MonsterFilter.parse(null, null, null, "PILLOW")::matches).toList());
        assertEquals(List.of(), List.of(a, b, c).stream().filter(MonsterFilter.parse("time", "rare", null, null)::matches).toList());
    }

    @Test
    void monsterFilterRejectsInvalidValues() {
        assertThrows(ApiException.class, () -> MonsterFilter.parse("lava", null, null, null));
        assertThrows(ApiException.class, () -> MonsterFilter.parse(null, "mythic", null, null));
        assertThrows(ApiException.class, () -> MonsterFilter.parse(null, null, "a", null));
    }

    @Test
    void blankQueryMeansNoSearch() {
        assertNull(MonsterFilter.parse(null, null, null, "   ").query());
    }
}
