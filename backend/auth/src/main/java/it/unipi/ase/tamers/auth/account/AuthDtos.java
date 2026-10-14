package it.unipi.ase.tamers.auth.account;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

public final class AuthDtos {
    private AuthDtos() {}

    public record RegisterRequest(
            @NotNull @Pattern(regexp = "^[A-Za-z0-9_]{3,20}$",
                    message = "must be 3-20 letters, digits or underscores") String username,
            @NotNull @Size(min = 8, max = 72, message = "must be 8-72 characters") String password) {}

    public record LoginRequest(@NotNull @Size(max = 64) String username, @NotNull @Size(max = 72) String password) {}

    public record RegisterResponse(UUID playerId, String username) {}

    public record TokenResponse(String accessToken, String tokenType, long expiresIn, UUID playerId, String username) {}

    public record MeResponse(UUID playerId, String username, Instant createdAt, Instant lastLogin) {}

    public record CreatePlayerRequest(UUID playerId, String username) {}
}
