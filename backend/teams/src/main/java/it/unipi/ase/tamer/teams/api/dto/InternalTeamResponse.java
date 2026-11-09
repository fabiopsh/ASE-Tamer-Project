package it.unipi.ase.tamer.teams.api.dto;

import java.util.List;
import java.util.UUID;

/** Revalidated team snapshot for starting a battle. */
public record InternalTeamResponse(UUID teamId, String name, UUID runId,
                                   List<InternalTeamMember> members) {
}
