package it.unipi.ase.tamer.auth.error;

/** {@code {"error": "...", "error_description": "..."}} (RFC 6749 §5.2). */
public record OAuthErrorResponse(String error, String errorDescription) {
}
