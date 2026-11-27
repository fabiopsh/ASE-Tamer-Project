package it.unipi.ase.tamer.auth.api;

import it.unipi.ase.tamer.auth.error.ErrorResponse;
import it.unipi.ase.tamer.auth.error.OAuthErrorResponse;
import it.unipi.ase.tamer.auth.service.TokenService;
import jakarta.annotation.security.PermitAll;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.FormParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/** Revokes the refresh token family for the presented refresh token (AUTH-7). */
@Path("/api/auth/logout")
@Tag(name = "auth")
public class LogoutResource {

    @Inject
    TokenService tokens;

    @POST
    @PermitAll
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Revoke a refresh token family (AUTH-7)",
            description = "Form-urlencoded refresh_token=... Revokes the entire session family idempotently; works without an active access token.")
    @APIResponse(responseCode = "204", description = "Session family revoked (or token unknown/already revoked)")
    @APIResponse(responseCode = "400", description = "invalid_request when refresh_token is missing or blank",
            content = @Content(schema = @Schema(implementation = OAuthErrorResponse.class)))
    @APIResponse(responseCode = "503", description = "DEPENDENCY_UNAVAILABLE when revocation could not be persisted",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public Response logout(@FormParam("refresh_token") String refreshToken) {
        tokens.logout(refreshToken);
        return Response.noContent()
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .header("Pragma", "no-cache")
                .build();
    }
}
