package it.unipi.ase.tamers.monsters.catalog;

import java.util.Locale;
import java.util.Set;

/** Validated filter of {@code GET /monsters}; any null field means "do not filter on it". */
public record MonsterFilter(Element element, Rarity rarity, Set<Integer> ids, String query) {

    public static MonsterFilter parse(String element, String rarity, String ids, String q) {
        String query = q == null || q.isBlank() ? null : q.trim().toLowerCase(Locale.ROOT);
        return new MonsterFilter(Filters.element(element), Filters.rarity(rarity), Filters.intIds(ids), query);
    }

    public boolean matches(Monster m) {
        return (element == null || element.value().equals(m.element))
                && (rarity == null || rarity.value().equals(m.rarity))
                && (ids == null || ids.contains(m.monsterId))
                && (query == null || m.name.toLowerCase(Locale.ROOT).contains(query));
    }
}
