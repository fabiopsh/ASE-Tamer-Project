package it.unipi.ase.tamer.encounters.api.dto;

/** Answer of {@code POST /api/runs} ({@code 202}): {@code {"status": "choose_starters"}}. */
public record NewRunStatusResponse(String status) {

    public static final String CHOOSE_STARTERS = "choose_starters";
}
