package it.unipi.ase.tamers.teams.team;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TeamRulesTest {
    private final UUID a = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private final UUID b = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private final UUID c = UUID.fromString("00000000-0000-0000-0000-000000000003");
    private final UUID d = UUID.fromString("00000000-0000-0000-0000-000000000004");
    private final Set<UUID> owned = Set.of(a, b, c, d);

    @Test
    void validTeamHasNoErrors() {
        assertEquals(List.of(), TeamRules.validate("Alpha", List.of(a, b, c), owned));
    }

    @Test
    void singleMonsterIsValid() {
        assertEquals(List.of(), TeamRules.validate("Solo", List.of(a), owned));
    }

    @Test
    void emptyTeamIsRejected() {
        assertEquals(List.of("a team needs between 1 and 3 monsters"),
                TeamRules.validate("Alpha", List.of(), owned));
    }

    @Test
    void nullMembersAreTreatedAsEmpty() {
        assertEquals(List.of("a team needs between 1 and 3 monsters"),
                TeamRules.validate("Alpha", null, owned));
    }

    @Test
    void tooManyMonstersIsRejected() {
        assertEquals(List.of("a team needs between 1 and 3 monsters"),
                TeamRules.validate("Alpha", List.of(a, b, c, d), owned));
    }

    @Test
    void duplicateMonsterIsRejectedOnce() {
        assertEquals(List.of("duplicate monster " + a),
                TeamRules.validate("Alpha", List.of(a, b, a), owned));
    }

    @Test
    void duplicateOfTwoIdsReportsEach() {
        assertEquals(List.of("a team needs between 1 and 3 monsters", "duplicate monster " + a,
                        "duplicate monster " + b),
                TeamRules.validate("Alpha", List.of(a, b, a, b), owned));
    }

    @Test
    void monsterNotOwnedIsRejected() {
        UUID stranger = UUID.fromString("00000000-0000-0000-0000-0000000000ff");
        assertEquals(List.of("monster " + stranger + " is not in your collection"),
                TeamRules.validate("Alpha", List.of(a, stranger), owned));
    }

    @Test
    void notOwnedIsReportedOncePerDistinctId() {
        UUID stranger = UUID.fromString("00000000-0000-0000-0000-0000000000ff");
        assertEquals(List.of("duplicate monster " + stranger, "monster " + stranger + " is not in your collection"),
                TeamRules.validate("Alpha", List.of(stranger, stranger), Set.of(a)));
    }

    @Test
    void nullMonsterIdIsRejected() {
        List<UUID> ids = Arrays.asList(a, null);
        assertEquals(List.of("monster id is required"), TeamRules.validate("Alpha", ids, owned));
    }

    @Test
    void emptyNameIsRejected() {
        assertEquals(List.of("name is required"), TeamRules.validate(null, List.of(a), owned));
        assertEquals(List.of("name must not be blank"), TeamRules.validate("", List.of(a), owned));
        assertEquals(List.of("name must not be blank"), TeamRules.validate("   ", List.of(a), owned));
    }

    @Test
    void nameLongerThanThirtyIsRejected() {
        String thirtyOne = "a".repeat(31);
        assertEquals(List.of("name must be at most 30 characters"),
                TeamRules.validate(thirtyOne, List.of(a), owned));
    }

    @Test
    void nameBoundariesAreAccepted() {
        assertEquals(List.of(), TeamRules.validate("a", List.of(a), owned));
        assertEquals(List.of(), TeamRules.validate("a".repeat(30), List.of(a), owned));
    }

    @Test
    void nameIsTrimmedBeforeLengthCheck() {
        String padded = "  " + "a".repeat(30) + "  ";
        assertEquals(List.of(), TeamRules.validate(padded, List.of(a), owned));
        assertEquals("Alpha", TeamRules.normalizeName("  Alpha  "));
    }

    @Test
    void errorsKeepRuleOrder() {
        UUID stranger = UUID.fromString("00000000-0000-0000-0000-0000000000ff");
        List<String> errors = TeamRules.validate(" ", List.of(stranger, stranger, a, b), Set.of(a));
        assertEquals(List.of(
                "name must not be blank",
                "a team needs between 1 and 3 monsters",
                "duplicate monster " + stranger,
                "monster " + stranger + " is not in your collection",
                "monster " + b + " is not in your collection"), errors);
    }

    @Test
    void validateMembersIgnoresTheName() {
        assertEquals(List.of(), TeamRules.validateMembers(List.of(a, b), owned));
        assertEquals(List.of("a team needs between 1 and 3 monsters"),
                TeamRules.validateMembers(List.of(), owned));
    }

    @Test
    void nullOwnedSetMeansNothingIsOwned() {
        assertEquals(List.of("monster " + a + " is not in your collection"),
                TeamRules.validate("Alpha", List.of(a), null));
    }

    @Test
    void creationAllowedBelowTheLimit() {
        assertEquals(List.of(), TeamRules.validateCreation(0));
        assertEquals(List.of(), TeamRules.validateCreation(TeamRules.MAX_TEAMS - 1));
    }

    @Test
    void creationRejectedAtTheLimit() {
        assertEquals(List.of("a player can have at most 5 teams"),
                TeamRules.validateCreation(TeamRules.MAX_TEAMS));
        assertEquals(List.of("a player can have at most 5 teams"),
                TeamRules.validateCreation(TeamRules.MAX_TEAMS + 1));
    }
}
