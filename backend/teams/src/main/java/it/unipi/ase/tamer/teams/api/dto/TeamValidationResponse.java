package it.unipi.ase.tamer.teams.api.dto;

import java.util.List;

public record TeamValidationResponse(boolean valid, List<TeamError> errors) {
}
