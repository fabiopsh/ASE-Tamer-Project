package it.unipi.ase.tamer.auth.client;

/**
 * A called service answered with a 4xx status: a permanent, non-retryable answer (not a dependency failure).
 * Thrown by {@link RemoteErrorMapper}; excluded from {@code @Retry} and from the circuit breaker statistics.
 */
public class RemoteClientError extends RuntimeException {

    private final int status;
    private final String code;

    public RemoteClientError(int status, String code, String message) {
        super(message == null ? "HTTP " + status : message);
        this.status = status;
        this.code = code;
    }

    public int status() {
        return status;
    }

    public String code() {
        return code;
    }
}
