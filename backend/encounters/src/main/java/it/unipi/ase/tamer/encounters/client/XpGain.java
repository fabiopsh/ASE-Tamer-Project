package it.unipi.ase.tamer.encounters.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.UUID;

/** Item of the XP grant response. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record XpGain(UUID ownedId, int levelBefore, int levelAfter, int xp) {
}
