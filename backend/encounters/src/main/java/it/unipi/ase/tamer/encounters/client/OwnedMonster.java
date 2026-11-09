package it.unipi.ase.tamer.encounters.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.UUID;

/** The owned instance created by a reward (only the fields used here). */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OwnedMonster(UUID ownedId, int monsterId, int level) {
}
