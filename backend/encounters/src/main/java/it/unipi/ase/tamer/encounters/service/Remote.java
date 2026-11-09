package it.unipi.ase.tamer.encounters.service;

import it.unipi.ase.tamer.encounters.client.RemoteClientError;
import it.unipi.ase.tamer.encounters.error.ApiException;
import java.util.function.Supplier;

/** Helpers for calls to other services. */
final class Remote {

    private Remote() {
    }

    /** The answer was a 4xx: repeating the same call would give the same answer. */
    static boolean permanent(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof RemoteClientError) {
                return true;
            }
        }
        return false;
    }

    static RemoteClientError clientError(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof RemoteClientError r) {
                return r;
            }
        }
        return null;
    }

    /** Runs a call; any failure becomes {@code 503 DEPENDENCY_UNAVAILABLE}. */
    static <T> T call(Supplier<T> call) {
        try {
            return call.get();
        } catch (ApiException e) {
            throw e;
        } catch (RuntimeException e) {
            throw ApiException.dependencyUnavailable(e);
        }
    }
}
