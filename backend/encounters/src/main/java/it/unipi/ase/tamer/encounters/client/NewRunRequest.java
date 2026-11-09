package it.unipi.ase.tamer.encounters.client;

import java.util.UUID;

/** Body of {@code POST /internal/players/{pid}/new-run}. */
public record NewRunRequest(UUID endedRunId) {
}
