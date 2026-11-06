package it.unipi.ase.tamer.players.error;

/** One entry of the optional {@code details} array of an error response. */
public record ErrorDetail(String field, String code) {
}
