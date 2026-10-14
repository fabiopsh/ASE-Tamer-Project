package it.unipi.ase.tamers.encounters.engine;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/** One entry of the battle log; null fields are omitted from the JSON. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record BattleEvent(
        @JsonProperty("round") int round,
        @JsonProperty("type") String type,
        @JsonProperty("actor") String actor,
        @JsonProperty("target") String target,
        @JsonProperty("damage") Integer damage,
        @JsonProperty("critical") Boolean critical,
        @JsonProperty("effectiveness") String effectiveness,
        @JsonProperty("ability") String ability,
        @JsonProperty("actor_hp") Integer actorHp,
        @JsonProperty("target_hp") Integer targetHp,
        @JsonProperty("text") String text) {
}
