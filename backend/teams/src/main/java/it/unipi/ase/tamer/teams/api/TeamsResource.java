package it.unipi.ase.tamer.teams.api;

import it.unipi.ase.tamer.teams.api.dto.TeamRequest;
import it.unipi.ase.tamer.teams.api.dto.ValidateTeamRequest;
import it.unipi.ase.tamer.teams.security.CurrentPlayer;
import it.unipi.ase.tamer.teams.service.TeamsService;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.UUID;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;

@Path("/api/teams")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@RolesAllowed("player")
@Tag(name = "teams")
public class TeamsResource {

    @Inject
    TeamsService service;

    @Inject
    CurrentPlayer currentPlayer;

    @GET
    @Operation(summary = "List saved teams for the current run (US-14)")
    @APIResponse(responseCode = "200", description = "Teams of the current run")
    @APIResponse(responseCode = "401", description = "UNAUTHORIZED: missing or invalid token")
    public Object teams() {
        return service.teams(currentPlayer.id());
    }

    @POST
    @Operation(summary = "Create and validate a team (US-14)")
    @APIResponse(responseCode = "201", description = "Team created")
    @APIResponse(responseCode = "400", description = "TEAM_INVALID (details: TEAM_SIZE, DUPLICATE_MEMBER, NOT_OWNED, TEAM_LIMIT, ...)")
    @APIResponse(responseCode = "401", description = "UNAUTHORIZED: missing or invalid token")
    @APIResponse(responseCode = "503", description = "DEPENDENCY_UNAVAILABLE")
    public Response create(@Valid @NotNull TeamRequest request) {
        return Response.status(Response.Status.CREATED).entity(service.create(currentPlayer.id(), request)).build();
    }

    @POST
    @Path("/validate")
    @Operation(summary = "Validate a team without saving it (US-16)")
    @APIResponse(responseCode = "200", description = "{valid, errors}: invalid teams are reported here, not as errors")
    @APIResponse(responseCode = "400", description = "VALIDATION_ERROR")
    @APIResponse(responseCode = "401", description = "UNAUTHORIZED: missing or invalid token")
    @APIResponse(responseCode = "503", description = "DEPENDENCY_UNAVAILABLE")
    public Object validate(@Valid @NotNull ValidateTeamRequest request) {
        return service.validate(currentPlayer.id(), request.members());
    }

    @GET
    @Path("/{id}")
    @Operation(summary = "View a saved team")
    @APIResponse(responseCode = "200", description = "Team with ordered members")
    @APIResponse(responseCode = "401", description = "UNAUTHORIZED: missing or invalid token")
    @APIResponse(responseCode = "404", description = "NOT_FOUND: missing or owned by another player")
    public Object team(@PathParam("id") UUID id) {
        return service.team(currentPlayer.id(), id);
    }

    @PUT
    @Path("/{id}")
    @Operation(summary = "Replace a team's name, members and order (US-15)")
    @APIResponse(responseCode = "200", description = "Team updated")
    @APIResponse(responseCode = "400", description = "TEAM_INVALID")
    @APIResponse(responseCode = "401", description = "UNAUTHORIZED: missing or invalid token")
    @APIResponse(responseCode = "404", description = "NOT_FOUND: missing or owned by another player")
    @APIResponse(responseCode = "409", description = "TEAM_INVALID: MIXED_RUNS")
    @APIResponse(responseCode = "503", description = "DEPENDENCY_UNAVAILABLE")
    public Object update(@PathParam("id") UUID id, @Valid @NotNull TeamRequest request) {
        return service.update(currentPlayer.id(), id, request);
    }

    @DELETE
    @Path("/{id}")
    @Operation(summary = "Delete a team")
    @APIResponse(responseCode = "204", description = "Team deleted")
    @APIResponse(responseCode = "401", description = "UNAUTHORIZED: missing or invalid token")
    @APIResponse(responseCode = "404", description = "NOT_FOUND: missing or owned by another player")
    public Response delete(@PathParam("id") UUID id) {
        service.delete(currentPlayer.id(), id);
        return Response.noContent().build();
    }
}
