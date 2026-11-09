package it.unipi.ase.tamer.auth.error;

/** Errors of the OAuth2 token endpoints, rendered as RFC 6749 §5.2 responses. */
public class OAuthException extends RuntimeException {

    private final int status;
    private final String error;

    public OAuthException(int status, String error, String description) {
        super(description);
        this.status = status;
        this.error = error;
    }

    public static OAuthException invalidRequest(String description) {
        return new OAuthException(400, "invalid_request", description);
    }

    public static OAuthException invalidGrant() {
        return new OAuthException(400, "invalid_grant", "Invalid username or password.");
    }

    public static OAuthException unsupportedGrantType() {
        return new OAuthException(400, "unsupported_grant_type", "Unsupported grant_type.");
    }

    public static OAuthException invalidClient() {
        return new OAuthException(401, "invalid_client", "Client authentication failed.");
    }

    public int status() {
        return status;
    }

    public String error() {
        return error;
    }
}
