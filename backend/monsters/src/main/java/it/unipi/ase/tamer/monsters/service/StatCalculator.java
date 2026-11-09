package it.unipi.ase.tamer.monsters.service;

import it.unipi.ase.tamer.monsters.api.dto.StatBlock;
import it.unipi.ase.tamer.monsters.domain.Monster;
import java.math.BigDecimal;
import java.math.RoundingMode;

/** Pure stat rules; only monsters owns this calculation. */
public final class StatCalculator {

    private StatCalculator() {
    }

    public static int effective(int base, BigDecimal growth, int level) {
        if (level < 1 || level > 50) {
            throw new IllegalArgumentException("Level must be between 1 and 50.");
        }
        return BigDecimal.valueOf(base).add(growth.multiply(BigDecimal.valueOf(level - 1)))
                .setScale(0, RoundingMode.FLOOR).intValueExact();
    }

    public static StatBlock stats(Monster monster, int level) {
        return new StatBlock(effective(monster.baseHp, monster.growthHp, level),
                effective(monster.baseAttack, monster.growthAttack, level),
                effective(monster.baseDefense, monster.growthDefense, level),
                effective(monster.baseSpeed, monster.growthSpeed, level));
    }
}
