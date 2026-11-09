package it.unipi.ase.tamer.teams.api.dto;

import java.util.List;
import java.util.UUID;

/** Domain validation deliberately returns TEAM_INVALID with all applicable errors. */
public record TeamRequest(String name, List<UUID> members) {
}
