package it.unipi.ase.tamer.encounters.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Verifies the canonical access policy packaged into encounters covers all service routes. */
class AccessPolicyTest {

    private static AccessPolicy loaded() {
        AccessPolicy policy = new AccessPolicy();
        policy.init();
        return policy;
    }

    @Test
    void matchesAllEncountersRoutesWithExpectedRoles() {
        AccessPolicy policy = loaded();
        assertRole(policy, "GET", "/api/encounters", "player");
        assertRole(policy, "GET", "/api/encounters/11", "player");
        assertRole(policy, "GET", "/api/runs", "player");
        assertRole(policy, "POST", "/api/runs", "player");
        assertRole(policy, "GET", "/api/runs/current", "player");
        assertRole(policy, "POST", "/api/runs/current/abandon", "player");
        assertRole(policy, "GET", "/api/runs/11111111-1111-4111-8111-111111111111", "player");
        assertRole(policy, "POST", "/api/battles", "player");
        assertRole(policy, "GET", "/api/battles/current", "player");
        assertRole(policy, "POST", "/api/battles/current/actions", "player");
        assertRole(policy, "POST", "/internal/runs", "service");
    }

    @Test
    void rejectsUnknownPathsAndPathTraversal() {
        AccessPolicy policy = loaded();
        assertTrue(policy.match("GET", "/api/does-not-exist").isEmpty());
        assertTrue(policy.match("DELETE", "/api/battles/current").isEmpty());
        assertTrue(policy.match("GET", "/api/runs/../internal/runs").isEmpty());
        assertTrue(policy.match("GET", "/api/runs/%2e%2e/internal").isEmpty());
        assertTrue(policy.match("GET", "/api//encounters").isEmpty());
    }

    private static void assertRole(AccessPolicy policy, String method, String path, String role) {
        Optional<AccessPolicy.CompiledRule> match = policy.match(method, path);
        assertTrue(match.isPresent(), "Missing rule for " + method + " " + path);
        AccessPolicy.CompiledRule rule = match.get();
        assertEquals("encounters", rule.service());
        assertTrue(rule.isRoleProtected());
        assertTrue(rule.allowsAnyRole(Set.of(role)));
        assertFalse(rule.allowsAnyRole(Set.of("player".equals(role) ? "service" : "player")));
    }
}
