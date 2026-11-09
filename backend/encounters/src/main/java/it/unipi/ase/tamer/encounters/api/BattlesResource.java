package it.unipi.ase.tamer.encounters.api;

import it.unipi.ase.tamer.encounters.api.dto.ActionRequest;
import it.unipi.ase.tamer.encounters.api.dto.BattleStateResponse;
import it.unipi.ase.tamer.encounters.api.dto.StartBattleRequest;
import it.unipi.ase.tamer.encounters.security.CurrentPlayer;
import it.unipi.ase.tamer.encounters.service.BattleService;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
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

/** Battles (US-18, US-19, US-20, US-21, US-22). Only actions are accepted: the server computes every result. */
@Path("/api/battles")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@RolesAllowed("player")
@Tag(name = "battles")
public class BattlesResource {

    @Inject
    CurrentPlayer player;

    @Inject
    BattleService battles;

    @POST
    @Operation(summary = "Start a battle on an available template with a saved team (US-18)")
    @APIResponse(responseCode = "201", description = "Initial battle state",
            content = @Content(schema = @Schema(implementation = BattleStateResponse.class)))
    @APIResponse(responseCode = "400", description = "VALIDATION_ERROR")
    @APIResponse(responseCode = "404", description = "Template not available, or team not found")
    @APIResponse(responseCode = "409", description = "NO_ACTIVE_RUN, BATTLE_IN_PROGRESS, RESET_PENDING, TEAM_INVALID")
    @APIResponse(responseCode = "503", description = "DEPENDENCY_UNAVAILABLE")
    public Response start(@Valid @NotNull StartBattleRequest request) {
        BattleStateResponse state = battles.start(player.id(), request.templateId(), request.teamId());
        return Response.status(201).entity(state).build();
    }

    @GET
    @Path("/current")
    @Operation(summary = "State of the battle in progress")
    @APIResponse(responseCode = "200", description = "Battle state (status in_progress)")
    @APIResponse(responseCode = "404", description = "No battle in progress")
    public BattleStateResponse current() {
        return battles.current(player.id());
    }

    @POST
    @Path("/current/actions")
    @Operation(summary = "Play one round: attack, switch {slot} or flee (US-19, US-21, US-22)",
            description = "Returns the state after the round with last_round events. When the battle ends, status "
                    + "is finished and result holds outcome, reward, XP, level-ups and the run progress (US-20).")
    @APIResponse(responseCode = "200", description = "Battle state after the round")
    @APIResponse(responseCode = "400", description = "VALIDATION_ERROR (unknown field, bad type) or INVALID_ACTION")
    @APIResponse(responseCode = "404", description = "No battle in progress")
    @APIResponse(responseCode = "409", description = "RESET_PENDING")
    public BattleStateResponse act(@Valid @NotNull ActionRequest request) {
        return battles.act(player.id(), request);
    }
}
