package it.unipi.ase.tamer.teams.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AccessPolicyTest {

    private AccessPolicy policy;

    @BeforeEach
    void setUp() {
        policy = new AccessPolicy();
        policy.init();
    }

    @Test
    void loadsPolicyAndMatchesTeamsEndpoints() {
        assertFalse(policy.rules().isEmpty());

        var list = policy.match("GET", "/api/teams").orElseThrow();
        assertEquals("teams", list.service());
        assertEquals(Set.of("player"), list.roles());

        var validate = policy.match("POST", "/api/teams/validate").orElseThrow();
        assertEquals(Set.of("player"), validate.roles());

        var update = policy.match("PUT", "/api/teams/44444444-4444-4444-8444-444444444444").orElseThrow();
        assertEquals(Set.of("player"), update.roles());

        var internalTeam = policy.match("GET", "/internal/teams/44444444-4444-4444-8444-444444444444")
                .orElseThrow();
        assertEquals(Set.of("service"), internalTeam.roles());

        var reset = policy.match("POST", "/internal/teams/44444444-4444-4444-8444-444444444444/reset")
                .orElseThrow();
        assertEquals(Set.of("service"), reset.roles());
    }

    @Test
    void rejectsTraversalAndEncodedSeparators() {
        assertTrue(policy.match("GET", "/api/teams/../teams").isEmpty());
        assertTrue(policy.match("GET", "/api/teams/%2e%2e").isEmpty());
        assertTrue(policy.match("GET", "/api/teams%2f1").isEmpty());
        assertTrue(policy.match("GET", "/api//teams").isEmpty());
        assertTrue(policy.match("PATCH", "/api/teams/44444444-4444-4444-8444-444444444444").isEmpty());
    }
}
