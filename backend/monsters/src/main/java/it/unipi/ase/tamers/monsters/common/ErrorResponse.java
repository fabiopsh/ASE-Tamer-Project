package it.unipi.ase.tamers.monsters.common;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(String error, String message, List<String> details) {}
