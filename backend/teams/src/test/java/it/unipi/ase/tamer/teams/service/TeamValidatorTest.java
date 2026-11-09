package it.unipi.ase.tamer.teams.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import it.unipi.ase.tamer.teams.client.MonsterInstance;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TeamValidatorTest {

    private final TeamValidator validator = new TeamValidator();
    private final UUID run = UUID.randomUUID();
    private final UUID a = UUID.randomUUID();
    private final UUID b = UUID.randomUUID();
    private final UUID foreign = UUID.randomUUID();

    private MonsterInstance instance(UUID id, UUID runId, String status) {
        return new MonsterInstance(id, 3, "Snoozle", "dream", "common", "/images/monsters/003.png",
                2, runId, status, new MonsterInstance.Stats(40, 20, 20, 20),
                new MonsterInstance.Ability(1, "Thick Hide", "defense", "damage_reduction", 10, "always"));
    }

    @Test
    void validOwnedActiveMembersInOrder() {
        var members = List.of(a, b);
        var result = validator.validate("Main", members,
                List.of(instance(a, run, "active"), instance(b, run, "active")), true, 0);
        assertTrue(result.valid());
        assertEquals(run, result.runId());
    }

    @Test
    void sizeAndDuplicateAndOwnershipErrorsAreCollected() {
        var result = validator.validate("Main", List.of(a, a, b, foreign),
                List.of(instance(a, run, "active"), instance(b, run, "active")), false, 0);
        assertFalse(result.valid());
        var codes = result.errors().stream().map(e -> e.code()).toList();
        assertTrue(codes.contains("TEAM_SIZE"));
        assertTrue(codes.contains("DUPLICATE_MEMBER"));
        assertTrue(codes.contains("NOT_OWNED"));
    }

    @Test
    void rejectsArchivedAndMixedRunMembers() {
        var result = validator.validate("Main", List.of(a, b),
                List.of(instance(a, run, "archived"), instance(b, UUID.randomUUID(), "active")),
                false, 0);
        assertTrue(result.errors().stream().anyMatch(e -> e.code().equals("NOT_ACTIVE")));
        assertTrue(result.errors().stream().anyMatch(e -> e.code().equals("MIXED_RUNS")));
    }

    @Test
    void validatesNameAndTeamLimit() {
        var result = validator.validate("!!!", List.of(a),
                List.of(instance(a, run, "active")), true, 5);
        assertTrue(result.errors().stream().anyMatch(e -> e.code().equals("NAME_INVALID")));
        assertTrue(result.errors().stream().anyMatch(e -> e.code().equals("TEAM_LIMIT")));
    }

    @Test
    void validateOnlyDoesNotRequireName() {
        assertTrue(validator.validate(null, List.of(a),
                List.of(instance(a, run, "active")), false, 0).valid());
    }
}
