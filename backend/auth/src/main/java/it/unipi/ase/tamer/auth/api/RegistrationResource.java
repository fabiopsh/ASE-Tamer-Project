package it.unipi.ase.tamer.auth.api;

import it.unipi.ase.tamer.auth.api.dto.RegisterRequest;
import it.unipi.ase.tamer.auth.api.dto.RegisterResponse;
import it.unipi.ase.tamer.auth.error.ErrorResponse;
import it.unipi.ase.tamer.auth.service.RegistrationService;
import jakarta.annotation.security.PermitAll;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.Consumes;
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

@Path("/api/auth/register")
@Tag(name = "auth")
public class RegistrationResource {

    @Inject
    RegistrationService registration;

    @POST
    @PermitAll
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Create an account (US-01)",
            description = "Creates the account and the player profile. Usernames are unique case-insensitively.")
    @APIResponse(responseCode = "201", description = "Account created",
            content = @Content(schema = @Schema(implementation = RegisterResponse.class)))
    @APIResponse(responseCode = "400", description = "VALIDATION_ERROR",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @APIResponse(responseCode = "409", description = "USERNAME_TAKEN",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @APIResponse(responseCode = "503", description = "DEPENDENCY_UNAVAILABLE: players is unavailable, no account created",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public Response register(@Valid @NotNull RegisterRequest request) {
        return Response.status(Response.Status.CREATED).entity(registration.register(request)).build();
    }
}
