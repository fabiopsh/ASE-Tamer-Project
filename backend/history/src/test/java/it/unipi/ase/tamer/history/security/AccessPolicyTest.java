package it.unipi.ase.tamer.history.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Verifies the canonical access policy packaged into history covers all service routes including /verify. */
class AccessPolicyTest {

    private static AccessPolicy loaded() {
        AccessPolicy policy = new AccessPolicy();
        policy.init();
        return policy;
    }

    @Test
    void matchesAllHistoryRoutesWithExpectedRolesAndSpecificity() {
        AccessPolicy policy = loaded();
        assertRole(policy, "GET", "/api/history", "/api/history", "player");
        assertRole(policy, "GET", "/api/history/verify", "/api/history/verify", "player");
        assertRole(policy, "GET", "/api/history/11111111-1111-4111-8111-111111111111",
                "/api/history/{encounter_id}", "player");
        assertRole(policy, "POST", "/internal/history", "/internal/history", "service");
    }

    @Test
    void rejectsUnknownPathsAndPathTraversal() {
        AccessPolicy policy = loaded();
        assertTrue(policy.match("DELETE", "/api/history/11111111-1111-4111-8111-111111111111").isEmpty());
        assertTrue(policy.match("GET", "/api/history/../internal/history").isEmpty());
        assertTrue(policy.match("GET", "/api/history/%2e%2e/verify").isEmpty());
        assertTrue(policy.match("GET", "/api//history").isEmpty());
    }

    private static void assertRole(AccessPolicy policy, String method, String path, String expectedRulePath,
                                   String role) {
        Optional<AccessPolicy.CompiledRule> match = policy.match(method, path);
        assertTrue(match.isPresent(), "Missing rule for " + method + " " + path);
        AccessPolicy.CompiledRule rule = match.get();
        assertEquals("history", rule.service());
        assertEquals(expectedRulePath, rule.path());
        assertTrue(rule.isRoleProtected());
        assertTrue(rule.allowsAnyRole(Set.of(role)));
        assertFalse(rule.allowsAnyRole(Set.of("player".equals(role) ? "service" : "player")));
    }
}
