package it.unipi.ase.tamer.encounters.api;

import it.unipi.ase.tamer.encounters.api.dto.EncounterResponse;
import it.unipi.ase.tamer.encounters.security.CurrentPlayer;
import it.unipi.ase.tamer.encounters.service.RunService;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import java.util.List;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/** Stage templates available in the active run (US-17). */
@Path("/api/encounters")
@Produces(MediaType.APPLICATION_JSON)
@RolesAllowed("player")
@Tag(name = "encounters")
public class EncountersResource {

    @Inject
    CurrentPlayer player;

    @Inject
    RunService runs;

    @GET
    @Operation(summary = "Encounters available in the active run",
            description = "Normal templates of the stages reached so far, plus the boss of the current stage once "
                    + "it is unlocked (2 wins in the stage, not yet beaten).")
    @APIResponse(responseCode = "200", description = "Available templates, by stage")
    @APIResponse(responseCode = "409", description = "NO_ACTIVE_RUN")
    public List<EncounterResponse> list() {
        return runs.encounters(player.id());
    }

    @GET
    @Path("/{template_id}")
    @Operation(summary = "Characteristics of an available encounter")
    @APIResponse(responseCode = "200", description = "The template")
    @APIResponse(responseCode = "404", description = "Unknown or not available in the active run")
    @APIResponse(responseCode = "409", description = "NO_ACTIVE_RUN")
    public EncounterResponse get(@PathParam("template_id") @Min(1) @Max(1000) int templateId) {
        return runs.encounter(player.id(), templateId);
    }
}
