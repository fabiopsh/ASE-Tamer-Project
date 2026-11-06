package it.unipi.ase.tamer.auth.api.dto;

import java.util.UUID;

/** Created account; {@code username} is the normalised (lowercase) form. */
public record RegisterResponse(UUID playerId, String username) {
}
