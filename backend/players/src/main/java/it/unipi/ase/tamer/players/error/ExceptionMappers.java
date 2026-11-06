package it.unipi.ase.tamer.players.error;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;
import io.quarkus.security.AuthenticationFailedException;
import io.quarkus.security.ForbiddenException;
import io.quarkus.security.UnauthorizedException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;
import jakarta.ws.rs.ProcessingException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.Comparator;
import java.util.List;
import org.eclipse.microprofile.faulttolerance.exceptions.FaultToleranceException;
import org.jboss.logging.Logger;
import org.jboss.resteasy.reactive.ClientWebApplicationException;
import org.jboss.resteasy.reactive.server.ServerExceptionMapper;

/**
 * Maps every exception to the common error format. Reference implementation: copied verbatim
 * (apart from the package) into every service.
 */
public class ExceptionMappers {

    private static final Logger LOG = Logger.getLogger(ExceptionMappers.class);

    @ServerExceptionMapper
    public Response apiException(ApiException e) {
        if (e.status() >= 500) {
            LOG.warnf(e.getCause(), "%s: %s", e.code(), e.getMessage());
        }
        return build(e.status(), e.code(), e.getMessage(), e.details());
    }

    @ServerExceptionMapper
    public Response constraintViolation(ConstraintViolationException e) {
        List<ErrorDetail> details = e.getConstraintViolations().stream()
                .map(v -> new ErrorDetail(fieldName(v), constraintName(v)))
                .sorted(Comparator.comparing(ErrorDetail::field).thenComparing(ErrorDetail::code))
                .toList();
        return build(400, ErrorCodes.VALIDATION_ERROR, "Request validation failed.", details);
    }

    @ServerExceptionMapper
    public Response unrecognizedProperty(UnrecognizedPropertyException e) {
        return build(400, ErrorCodes.VALIDATION_ERROR, "Unknown field in request body.",
                List.of(new ErrorDetail(e.getPropertyName(), "UNKNOWN_FIELD")));
    }

    @ServerExceptionMapper
    public Response mismatchedInput(MismatchedInputException e) {
        return build(400, ErrorCodes.VALIDATION_ERROR, "Malformed request body.", List.of());
    }

    @ServerExceptionMapper
    public Response jsonProcessing(JsonProcessingException e) {
        return build(400, ErrorCodes.VALIDATION_ERROR, "Malformed request body.", List.of());
    }

    @ServerExceptionMapper({UnauthorizedException.class, AuthenticationFailedException.class})
    public Response unauthorized(Exception e) {
        return Response.status(401)
                .header(HttpHeaders.WWW_AUTHENTICATE, "Bearer")
                .type(MediaType.APPLICATION_JSON_TYPE)
                .entity(new ErrorResponse(ErrorCodes.UNAUTHORIZED, "Missing, invalid or expired token."))
                .build();
    }

    @ServerExceptionMapper
    public Response forbidden(ForbiddenException e) {
        return build(403, ErrorCodes.FORBIDDEN, "Not allowed.", List.of());
    }

    @ServerExceptionMapper({FaultToleranceException.class, ProcessingException.class,
            ClientWebApplicationException.class})
    public Response dependencyFailure(Exception e) {
        LOG.warnf("Dependency call failed: %s", e.toString());
        return build(503, ErrorCodes.DEPENDENCY_UNAVAILABLE, "A required service is temporarily unavailable.",
                List.of());
    }

    @ServerExceptionMapper
    public Response webApplication(WebApplicationException e) {
        int status = e.getResponse().getStatus();
        if (status >= 500) {
            LOG.error("Unexpected server error", e);
            return build(500, ErrorCodes.INTERNAL_ERROR, "Unexpected error.", List.of());
        }
        return switch (status) {
            case 400 -> build(400, ErrorCodes.VALIDATION_ERROR, "Bad request.", List.of());
            case 401 -> unauthorized(e);
            case 403 -> build(403, ErrorCodes.FORBIDDEN, "Not allowed.", List.of());
            case 404 -> build(404, ErrorCodes.NOT_FOUND, "Resource not found.", List.of());
            case 405 -> build(405, ErrorCodes.METHOD_NOT_ALLOWED, "Method not allowed.", List.of());
            case 406 -> build(406, ErrorCodes.NOT_ACCEPTABLE, "Not acceptable.", List.of());
            case 415 -> build(415, ErrorCodes.UNSUPPORTED_MEDIA_TYPE, "Unsupported media type.", List.of());
            default -> build(status, "HTTP_" + status, "Request failed.", List.of());
        };
    }

    @ServerExceptionMapper
    public Response unexpected(Exception e) {
        LOG.error("Unexpected error", e);
        return build(500, ErrorCodes.INTERNAL_ERROR, "Unexpected error.", List.of());
    }

    private static Response build(int status, String code, String message, List<ErrorDetail> details) {
        return Response.status(status)
                .type(MediaType.APPLICATION_JSON_TYPE)
                .entity(new ErrorResponse(code, message, details))
                .build();
    }

    private static String fieldName(ConstraintViolation<?> v) {
        String name = null;
        for (Path.Node node : v.getPropertyPath()) {
            name = node.getName();
        }
        return name == null ? "request" : toSnakeCase(name);
    }

    private static String constraintName(ConstraintViolation<?> v) {
        return toSnakeCase(v.getConstraintDescriptor().getAnnotation().annotationType().getSimpleName())
                .toUpperCase();
    }

    static String toSnakeCase(String camel) {
        return camel.replaceAll("([a-z0-9])([A-Z])", "$1_$2").toLowerCase();
    }
}
