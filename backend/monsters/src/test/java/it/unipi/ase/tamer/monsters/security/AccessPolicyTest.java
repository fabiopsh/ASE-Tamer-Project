package it.unipi.ase.tamer.monsters.security;

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
    void loadsPolicyAndMatchesMonstersEndpoints() {
        assertFalse(policy.rules().isEmpty());

        var listMonsters = policy.match("GET", "/api/monsters").orElseThrow();
        assertEquals("monsters", listMonsters.service());
        assertEquals(Set.of("player"), listMonsters.roles());

        var patchNickname = policy.match("PATCH", "/api/collection/22222222-2222-4222-8222-222222222222")
                .orElseThrow();
        assertEquals("monsters", patchNickname.service());
        assertEquals(Set.of("player"), patchNickname.roles());

        var spriteGet = policy.match("GET", "/images/monsters/001.png").orElseThrow();
        assertEquals("public", spriteGet.access());
        assertFalse(spriteGet.isRoleProtected());

        var spriteHead = policy.match("HEAD", "/images/monsters/001.png").orElseThrow();
        assertEquals("public", spriteHead.access());

        var instances = policy.match("GET",
                "/internal/collections/22222222-2222-4222-8222-222222222222/instances").orElseThrow();
        assertEquals(Set.of("service"), instances.roles());
    }

    @Test
    void rejectsTraversalAndUnknownMethods() {
        assertTrue(policy.match("GET", "/images/monsters/../secret.png").isEmpty());
        assertTrue(policy.match("GET", "/images/monsters/%2e%2e/secret.png").isEmpty());
        assertTrue(policy.match("GET", "/api/monsters%2f1").isEmpty());
        assertTrue(policy.match("GET", "/api//monsters").isEmpty());
        assertTrue(policy.match("DELETE", "/api/monsters/1").isEmpty());
    }
}
