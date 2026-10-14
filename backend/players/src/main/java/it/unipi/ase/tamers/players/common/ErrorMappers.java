package it.unipi.ase.tamers.players.common;

import jakarta.validation.ConstraintViolationException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.jboss.logging.Logger;
import org.jboss.resteasy.reactive.server.ServerExceptionMapper;

/** Turns every error into {"error", "message", "details"}. */
public class ErrorMappers {
    private static final Logger LOG = Logger.getLogger(ErrorMappers.class);

    private static Response json(int status, ErrorResponse body) {
        return Response.status(status).type(MediaType.APPLICATION_JSON).entity(body).build();
    }

    @ServerExceptionMapper
    public Response api(ApiException e) {
        return json(e.status(), new ErrorResponse(e.error(), e.getMessage(), e.details()));
    }

    @ServerExceptionMapper
    public Response validation(ConstraintViolationException e) {
        var details = e.getConstraintViolations().stream()
                .map(v -> lastNode(v.getPropertyPath().toString()) + ": " + v.getMessage())
                .sorted().toList();
        return json(400, new ErrorResponse("validation_error", "invalid request", details));
    }

    @ServerExceptionMapper
    public Response web(WebApplicationException e) {
        int status = e.getResponse().getStatus();
        String error = switch (status) {
            case 400 -> "validation_error";
            case 401 -> "unauthorized";
            case 403 -> "forbidden";
            case 404 -> "not_found";
            case 409 -> "conflict";
            default -> status >= 500 ? "upstream_error" : "error";
        };
        return json(status, new ErrorResponse(error, e.getMessage(), null));
    }

    @ServerExceptionMapper
    public Response unexpected(RuntimeException e) {
        LOG.error("unexpected error", e);
        return json(500, new ErrorResponse("internal_error", "unexpected server error", null));
    }

    private static String lastNode(String path) {
        int dot = path.lastIndexOf('.');
        return dot < 0 ? path : path.substring(dot + 1);
    }
}
