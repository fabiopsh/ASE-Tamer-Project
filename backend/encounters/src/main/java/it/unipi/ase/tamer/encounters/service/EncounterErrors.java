package it.unipi.ase.tamer.encounters.service;

import it.unipi.ase.tamer.encounters.error.ApiException;

/** Domain error codes of encounters (docs/plan/07-encounters.md, "Errors"). */
public final class EncounterErrors {

    public static final String NO_ACTIVE_RUN = "NO_ACTIVE_RUN";
    public static final String BATTLE_IN_PROGRESS = "BATTLE_IN_PROGRESS";
    public static final String RESET_PENDING = "RESET_PENDING";
    public static final String TEAM_INVALID = "TEAM_INVALID";
    public static final String INVALID_ACTION = "INVALID_ACTION";
    public static final String RUN_CONFLICT = "RUN_CONFLICT";
    public static final String NEW_RUN_NOT_ALLOWED = "NEW_RUN_NOT_ALLOWED";

    private EncounterErrors() {
    }

    public static ApiException noActiveRun(int status) {
        return new ApiException(status, NO_ACTIVE_RUN, "There is no active run.");
    }

    public static ApiException battleInProgress() {
        return new ApiException(409, BATTLE_IN_PROGRESS, "A battle is already in progress.");
    }

    public static ApiException resetPending() {
        return new ApiException(409, RESET_PENDING,
                "The previous run is still being closed; retry in a moment.");
    }

    public static ApiException noBattle() {
        return ApiException.notFound("No battle in progress.");
    }

    public static ApiException invalidAction(String message) {
        return new ApiException(400, INVALID_ACTION, message);
    }
}
