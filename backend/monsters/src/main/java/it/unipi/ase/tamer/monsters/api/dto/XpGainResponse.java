package it.unipi.ase.tamer.monsters.api.dto;

import java.util.UUID;

public record XpGainResponse(UUID ownedId, int levelBefore, int levelAfter, int xp) {
}
