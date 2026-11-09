package it.unipi.ase.tamer.encounters.api.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * Body of {@code POST /api/battles/current/actions}: only an action, never a result. {@code slot} (team position
 * 1–3) is required for {@code switch} and forbidden otherwise ({@code 400 INVALID_ACTION}). Any other field
 * (outcome, HP, damage...) is rejected with {@code 400 VALIDATION_ERROR}.
 */
public record ActionRequest(@NotNull @Pattern(regexp = "attack|switch|flee") String type,
                            @Min(1) @Max(3) Integer slot) {
}
