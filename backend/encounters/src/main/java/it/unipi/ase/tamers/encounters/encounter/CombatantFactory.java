package it.unipi.ase.tamers.encounters.encounter;

import it.unipi.ase.tamers.encounters.client.ClientDtos.MonsterDetail;
import it.unipi.ase.tamers.encounters.client.ClientDtos.OwnedMonster;
import it.unipi.ase.tamers.encounters.engine.BoardGenerator;
import it.unipi.ase.tamers.encounters.engine.Combatant;

/** Builds engine combatants from service data (pure, no CDI). */
public final class CombatantFactory {
    private CombatantFactory() {}

    public static Combatant fromOwned(OwnedMonster m) {
        return Combatant.of(m.name(), m.element(), m.level(), m.stats(), m.ability());
    }

    public static Combatant enemy(MonsterDetail species, int level, boolean guardian) {
        return Combatant.of(species.name(), species.element(), level, species.statsAt(level), species.ability(),
                guardian ? BoardGenerator.GUARDIAN_HP_MULTIPLIER : BoardGenerator.WILD_HP_MULTIPLIER);
    }
}
