package it.unipi.ase.tamer.auth.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** INFRA-10: canonical access policy parsing, specificity, traversal rejection, and duplicate detection. */
class AccessPolicyTest {

    @Test
    void loadsCanonicalPolicyFromClasspath() {
        AccessPolicy policy = new AccessPolicy();
        policy.init();

        assertFalse(policy.rules().isEmpty());
        AccessPolicy.CompiledRule me = policy.match("GET", "/api/players/me").orElseThrow();
        assertTrue(me.isRoleProtected());
        assertEquals(Set.of("player"), me.roles());

        AccessPolicy.CompiledRule health = policy.match("GET", "/health").orElseThrow();
        assertEquals("public", health.access());
        assertFalse(health.isRoleProtected());

        AccessPolicy.CompiledRule logout = policy.match("POST", "/api/auth/logout").orElseThrow();
        assertEquals("public_credential", logout.access());
    }

    @Test
    void prefersMoreSpecificLiteralRouteOverParameterizedRoute() {
        AccessPolicy policy = new AccessPolicy();
        policy.init();

        AccessPolicy.CompiledRule current = policy.match("GET", "/api/runs/current").orElseThrow();
        AccessPolicy.CompiledRule byId = policy.match("GET", "/api/runs/run-12345").orElseThrow();

        assertEquals("/api/runs/current", current.path());
        assertEquals("/api/runs/{run_id}", byId.path());
        assertTrue(current.specificity() > byId.specificity());

        AccessPolicy.CompiledRule verify = policy.match("GET", "/api/history/verify").orElseThrow();
        AccessPolicy.CompiledRule encounter = policy.match("GET", "/api/history/enc-99").orElseThrow();
        assertEquals("/api/history/verify", verify.path());
        assertEquals("/api/history/{encounter_id}", encounter.path());
    }

    @Test
    void canonicalizesQueryStringFragmentAndTrailingSlash() {
        AccessPolicy policy = new AccessPolicy();
        policy.init();

        Optional<AccessPolicy.CompiledRule> matched = policy.match("GET", "/api/monsters/7/?expand=true#top");
        assertTrue(matched.isPresent());
        assertEquals("/api/monsters/{id}", matched.get().path());
    }

    @Test
    void rejectsPathTraversalAndEncodedSeparators() {
        AccessPolicy policy = new AccessPolicy();
        policy.init();

        for (String bad : new String[]{
                "/api/players/../monsters",
                "/api/./players/me",
                "/api/players/%2e%2e/monsters",
                "/api/players/%2E%2E/monsters",
                "/api/players/%2fme",
                "/api/players/%5cme",
                "/api/players/%00",
                "/api//players/me",
                "/api/players\\me",
                "api/players/me"
        }) {
            assertNull(AccessPolicy.canonicalizePath(bad), "Should reject path: " + bad);
            assertTrue(policy.match("GET", bad).isEmpty(), "Should not match path: " + bad);
        }
    }

    @Test
    void rejectsAmbiguousDuplicateRules() {
        String json = """
                {
                  "version": 1,
                  "rules": [
                    { "service": "encounters", "method": "GET", "path": "/api/runs/{run_id}", "access": "roles", "roles": ["player"] },
                    { "service": "encounters", "method": "GET", "path": "/api/runs/{other_id}", "access": "roles", "roles": ["player"] }
                  ]
                }
                """;
        assertThrows(IllegalStateException.class, () ->
                AccessPolicy.parse(new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8))));
    }

    @Test
    void rejectsInvalidAccessModesAndMissingRoles() {
        String missingRoles = """
                {
                  "version": 1,
                  "rules": [
                    { "service": "players", "method": "GET", "path": "/api/players/me", "access": "roles", "roles": [] }
                  ]
                }
                """;
        assertThrows(IllegalStateException.class, () ->
                AccessPolicy.parse(new ByteArrayInputStream(missingRoles.getBytes(StandardCharsets.UTF_8))));

        String badAccess = """
                {
                  "version": 1,
                  "rules": [
                    { "service": "players", "method": "GET", "path": "/api/players/me", "access": "unknown" }
                  ]
                }
                """;
        assertThrows(IllegalStateException.class, () ->
                AccessPolicy.parse(new ByteArrayInputStream(badAccess.getBytes(StandardCharsets.UTF_8))));
    }
}
