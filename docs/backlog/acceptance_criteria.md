# Acceptance criteria

One section per user story in scope (red stories, US-05 and US-25, and the implemented green stories). Each criterion is written in **Given / When / Then** and lists the tests that verify it. Plan: [docs/plan/12-docs-delivery.md](../plan/12-docs-delivery.md) (DOC-1).

Test references:

- `JUnit Class#method`: `@QuarkusTest` or unit test in `backend/<service>/src/test/java`.
- `Bruno <path>`: request in `docs/tests/bruno/`.

---

## US-01 — Create an account

**Dependencies:** none · **Service:** auth (calls players) · **Phase:** P1

| # | Criterion | Tests |
|---|---|---|
| 1 | **Given** no account named `ash` exists, **when** I register `ash` with a valid password, **then** I receive 201 with my `player_id` and a player profile exists. | `RegistrationResourceTest#validRegistrationCreatesAccountAndProfile`; Bruno `services/auth/public/01-register-valid` |
| 2 | **Given** an account `ash` exists, **when** I register `ash` or `ASH`, **then** I receive 409 `USERNAME_TAKEN` and no account is created. | `RegistrationResourceTest#duplicateUsernameIsRejected`, `#duplicateUsernameIsCaseInsensitive`; Bruno `services/auth/public/02-register-invalid-duplicate` |
| 3 | **Given** any state, **when** I register with a password shorter than 8 characters, longer than 72 characters or 72 bytes, or equal to the username, **then** I receive 400 `VALIDATION_ERROR` and no account is created. | `RegistrationResourceTest#shortPasswordIsRejected`, `#passwordLongerThan72CharactersIsRejected`, `#passwordLongerThan72BytesIsRejected`, `#passwordEqualToUsernameIsRejected`; Bruno `services/auth/public/03-register-invalid-short-password` |
| 4 | **Given** any state, **when** I register a username that is shorter than 3 or longer than 20 characters, or contains characters other than letters, digits and `_`, **then** I receive 400 `VALIDATION_ERROR`. | `RegistrationResourceTest#invalidUsernameIsRejected` |
| 5 | **Given** any state, **when** the request body contains unknown fields or is not valid JSON, **then** I receive 400 `VALIDATION_ERROR`. | `RegistrationResourceTest#unknownFieldIsRejected`, `#malformedJsonIsRejected` |
| 6 | **Given** the players service is unavailable, **when** I register, **then** I receive 503 `DEPENDENCY_UNAVAILABLE` and no account is created. | `RegistrationResourceTest#playersUnavailableRollsBackTheAccount` |
| 7 | **Given** I registered, **then** my password is stored only as a bcrypt hash. | `RegistrationResourceTest#validRegistrationCreatesAccountAndProfile`, `PasswordHasherTest#hashIsBcryptAndNotThePassword` |

## US-02 — Log in

**Dependencies:** US-01 · **Service:** auth · **Phase:** P1

| # | Criterion | Tests |
|---|---|---|
| 1 | **Given** a registered account, **when** I request a token with `grant_type=password` and the correct credentials (username in any case), **then** I receive 200 with a Bearer access token, `expires_in` and `Cache-Control: no-store`. | `TokenResourceTest#correctCredentialsReturnAPlayerToken`; Bruno `services/auth/public/04-token-valid` |
| 2 | **Given** a registered account, **when** I use a wrong password or an unknown username, **then** I receive the same 400 `invalid_grant` error. | `TokenResourceTest#wrongPasswordAndUnknownUserGiveTheSameError`; Bruno `services/auth/public/05-token-invalid-wrong-password`, `06-token-invalid-unknown-user` |
| 3 | **Given** any state, **when** the request misses `grant_type`, `username` or `password`, or uses an unsupported grant type, **then** I receive 400 `invalid_request` or `unsupported_grant_type`. | `TokenResourceTest#missingGrantTypeIsInvalidRequest`, `#missingPasswordIsInvalidRequest`, `#unsupportedGrantTypeIsRejected`; Bruno `services/auth/public/07-token-invalid-unsupported-grant` |
| 4 | **Given** a token I received, **when** I call a protected endpoint of any service, **then** the token is accepted and identifies me (`sub` = my `player_id`). | `TokenVerificationTest#playerTokenIsAccepted`, `CommonsTest#playerIdComesFromSubject` (players) |
| 5 | **Given** no token, an expired token, a tampered or unsigned token, or a token from another issuer or for another audience, **when** I call a protected endpoint, **then** I receive 401 `UNAUTHORIZED`. | `TokenVerificationTest#missingTokenIs401`, `#tamperedTokenIs401`, `#unsignedTokenIs401`, `#expiredTokenIs401`, `#wrongIssuerIs401`, `#wrongAudienceIs401`; `CommonsTest#noTokenIs401WithErrorBody` |
| 6 | **Given** a player token, **when** I call an internal endpoint, **then** I receive 403 `FORBIDDEN` (and through the gateway, 404). | `TokenVerificationTest#playerTokenOnInternalEndpointIs403`, `CommonsTest#playerOnInternalIs403` |
