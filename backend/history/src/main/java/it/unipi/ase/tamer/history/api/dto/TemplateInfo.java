package it.unipi.ase.tamer.history.api.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** The stage template the wild monster came from. */
public record TemplateInfo(
        @NotNull @Min(1) Integer templateId,
        @NotBlank @Size(max = 60) String name,
        @NotNull @Min(1) @Max(1000) Integer stage,
        @NotBlank @Size(max = 20) String element,
        @NotNull Boolean isBoss) {
}
