package it.unipi.ase.tamer.history.api.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** The wild monster that was faced. */
public record WildInfo(
        @NotNull @Min(1) Integer monsterId,
        @NotBlank @Size(max = 60) String name,
        @NotNull @Min(1) @Max(1000) Integer level,
        @NotBlank @Size(max = 20) String rarity,
        @NotBlank @Size(max = 20) String element) {
}
