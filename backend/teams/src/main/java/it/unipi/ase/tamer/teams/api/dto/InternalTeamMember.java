package it.unipi.ase.tamer.teams.api.dto;

import it.unipi.ase.tamer.teams.client.MonsterInstance;
import java.util.UUID;

public record InternalTeamMember(int position, UUID ownedId, int monsterId, String name,
                                 int level, String element, String rarity, String imageUrl,
                                 MonsterInstance.Stats stats, MonsterInstance.Ability ability) {
}
