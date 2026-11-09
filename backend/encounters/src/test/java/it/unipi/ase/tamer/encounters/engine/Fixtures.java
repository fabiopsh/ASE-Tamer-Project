package it.unipi.ase.tamer.encounters.engine;

import java.util.List;

/** Builders shared by the engine tests. */
final class Fixtures {

    static final TemplateInfo NORMAL = new TemplateInfo(11, "Test Meadow", 1, "dream", false);
    static final TemplateInfo BOSS = new TemplateInfo(14, "Test Lair", 1, "memory", true);

    private Fixtures() {
    }

    static Combatant mon(String name, String element, int hp, int atk, int def, int spd, Ability ability) {
        return new Combatant(name + "-id", 1, name, element, "common", 5, hp, atk, def, spd, ability);
    }

    static Combatant mon(String name, String element, int hp, int atk, int def, int spd) {
        return mon(name, element, hp, atk, def, spd, null);
    }

    static Ability ability(String name, String effect, int value, String trigger) {
        return new Ability(99, name, name + " description", effect, value, trigger);
    }

    static Battle battle(long seed, Combatant wild, Combatant... team) {
        return new Battle(seed, new EncounterSpec(NORMAL, wild), List.of(team), ElementChart.defaults());
    }

    /** Applies {@code attack} until the battle ends (bounded). */
    static Battle attackUntilEnd(Battle b) {
        for (int i = 0; i < 500 && !b.isFinished(); i++) {
            b.apply(Action.attack());
        }
        return b;
    }

    static BattleEvent firstAttack(RoundResult r, String side) {
        return r.events().stream().filter(e -> BattleEvent.ATTACK.equals(e.type()) && side.equals(e.side()))
                .findFirst().orElseThrow();
    }

    static Fighter fighter(Combatant c, String side) {
        return new Fighter(c, side, side.equals(Fighter.TEAM) ? 1 : 0, HpCalculator.teamMaxHp(c.hp()));
    }
}
