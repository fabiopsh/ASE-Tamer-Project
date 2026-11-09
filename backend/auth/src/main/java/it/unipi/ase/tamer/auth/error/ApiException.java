package it.unipi.ase.tamer.auth.error;

import java.util.List;

/**
 * Thrown by the service layer for any expected failure. Mapped to an {@link ErrorResponse}
 * with the given HTTP status by {@link ExceptionMappers}.
 */
public class ApiException extends RuntimeException {

    private final int status;
    private final String code;
    private final List<ErrorDetail> details;

    public ApiException(int status, String code, String message) {
        this(status, code, message, List.of());
    }

    public ApiException(int status, String code, String message, List<ErrorDetail> details) {
        super(message);
        this.status = status;
        this.code = code;
        this.details = List.copyOf(details);
    }

    public static ApiException notFound(String message) {
        return new ApiException(404, ErrorCodes.NOT_FOUND, message);
    }

    public static ApiException dependencyUnavailable(Throwable cause) {
        ApiException e = new ApiException(503, ErrorCodes.DEPENDENCY_UNAVAILABLE,
                "A required service is temporarily unavailable.");
        e.initCause(cause);
        return e;
    }

    public int status() {
        return status;
    }

    public String code() {
        return code;
    }

    public List<ErrorDetail> details() {
        return details;
    }
}
