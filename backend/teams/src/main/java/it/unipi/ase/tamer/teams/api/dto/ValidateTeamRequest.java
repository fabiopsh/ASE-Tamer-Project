package it.unipi.ase.tamer.teams.api.dto;

import java.util.List;
import java.util.UUID;

public record ValidateTeamRequest(List<UUID> members) {
}
