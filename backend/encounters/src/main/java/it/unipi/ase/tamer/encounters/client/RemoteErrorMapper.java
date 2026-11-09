package it.unipi.ase.tamer.encounters.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.rest.client.ext.ResponseExceptionMapper;

/**
 * Maps 4xx answers of internal services to {@link RemoteClientError} (with the remote {@code error} code and
 * message), so that they are not retried and do not open the circuit. 5xx answers keep the default mapping
 * (a {@code WebApplicationException}), which counts as a dependency failure.
 */
public class RemoteErrorMapper implements ResponseExceptionMapper<RemoteClientError> {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public boolean handles(int status, MultivaluedMap<String, Object> headers) {
        return status >= 400 && status < 500;
    }

    @Override
    public RemoteClientError toThrowable(Response response) {
        String code = null;
        String message = null;
        try {
            String body = response.readEntity(String.class);
            if (body != null && !body.isBlank()) {
                JsonNode json = MAPPER.readTree(body);
                code = json.path("error").isTextual() ? json.get("error").asText() : null;
                message = json.path("message").isTextual() ? json.get("message").asText() : null;
            }
        } catch (Exception ignored) {
            // the status alone is enough
        }
        return new RemoteClientError(response.getStatus(), code, message);
    }
}
