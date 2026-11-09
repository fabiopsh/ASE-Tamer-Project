package it.unipi.ase.tamer.teams.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record TeamError(String code, String message, UUID ownedId) {
}
