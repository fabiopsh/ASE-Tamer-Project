package it.unipi.ase.tamer.players.client;

/** Non-retryable 4xx response from an internal service; excluded from retries and circuit breakers. */
public class RemoteClientException extends RuntimeException {

    private final int status;

    public RemoteClientException(int status) {
        super("Downstream service returned HTTP " + status);
        this.status = status;
    }

    public int status() {
        return status;
    }
}
