package it.unipi.ase.tamers.teams.team;

import it.unipi.ase.tamers.teams.team.TeamDtos.TeamRequest;
import it.unipi.ase.tamers.teams.team.TeamDtos.TeamView;
import it.unipi.ase.tamers.teams.team.TeamDtos.ValidateRequest;
import it.unipi.ase.tamers.teams.team.TeamDtos.ValidationResult;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.core.Response;
import java.util.List;
import java.util.UUID;
import org.eclipse.microprofile.jwt.JsonWebToken;

@Path("/teams")
public class TeamResource {
    @Inject
    JsonWebToken jwt;
    @Inject
    TeamService service;

    @GET
    @RolesAllowed("player")
    public List<TeamView> list() {
        return service.list(currentPlayer());
    }

    @POST
    @RolesAllowed("player")
    public Response create(@Valid @NotNull TeamRequest request) {
        return Response.status(201).entity(service.create(currentPlayer(), request)).build();
    }

    @GET
    @Path("/{team_id}")
    @RolesAllowed("player")
    public TeamView get(@PathParam("team_id") UUID teamId) {
        return service.get(currentPlayer(), teamId);
    }

    @PUT
    @Path("/{team_id}")
    @RolesAllowed("player")
    public TeamView update(@PathParam("team_id") UUID teamId, @Valid @NotNull TeamRequest request) {
        return service.update(currentPlayer(), teamId, request);
    }

    @DELETE
    @Path("/{team_id}")
    @RolesAllowed("player")
    public Response delete(@PathParam("team_id") UUID teamId) {
        service.delete(currentPlayer(), teamId);
        return Response.noContent().build();
    }

    /** Always 200: invalid teams are reported in the body, not as an HTTP error. */
    @POST
    @Path("/validate")
    @RolesAllowed("player")
    public ValidationResult validate(@Valid @NotNull ValidateRequest request) {
        return service.validate(currentPlayer(), request);
    }

    private UUID currentPlayer() {
        return UUID.fromString(jwt.getSubject());
    }
}
