package it.unipi.ase.tamer.encounters.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import it.unipi.ase.tamer.encounters.engine.Ability;

/** Ability as returned by monsters and teams. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AbilityInfo(int abilityId, String name, String description, String effectType, int effectValue,
                          String trigger) {

    /** Engine input; a missing effect or trigger becomes a no-op. */
    public Ability toEngine() {
        return new Ability(abilityId, name == null ? "" : name, description,
                effectType == null ? "none" : effectType, effectValue, trigger == null ? "never" : trigger);
    }
}
