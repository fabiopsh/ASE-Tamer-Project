package it.unipi.ase.tamer.encounters.engine;

/**
 * An action that the rules do not allow in the current state (switch to a KO'd or active member, slot out of
 * range, any action after the end). The battle state is unchanged. The service maps it to
 * {@code 400 INVALID_ACTION}.
 */
public class InvalidActionException extends RuntimeException {

    public InvalidActionException(String message) {
        super(message);
    }
}
