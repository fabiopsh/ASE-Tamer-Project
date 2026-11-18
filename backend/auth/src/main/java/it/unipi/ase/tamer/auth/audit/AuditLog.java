package it.unipi.ase.tamer.auth.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.json.JsonWriteFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import it.unipi.ase.tamer.auth.security.RequestIdFilter;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;
import org.jboss.logging.Logger;

/**
 * Audit log (SEC-3, decision P-26): one JSON line per security event on the {@code audit}
 * category: {@code ts} (UTC), {@code event}, {@code user}, {@code outcome}, then the facts.
 *
 * <p>Never pass a password, a token, a secret, a request body or a plain username (P-27).
 * Every value is cut to {@value #MAX_FIELD} characters and serialized by Jackson, which escapes
 * newlines and control characters (one event, one line) and every non-ASCII character.
 */
public final class AuditLog {

    public static final String CATEGORY = "audit";
    public static final int MAX_FIELD = 64;

    private static final Logger LOG = Logger.getLogger(CATEGORY);
    private static final JsonMapper JSON = JsonMapper.builder()
            .enable(JsonWriteFeature.ESCAPE_NON_ASCII)
            .build();
    private static final DateTimeFormatter TS = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    public enum Outcome {
        SUCCESS, DENIED, FAILURE;

        String value() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    private AuditLog() {
    }

    public static void audit(String event, Outcome outcome, String user) {
        audit(event, outcome, user, Map.of());
    }

    /**
     * Writes one line. The facts cannot replace the five standard fields; the request id of
     * the current request (if any) is added as {@code request_id}.
     */
    public static void audit(String event, Outcome outcome, String user, Map<String, String> facts) {
        Map<String, String> record = new LinkedHashMap<>();
        record.put("ts", OffsetDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.SECONDS).format(TS));
        record.put("event", cut(event));
        record.put("user", user == null || user.isEmpty() ? "-" : cut(user));
        record.put("outcome", outcome.value());
        String requestId = RequestIdFilter.current();
        if (requestId != null) {
            record.put("request_id", cut(requestId));
        }
        facts.forEach((key, value) -> record.putIfAbsent(cut(key), cut(value)));
        try {
            LOG.info(JSON.writeValueAsString(record));
        } catch (JsonProcessingException e) {
            // Cannot happen with a map of strings; never fall back to string formatting.
            LOG.error("{\"event\":\"audit_error\",\"outcome\":\"failure\"}");
        }
    }

    private static String cut(String value) {
        if (value == null) {
            return "-";
        }
        return value.length() > MAX_FIELD ? value.substring(0, MAX_FIELD) : value;
    }
}
