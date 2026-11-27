package it.unipi.ase.tamer.teams.client;

import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.rest.client.ext.ResponseExceptionMapper;

/** Maps 4xx responses from downstream services so retries and circuit breakers ignore client errors. */
public class ClientErrorResponseMapper implements ResponseExceptionMapper<RemoteClientException> {

    @Override
    public boolean handles(int status, MultivaluedMap<String, Object> headers) {
        return status >= 400 && status < 500;
    }

    @Override
    public RemoteClientException toThrowable(Response response) {
        return new RemoteClientException(response.getStatus());
    }
}
