package it.unipi.ase.tamer.auth.audit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import it.unipi.ase.tamer.auth.audit.AuditLog.Outcome;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** SEC-3: format of one audit line (unit test, no Quarkus). */
class AuditLogTest {

    private AuditCapture capture;

    @BeforeEach
    void listen() {
        capture = AuditCapture.start();
    }

    @AfterEach
    void stop() {
        capture.close();
    }

    @Test
    void oneCallWritesOneLineWithTheStandardFieldsInOrder() {
        Instant before = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        AuditLog.audit("login", Outcome.DENIED, "ada", Map.of("reason", "invalid_grant"));

        assertEquals(1, capture.messages().size());
        String line = capture.messages().get(0);
        assertTrue(line.startsWith("{\"ts\":\""), line);
        assertTrue(line.indexOf("\"event\"") < line.indexOf("\"user\"")
                && line.indexOf("\"user\"") < line.indexOf("\"outcome\""), line);

        Map<String, String> record = capture.records().get(0);
        assertEquals("login", record.get("event"));
        assertEquals("ada", record.get("user"));
        assertEquals("denied", record.get("outcome"));
        assertEquals("invalid_grant", record.get("reason"));
        assertTrue(record.get("ts").endsWith("Z"), "UTC: " + record.get("ts"));
        assertFalse(Instant.parse(record.get("ts")).isBefore(before));
    }

    @Test
    void theThreeOutcomesAreLowercase() {
        AuditLog.audit("e", Outcome.SUCCESS, "u");
        AuditLog.audit("e", Outcome.DENIED, "u");
        AuditLog.audit("e", Outcome.FAILURE, "u");

        assertEquals(List.of("success", "denied", "failure"),
                capture.records().stream().map(r -> r.get("outcome")).toList());
    }

    @Test
    void aNewlineCannotForgeASecondLine() {
        AuditLog.audit("request", Outcome.SUCCESS, "ada\n{\"user\":\"root\",\"outcome\":\"success\"}");

        assertEquals(1, capture.messages().size());
        String line = capture.messages().get(0);
        assertFalse(line.contains("\n"), line);
        assertFalse(line.contains("\r"), line);
        assertTrue(capture.records().get(0).get("user").startsWith("ada\n{"));
    }

    @Test
    void controlAndNonAsciiCharactersAreEscaped() {
        AuditLog.audit("request", Outcome.SUCCESS, "\u001b[31mred\u0000 café");

        String line = capture.messages().get(0);
        assertTrue(line.chars().allMatch(c -> c >= 0x20 && c < 0x7f), line);
        assertEquals("\u001b[31mred\u0000 café", capture.records().get(0).get("user"));
    }

    @Test
    void everyValueIsCutTo64Characters() {
        AuditLog.audit("x".repeat(100), Outcome.SUCCESS, "y".repeat(100), Map.of("reason", "z".repeat(100)));

        Map<String, String> record = capture.records().get(0);
        assertEquals(AuditLog.MAX_FIELD, record.get("event").length());
        assertEquals(AuditLog.MAX_FIELD, record.get("user").length());
        assertEquals(AuditLog.MAX_FIELD, record.get("reason").length());
    }

    @Test
    void aMissingUserIsADash() {
        AuditLog.audit("request", Outcome.DENIED, null);
        AuditLog.audit("request", Outcome.DENIED, "");

        assertEquals(List.of("-", "-"), capture.records().stream().map(r -> r.get("user")).toList());
    }

    @Test
    void factsCannotReplaceTheStandardFields() {
        AuditLog.audit("login", Outcome.DENIED, "ada",
                Map.of("user", "root", "outcome", "success", "event", "logout", "ts", "1970"));

        Map<String, String> record = capture.records().get(0);
        assertEquals("ada", record.get("user"));
        assertEquals("denied", record.get("outcome"));
        assertEquals("login", record.get("event"));
        assertFalse(record.get("ts").startsWith("1970"));
    }

    @Test
    void outsideARequestThereIsNoRequestId() {
        AuditLog.audit("login", Outcome.SUCCESS, "ada");

        assertFalse(capture.records().get(0).containsKey("request_id"));
    }
}
