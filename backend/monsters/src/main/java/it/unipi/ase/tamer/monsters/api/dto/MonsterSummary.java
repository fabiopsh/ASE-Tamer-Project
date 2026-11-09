package it.unipi.ase.tamer.monsters.api.dto;

import it.unipi.ase.tamer.monsters.domain.Monster;

public record MonsterSummary(int monsterId, String name, String element,
                             String rarity, String imageUrl) {
    public static MonsterSummary from(Monster m) {
        return new MonsterSummary(m.monsterId, m.name, m.element, m.rarity, m.imageUrl);
    }
}
