package it.unipi.ase.tamer.teams.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.UUID;

/** Read model from monsters: unknown fields are allowed because the owner may add new ones. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record MonsterInstance(UUID ownedId, int monsterId, String name, String element,
                              String rarity, String imageUrl, int level, UUID runId,
                              String status, Stats stats, Ability ability) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Stats(int hp, int attack, int defense, int speed) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Ability(int abilityId, String name, String description,
                          String effectType, int effectValue, String trigger) {
    }
}
