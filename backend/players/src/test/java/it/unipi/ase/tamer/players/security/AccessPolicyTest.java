package it.unipi.ase.tamer.players.security;

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
    void loadsPolicyAndMatchesPlayersEndpoints() {
        assertFalse(policy.rules().isEmpty());

        var getMe = policy.match("GET", "/api/players/me").orElseThrow();
        assertEquals("players", getMe.service());
        assertEquals(Set.of("player"), getMe.roles());

        var patchMe = policy.match("PATCH", "/api/players/me").orElseThrow();
        assertEquals("players", patchMe.service());
        assertEquals(Set.of("player"), patchMe.roles());

        var starters = policy.match("POST", "/api/players/me/starters").orElseThrow();
        assertEquals(Set.of("player"), starters.roles());

        var createInternal = policy.match("POST", "/internal/players").orElseThrow();
        assertEquals(Set.of("service"), createInternal.roles());

        var newRun = policy.match("POST", "/internal/players/11111111-1111-4111-8111-111111111111/new-run")
                .orElseThrow();
        assertEquals(Set.of("service"), newRun.roles());
    }

    @Test
    void rejectsTraversalAndEncodedSeparators() {
        assertTrue(policy.match("GET", "/api/players/../players/me").isEmpty());
        assertTrue(policy.match("GET", "/api/players/%2e%2e/me").isEmpty());
        assertTrue(policy.match("GET", "/api/players%2fme").isEmpty());
        assertTrue(policy.match("GET", "/api//players/me").isEmpty());
        assertTrue(policy.match("GET", "/api/players\\me").isEmpty());
        assertTrue(policy.match("DELETE", "/api/players/me").isEmpty());
    }
}
