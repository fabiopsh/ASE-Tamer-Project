package it.unipi.ase.tamer.auth.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/** OAuth2 access token response (RFC 6749 §5.1). {@code refresh_token} only from P2. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TokenResponse(String accessToken, String tokenType, long expiresIn, String refreshToken,
                            String scope) {

    public static TokenResponse bearer(String accessToken, long expiresIn, String scope) {
        return new TokenResponse(accessToken, "Bearer", expiresIn, null, scope);
    }
}
