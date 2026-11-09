package it.unipi.ase.tamer.encounters.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.UUID;

/** Answer of {@code POST /internal/players/{pid}/new-run}. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record NewRunResponse(UUID currentRunId) {
}
