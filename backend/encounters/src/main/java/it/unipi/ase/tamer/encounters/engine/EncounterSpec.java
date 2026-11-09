package it.unipi.ase.tamer.encounters.engine;

import java.util.Objects;

/**
 * Immutable description of the encounter: template, the wild monster already chosen (by {@link WildSelector} for
 * a normal template, the boss species for a boss template) and whether it is a boss fight (boss HP multiplier).
 */
public record EncounterSpec(TemplateInfo template, Combatant wild, boolean isBoss) {

    public EncounterSpec {
        Objects.requireNonNull(template, "template");
        Objects.requireNonNull(wild, "wild");
    }

    /** Boss flag taken from the template. */
    public EncounterSpec(TemplateInfo template, Combatant wild) {
        this(template, wild, template.isBoss());
    }
}
