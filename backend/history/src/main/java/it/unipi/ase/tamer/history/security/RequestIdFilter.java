package it.unipi.ase.tamer.history.security;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import java.util.UUID;
import java.util.regex.Pattern;
import org.jboss.logging.MDC;
import org.jboss.resteasy.reactive.server.ServerRequestFilter;
import org.jboss.resteasy.reactive.server.ServerResponseFilter;

/**
 * Reads (or creates) the {@code X-Request-Id} header, puts it in the log MDC and echoes it in
 * the response. Outgoing REST clients forward it (see ServiceTokenFilter).
 */
public class RequestIdFilter {

    public static final String HEADER = "X-Request-Id";
    public static final String MDC_KEY = "request_id";

    /** Accept only safe values, to avoid log injection. */
    private static final Pattern VALID = Pattern.compile("[A-Za-z0-9-]{1,64}");

    @ServerRequestFilter(preMatching = true)
    public void onRequest(ContainerRequestContext request) {
        String id = request.getHeaderString(HEADER);
        if (id == null || !VALID.matcher(id).matches()) {
            id = UUID.randomUUID().toString();
        }
        request.setProperty(MDC_KEY, id);
        MDC.put(MDC_KEY, id);
    }

    @ServerResponseFilter
    public void onResponse(ContainerRequestContext request, ContainerResponseContext response) {
        Object id = request.getProperty(MDC_KEY);
        if (id != null) {
            response.getHeaders().putSingle(HEADER, id);
        }
    }

    /** Current request id, or {@code null} outside a request. */
    public static String current() {
        Object id = MDC.get(MDC_KEY);
        return id == null ? null : id.toString();
    }
}
