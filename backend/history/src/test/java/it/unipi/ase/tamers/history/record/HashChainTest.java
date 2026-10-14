package it.unipi.ase.tamers.history.record;

import static it.unipi.ase.tamers.history.record.HashChain.GENESIS;
import static org.junit.jupiter.api.Assertions.*;

import it.unipi.ase.tamers.history.record.HashChain.Link;
import it.unipi.ase.tamers.history.record.HashChain.VerifyResult;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class HashChainTest {
    private static final String SECRET = "test-secret";
    private static final Pattern LOWER_HEX_64 = Pattern.compile("^[0-9a-f]{64}$");

    /** Builds a correct chain over the given payloads, the way the service appends them. */
    private static List<Link> chain(String... payloads) {
        List<Link> links = new ArrayList<>();
        String prev = GENESIS;
        for (int i = 0; i < payloads.length; i++) {
            long sequence = i + 1;
            String hash = HashChain.hash(SECRET, prev, sequence, payloads[i]);
            links.add(new Link(sequence, prev, hash, payloads[i]));
            prev = hash;
        }
        return links;
    }

    private static List<Link> replace(List<Link> links, int index, Link replacement) {
        List<Link> copy = new ArrayList<>(links);
        copy.set(index, replacement);
        return copy;
    }

    // ---- hash ----

    @Test
    void hashMatchesKnownHmacSha256Vector() {
        // Computed independently with: printf 'GENESIS|1|x' | openssl dgst -sha256 -hmac 'dev-history-secret'
        assertEquals(
                "9ba91f10cdba1ac75193c3e73d3c7b4dd48cfc8cd810cc36b607eb00914d667a",
                HashChain.hash("dev-history-secret", GENESIS, 1, "x"));
    }

    @Test
    void hashIsLowercaseHexOf64Chars() {
        String hash = HashChain.hash(SECRET, GENESIS, 1, "{\"run_id\":\"abc\"}");
        assertTrue(LOWER_HEX_64.matcher(hash).matches(), hash);
    }

    @Test
    void hashIsDeterministic() {
        assertEquals(HashChain.hash(SECRET, GENESIS, 7, "p"), HashChain.hash(SECRET, GENESIS, 7, "p"));
    }

    @Test
    void hashChangesWithEveryInput() {
        String base = HashChain.hash(SECRET, GENESIS, 1, "payload");
        assertNotEquals(base, HashChain.hash("other-secret", GENESIS, 1, "payload"));
        assertNotEquals(base, HashChain.hash(SECRET, "0".repeat(64), 1, "payload"));
        assertNotEquals(base, HashChain.hash(SECRET, GENESIS, 2, "payload"));
        assertNotEquals(base, HashChain.hash(SECRET, GENESIS, 1, "payload "));
    }

    @Test
    void hashRejectsEmptySecret() {
        assertThrows(IllegalArgumentException.class, () -> HashChain.hash("", GENESIS, 1, "p"));
        assertThrows(IllegalArgumentException.class, () -> HashChain.hash(null, GENESIS, 1, "p"));
    }

    // ---- verify ----

    @Test
    void validChainVerifies() {
        VerifyResult result = HashChain.verify(SECRET, chain("a", "b", "c"));
        assertEquals(new VerifyResult(true, 3, null), result);
    }

    @Test
    void singleRecordChainVerifies() {
        assertEquals(new VerifyResult(true, 1, null), HashChain.verify(SECRET, chain("only")));
    }

    @Test
    void emptyChainIsValid() {
        assertEquals(new VerifyResult(true, 0, null), HashChain.verify(SECRET, List.of()));
    }

    @Test
    void wrongSecretFailsOnFirstRecord() {
        assertEquals(new VerifyResult(false, 3, 1L), HashChain.verify("wrong-secret", chain("a", "b", "c")));
    }

    @Test
    void tamperedPayloadIsDetectedAtThatRecord() {
        List<Link> links = chain("a", "b", "c");
        Link second = links.get(1);
        List<Link> tampered = replace(links, 1,
                new Link(second.sequence(), second.prevHash(), second.hash(), "{\"outcome\":\"victory\"}"));
        assertEquals(new VerifyResult(false, 3, 2L), HashChain.verify(SECRET, tampered));
    }

    @Test
    void tamperedPayloadOfFirstRecordIsDetected() {
        List<Link> links = chain("a", "b");
        Link first = links.get(0);
        List<Link> tampered = replace(links, 0,
                new Link(first.sequence(), first.prevHash(), first.hash(), "changed"));
        assertEquals(new VerifyResult(false, 2, 1L), HashChain.verify(SECRET, tampered));
    }

    @Test
    void tamperedHashIsDetectedAtThatRecord() {
        List<Link> links = chain("a", "b", "c");
        Link second = links.get(1);
        String forged = "f".repeat(64);
        List<Link> tampered = replace(links, 1,
                new Link(second.sequence(), second.prevHash(), forged, second.payload()));
        // record 3 also breaks (its prev no longer matches), but the first invalid one is 2
        assertEquals(new VerifyResult(false, 3, 2L), HashChain.verify(SECRET, tampered));
    }

    @Test
    void recomputedHashWithoutPrevLinkIsStillDetected() {
        // Attacker rewrites record 2 and its hash consistently, but record 3 keeps the old prev_hash.
        List<Link> links = chain("a", "b", "c");
        String newHash = HashChain.hash(SECRET, links.get(0).hash(), 2, "forged");
        List<Link> tampered = List.of(links.get(0),
                new Link(2, links.get(0).hash(), newHash, "forged"),
                links.get(2));
        assertEquals(new VerifyResult(false, 3, 3L), HashChain.verify(SECRET, tampered));
    }

    @Test
    void brokenLinkIsDetected() {
        List<Link> links = chain("a", "b", "c");
        Link third = links.get(2);
        List<Link> broken = replace(links, 2,
                new Link(third.sequence(), "1".repeat(64), third.hash(), third.payload()));
        assertEquals(new VerifyResult(false, 3, 3L), HashChain.verify(SECRET, broken));
    }

    @Test
    void firstRecordNotLinkedToGenesisIsDetected() {
        List<Link> links = chain("a");
        Link first = links.get(0);
        List<Link> broken = List.of(new Link(first.sequence(), "not-genesis", first.hash(), first.payload()));
        assertEquals(new VerifyResult(false, 1, 1L), HashChain.verify(SECRET, broken));
    }

    @Test
    void deletedMiddleRecordIsDetected() {
        List<Link> links = chain("a", "b", "c");
        List<Link> withoutSecond = List.of(links.get(0), links.get(2));
        assertEquals(new VerifyResult(false, 2, 3L), HashChain.verify(SECRET, withoutSecond));
    }

    @Test
    void alteredSequenceIsDetected() {
        List<Link> links = chain("a", "b");
        Link second = links.get(1);
        List<Link> tampered = replace(links, 1,
                new Link(5, second.prevHash(), second.hash(), second.payload()));
        assertEquals(new VerifyResult(false, 2, 5L), HashChain.verify(SECRET, tampered));
    }

    @Test
    void recordWithMissingFieldsIsInvalid() {
        List<Link> broken = List.of(new Link(1, GENESIS, null, "a"));
        assertEquals(new VerifyResult(false, 1, 1L), HashChain.verify(SECRET, broken));
        List<Link> noPayload = List.of(new Link(1, GENESIS, HashChain.hash(SECRET, GENESIS, 1, "a"), null));
        assertEquals(new VerifyResult(false, 1, 1L), HashChain.verify(SECRET, noPayload));
    }
}
