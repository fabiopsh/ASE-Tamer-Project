package it.unipi.ase.tamer.auth.api;

import it.unipi.ase.tamer.auth.api.dto.TokenResponse;
import it.unipi.ase.tamer.auth.error.OAuthErrorResponse;
import it.unipi.ase.tamer.auth.error.OAuthException;
import it.unipi.ase.tamer.auth.service.TokenService;
import jakarta.annotation.security.PermitAll;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.FormParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/** Client credentials grant for service-to-service calls. Not routed by the gateway. */
@Path("/internal/auth/token")
@Tag(name = "auth-internal")
public class InternalTokenResource {

    @Inject
    TokenService tokens;

    @POST
    @PermitAll
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Obtain a service token",
            description = "grant_type=client_credentials with client_id and client_secret.")
    @APIResponse(responseCode = "200", description = "Service token",
            content = @Content(schema = @Schema(implementation = TokenResponse.class)))
    @APIResponse(responseCode = "400", description = "invalid_request or unsupported_grant_type",
            content = @Content(schema = @Schema(implementation = OAuthErrorResponse.class)))
    @APIResponse(responseCode = "401", description = "invalid_client",
            content = @Content(schema = @Schema(implementation = OAuthErrorResponse.class)))
    public Response token(@FormParam("grant_type") String grantType,
                          @FormParam("client_id") String clientId,
                          @FormParam("client_secret") String clientSecret) {
        if (grantType == null || grantType.isBlank()) {
            throw OAuthException.invalidRequest("grant_type is required.");
        }
        if (!"client_credentials".equals(grantType)) {
            throw OAuthException.unsupportedGrantType();
        }
        return TokenResponses.ok(tokens.clientCredentialsGrant(clientId, clientSecret));
    }
}
