package it.unipi.ase.tamers.history.record;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Anti-tampering chain of the encounter history (US-25), pure logic.
 *
 * <p>Each record stores {@code hash = HMAC-SHA256(secret, prevHash + "|" + sequence + "|" + payload)}
 * in lowercase hex, where {@code prevHash} is the hash of the previous record of the same player
 * (or {@link #GENESIS} for the first one). Changing any payload, sequence or link breaks the chain.
 */
public final class HashChain {
    public static final String GENESIS = "GENESIS";
    private static final String ALGORITHM = "HmacSHA256";

    private HashChain() {}

    /** A stored record as needed for verification. */
    public record Link(long sequence, String prevHash, String hash, String payload) {}

    /**
     * Verification outcome.
     *
     * @param valid true when every record matches its hash and every link matches the previous hash
     * @param records number of records that were checked
     * @param firstInvalidSequence sequence of the first broken record, null when the chain is valid
     */
    public record VerifyResult(boolean valid, int records, Long firstInvalidSequence) {}

    public static String hash(String secret, String prevHash, long sequence, String payload) {
        if (secret == null || secret.isEmpty()) {
            throw new IllegalArgumentException("history secret must not be empty");
        }
        byte[] message = (prevHash + "|" + sequence + "|" + payload).getBytes(StandardCharsets.UTF_8);
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), ALGORITHM));
            return HexFormat.of().formatHex(mac.doFinal(message));
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("HMAC-SHA256 not available", e);
        }
    }

    /**
     * Checks the chain in order. The first record must link to {@link #GENESIS}, each following
     * record must link to the stored hash of the one before it, and each stored hash must match
     * the recomputed HMAC. Stops at the first broken record.
     */
    public static VerifyResult verify(String secret, List<Link> records) {
        String expectedPrev = GENESIS;
        for (Link link : records) {
            if (!isValid(secret, expectedPrev, link)) {
                return new VerifyResult(false, records.size(), link.sequence());
            }
            expectedPrev = link.hash();
        }
        return new VerifyResult(true, records.size(), null);
    }

    private static boolean isValid(String secret, String expectedPrev, Link link) {
        if (link.prevHash() == null || link.hash() == null || link.payload() == null) {
            return false;
        }
        if (!expectedPrev.equals(link.prevHash())) {
            return false;
        }
        String recomputed = hash(secret, link.prevHash(), link.sequence(), link.payload());
        return MessageDigest.isEqual(
                recomputed.getBytes(StandardCharsets.UTF_8), link.hash().getBytes(StandardCharsets.UTF_8));
    }
}
