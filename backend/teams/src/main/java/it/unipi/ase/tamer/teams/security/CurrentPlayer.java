package it.unipi.ase.tamer.teams.security;

import it.unipi.ase.tamer.teams.error.ApiException;
import it.unipi.ase.tamer.teams.error.ErrorCodes;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import java.util.UUID;
import org.eclipse.microprofile.jwt.JsonWebToken;

/** The authenticated player: {@code player_id} always comes from the JWT {@code sub} claim. */
@RequestScoped
public class CurrentPlayer {

    @Inject
    JsonWebToken jwt;

    public UUID id() {
        String subject = jwt.getSubject();
        if (subject == null) {
            throw unauthorized();
        }
        try {
            return UUID.fromString(subject);
        } catch (IllegalArgumentException e) {
            throw unauthorized();
        }
    }

    private static ApiException unauthorized() {
        return new ApiException(401, ErrorCodes.UNAUTHORIZED, "Missing, invalid or expired token.");
    }
}
