package it.unipi.ase.tamers.players.common;

import java.util.List;

/** Domain error mapped to the JSON error format of the API contract. */
public class ApiException extends RuntimeException {
    private final int status;
    private final String error;
    private final List<String> details;

    public ApiException(int status, String error, String message, List<String> details) {
        super(message);
        this.status = status;
        this.error = error;
        this.details = details;
    }

    public ApiException(int status, String error, String message) {
        this(status, error, message, null);
    }

    public static ApiException badRequest(String message, List<String> details) {
        return new ApiException(400, "validation_error", message, details);
    }

    public static ApiException badRequest(String message) {
        return badRequest(message, null);
    }

    public static ApiException notFound(String message) {
        return new ApiException(404, "not_found", message);
    }

    public static ApiException forbidden(String message) {
        return new ApiException(403, "forbidden", message);
    }

    public static ApiException conflict(String message) {
        return new ApiException(409, "conflict", message);
    }

    public static ApiException upstream(String message) {
        return new ApiException(503, "upstream_error", message);
    }

    public int status() { return status; }
    public String error() { return error; }
    public List<String> details() { return details; }
}
