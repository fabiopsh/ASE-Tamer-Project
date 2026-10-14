package it.unipi.ase.tamers.teams.team;

import it.unipi.ase.tamers.teams.monsters.MonstersDtos.OwnedMonster;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class TeamDtos {
    private TeamDtos() {}

    public record TeamRequest(String name, List<UUID> ownedIds) {}

    /** name is optional: without it only the monsters are validated. */
    public record ValidateRequest(String name, List<UUID> ownedIds) {}

    public record ValidationResult(boolean valid, List<String> errors) {}

    /** members, valid and errors are recomputed on every read. */
    public record TeamView(
            UUID teamId,
            String name,
            List<UUID> ownedIds,
            List<OwnedMonster> members,
            boolean valid,
            List<String> errors,
            Instant createdAt,
            Instant updatedAt) {}

    public record InternalTeam(UUID teamId, UUID playerId, String name, List<UUID> ownedIds) {}
}
