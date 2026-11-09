package it.unipi.ase.tamer.encounters.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Effective stats at a level, computed by monsters. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record StatBlock(int hp, int attack, int defense, int speed) {
}
