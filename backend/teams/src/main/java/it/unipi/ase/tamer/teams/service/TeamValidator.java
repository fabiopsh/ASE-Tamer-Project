package it.unipi.ase.tamer.teams.service;

import it.unipi.ase.tamer.teams.api.dto.TeamError;
import it.unipi.ase.tamer.teams.api.dto.TeamValidationResponse;
import it.unipi.ase.tamer.teams.client.MonsterInstance;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Single set of team rules for validate/create/update/revalidation; pure and unit-testable. */
@ApplicationScoped
public class TeamValidator {

    public record Result(List<TeamError> errors, UUID runId) {
        public boolean valid() {
            return errors.isEmpty();
        }

        public TeamValidationResponse response() {
            return new TeamValidationResponse(valid(), errors);
        }
    }

    public Result validate(String name, List<UUID> members, List<MonsterInstance> instances,
                           boolean isCreate, long existingTeams) {
        List<TeamError> errors = new ArrayList<>();
        if (name != null && (name.isBlank() || name.length() > 30
                || !name.matches("[A-Za-z0-9 _-]+"))) {
            errors.add(error("NAME_INVALID", "Name must be 1-30 letters, digits, spaces, underscores or dashes.", null));
        }
        if (name == null && isCreate) {
            errors.add(error("NAME_INVALID", "Team name is required.", null));
        }
        if (members == null || members.isEmpty() || members.size() > 3) {
            errors.add(error("TEAM_SIZE", "Team must contain between 1 and 3 monsters.", null));
        }
        if (members == null) {
            return new Result(List.copyOf(errors), null);
        }
        if (new HashSet<>(members).size() != members.size()) {
            errors.add(error("DUPLICATE_MEMBER", "The same monster instance cannot appear twice.", null));
        }
        Map<UUID, MonsterInstance> byId = new HashMap<>();
        for (MonsterInstance instance : instances) {
            byId.put(instance.ownedId(), instance);
        }
        UUID runId = null;
        for (UUID ownedId : members) {
            MonsterInstance monster = byId.get(ownedId);
            if (monster == null) {
                errors.add(error("NOT_OWNED", "Monster is missing or does not belong to the player.", ownedId));
                continue;
            }
            if (!"active".equals(monster.status())) {
                errors.add(error("NOT_ACTIVE", "Monster is not active.", ownedId));
            }
            if (runId == null) {
                runId = monster.runId();
            } else if (!runId.equals(monster.runId())) {
                errors.add(error("MIXED_RUNS", "All team members must belong to the same run.", ownedId));
            }
        }
        if (isCreate && existingTeams >= 5) {
            errors.add(error("TEAM_LIMIT", "Maximum five teams per run.", null));
        }
        return new Result(List.copyOf(errors), runId);
    }

    private static TeamError error(String code, String message, UUID ownedId) {
        return new TeamError(code, message, ownedId);
    }
}
