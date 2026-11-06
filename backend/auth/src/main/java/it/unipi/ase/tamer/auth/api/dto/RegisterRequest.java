package it.unipi.ase.tamer.auth.api.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/** US-01: new account. */
public record RegisterRequest(
        @NotNull
        @Size(min = 3, max = 20)
        @Pattern(regexp = "^[a-zA-Z0-9_]+$")
        @Schema(examples = "ash_ketchum", description = "3-20 letters, digits or underscore; unique case-insensitively")
        String username,

        @NotNull
        @Size(min = 8, max = 72)
        @Schema(examples = "pikachu123", description = "8-72 characters (at most 72 bytes in UTF-8), not equal to the username")
        String password) {
}
