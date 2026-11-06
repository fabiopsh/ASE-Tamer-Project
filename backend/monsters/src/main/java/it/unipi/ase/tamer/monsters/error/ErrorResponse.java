package it.unipi.ase.tamer.monsters.error;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/** Body of every error response: {@code {error, message, details}}. */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ErrorResponse(String error, String message, List<ErrorDetail> details) {

    public ErrorResponse(String error, String message) {
        this(error, message, List.of());
    }
}
