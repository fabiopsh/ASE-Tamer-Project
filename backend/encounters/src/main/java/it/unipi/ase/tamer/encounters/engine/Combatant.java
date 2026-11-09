package it.unipi.ase.tamer.encounters.engine;

import java.util.Objects;

/**
 * Immutable input describing one monster entering a battle. Stats are the <b>effective</b> stats at
 * {@code level}, already computed by the monsters service; the engine derives {@code max_hp} from {@code hp}.
 *
 * @param id        {@code owned_id} for a team member; any value (or {@code null}) for the wild monster
 * @param monsterId catalogue species id
 * @param name      species name (used in events)
 * @param element   element name, as in {@code elements.json}
 * @param rarity    {@code common}, {@code rare} or {@code legendary}
 * @param level     current level
 * @param hp        effective HP stat (not the battle HP)
 * @param attack    effective attack
 * @param defense   effective defense
 * @param speed     effective speed
 * @param ability   the monster's ability, or {@code null}
 */
public record Combatant(String id, int monsterId, String name, String element, String rarity, int level,
                        int hp, int attack, int defense, int speed, Ability ability) {

    public Combatant {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(element, "element");
        Objects.requireNonNull(rarity, "rarity");
        if (level < 1 || hp < 0 || attack < 0 || defense < 0 || speed < 0) {
            throw new IllegalArgumentException("Invalid level or stats for " + name + ".");
        }
    }
}
