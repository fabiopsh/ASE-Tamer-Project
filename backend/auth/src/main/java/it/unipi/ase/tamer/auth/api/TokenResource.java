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

/** Public OAuth2 token endpoint (P-05). P1: {@code password} grant; P2 adds {@code refresh_token}. */
@Path("/api/auth/token")
@Tag(name = "auth")
public class TokenResource {

    @Inject
    TokenService tokens;

    @POST
    @PermitAll
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Obtain an access token (US-02)",
            description = "OAuth2 token endpoint, form-urlencoded. grant_type=password with username and password.")
    @APIResponse(responseCode = "200", description = "Access token",
            content = @Content(schema = @Schema(implementation = TokenResponse.class)))
    @APIResponse(responseCode = "400", description = "invalid_request, invalid_grant or unsupported_grant_type",
            content = @Content(schema = @Schema(implementation = OAuthErrorResponse.class)))
    public Response token(@FormParam("grant_type") String grantType,
                          @FormParam("username") String username,
                          @FormParam("password") String password) {
        if (grantType == null || grantType.isBlank()) {
            throw OAuthException.invalidRequest("grant_type is required.");
        }
        if ("password".equals(grantType)) {
            return TokenResponses.ok(tokens.passwordGrant(username, password));
        }
        throw OAuthException.unsupportedGrantType();
    }
}
