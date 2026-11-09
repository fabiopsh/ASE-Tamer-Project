package it.unipi.ase.tamer.monsters.api.dto;

import it.unipi.ase.tamer.monsters.domain.Ability;

public record AbilityResponse(int abilityId, String name, String description,
                              String scope, String element, String effectType, int effectValue,
                              String trigger) {
    public static AbilityResponse from(Ability ability) {
        return new AbilityResponse(ability.abilityId, ability.name, ability.description,
                ability.scope, ability.element, ability.effectType, ability.effectValue, ability.trigger);
    }
}
