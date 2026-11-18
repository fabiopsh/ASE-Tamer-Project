package it.unipi.ase.tamer.auth.audit;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

/**
 * The Java counterpart of pytest {@code caplog}: an in-memory handler attached to the
 * {@code audit} logger. {@link #records()} to check what is in the lines, {@link #text()} to
 * check what must never be there.
 */
public final class AuditCapture extends Handler implements AutoCloseable {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final Logger logger = Logger.getLogger(AuditLog.CATEGORY);
    private final List<String> messages = new CopyOnWriteArrayList<>();

    private AuditCapture() {
        setLevel(Level.ALL);
    }

    /** Starts listening; close it (try-with-resources or {@code @AfterEach}). */
    public static AuditCapture start() {
        AuditCapture capture = new AuditCapture();
        capture.logger.addHandler(capture);
        return capture;
    }

    @Override
    public void publish(LogRecord record) {
        messages.add(record.getMessage());
    }

    @Override
    public void flush() {
    }

    @Override
    public void close() {
        logger.removeHandler(this);
    }

    public void clear() {
        messages.clear();
    }

    /** The raw messages, one per log record. */
    public List<String> messages() {
        return List.copyOf(messages);
    }

    /** Every message parsed as JSON (fails the test if a line is not valid JSON). */
    public List<Map<String, String>> records() {
        return messages.stream().map(AuditCapture::parse).toList();
    }

    /** The records of one event. */
    public List<Map<String, String>> records(String event) {
        return records().stream().filter(r -> event.equals(r.get("event"))).toList();
    }

    /** Everything captured, as one string. */
    public String text() {
        return String.join("\n", messages);
    }

    private static Map<String, String> parse(String line) {
        try {
            return JSON.readValue(line, new TypeReference<Map<String, String>>() { });
        } catch (Exception e) {
            throw new AssertionError("Audit line is not valid JSON: " + line, e);
        }
    }
}
