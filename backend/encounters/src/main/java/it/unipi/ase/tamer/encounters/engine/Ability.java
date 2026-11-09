package it.unipi.ase.tamer.encounters.engine;

import java.util.Objects;

/**
 * An ability as data (catalogue row of {@code abilities.json}). The logic lives only in {@link AbilityEngine}.
 *
 * @param abilityId   catalogue id
 * @param name        display name, also used in the {@code abilities} list of attack events
 * @param description display text
 * @param effectType  e.g. {@code damage_boost}; unknown types are no-ops
 * @param effectValue the {@code v} of the formulas (a percentage)
 * @param trigger     e.g. {@code always}; unknown triggers are never active
 */
public record Ability(int abilityId, String name, String description, String effectType, int effectValue,
                      String trigger) {

    public Ability {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(effectType, "effectType");
        Objects.requireNonNull(trigger, "trigger");
        description = description == null ? "" : description;
    }
}
