package it.unipi.ase.tamer.teams.api.dto;

import java.util.UUID;

public record TeamMemberResponse(int position, UUID ownedId, int monsterId, String name,
                                 int level, String element, String imageUrl) {
}
