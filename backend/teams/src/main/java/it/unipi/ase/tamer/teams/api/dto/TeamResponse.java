package it.unipi.ase.tamer.teams.api.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record TeamResponse(UUID teamId, String name, UUID runId,
                           List<TeamMemberResponse> members, Instant createdAt, Instant updatedAt) {
}
