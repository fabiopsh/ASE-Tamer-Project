package it.unipi.ase.tamers.teams.team;

import it.unipi.ase.tamers.teams.team.TeamDtos.InternalTeam;
import jakarta.annotation.security.PermitAll;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.QueryParam;
import java.util.UUID;

@Path("/internal/teams")
public class InternalTeamResource {
    @Inject
    TeamService service;

    /** 404 both when the team does not exist and when it belongs to another player. */
    @GET
    @Path("/{team_id}")
    @PermitAll
    public InternalTeam get(@PathParam("team_id") UUID teamId, @QueryParam("player_id") UUID playerId) {
        return service.internalTeam(teamId, playerId);
    }
}
