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

## US-03 — Resume saved progress

**Dependencies:** US-01, US-02 · **Service:** players (with monsters/encounters/history) · **Phase:** P1

| # | Criterion | Tests |
|---|---|---|
| 1 | **Given** I have a player profile, **when** I sign in again and request `/api/players/me`, **then** I receive my same `player_id`, `current_run_id`, and starter-choice state. | Bruno `services/players/public/01-get-profile-valid`; `services/players/00-setup/04-get-profile-and-run` |
| 2 | **Given** I am not authenticated, **when** I request my profile, **then** the backend returns 401. | Bruno `services/players/public/02-get-profile-unauthorized` |
| 3 | **Given** I have chosen starters, **when** I consult my collection or return after a restart, **then** the saved instances and run remain available through the owning services. | Bruno `services/monsters/public/13-get-collection-valid`; integration test to be completed after EN-1/HI-1 |

## US-06 — Receive a starter collection

**Dependencies:** US-01, US-10 · **Service:** players, monsters · **Phase:** P1

| # | Criterion | Tests |
|---|---|---|
| 1 | **Given** I registered, **when** I request the starter pool twice, **then** the same 10 distinct common species are offered for the current run. | `PoolGeneratorTest#picksTenDistinctCommonsWithSeededRandom`; Bruno `services/players/public/03-get-pool-valid` |
| 2 | **Given** I have not chosen starters, **when** I choose three distinct offered species, **then** each is added once at level 1 and my run is opened. | Bruno `services/players/public/06-post-choose-valid` (requires EN-1), `services/monsters/internal/05-post-starters-valid` |
| 3 | **Given** I choose fewer than three, duplicates, or an outside species, **when** I submit, **then** I receive `400 INVALID_STARTERS`, with no collection change. | Bruno `services/players/public/05-post-choose-invalid-duplicate` |
| 4 | **Given** I already chose starters, **when** I submit again, **then** I receive `409 STARTERS_ALREADY_CHOSEN`; a repeated internal grant must not add more instances. | Bruno `services/players/public/07-post-choose-invalid-second`, `services/monsters/internal/06-post-starters-idempotent` |

## US-07 — Choose initial monsters (optional)

**Dependencies:** US-06 · **Service:** players · **Phase:** P1 (green story implemented)

| # | Criterion | Tests |
|---|---|---|
| 1 | **Given** a persistent pool of ten common species, **when** I select three different listed species, **then** exactly those three become my starters. | Bruno `services/players/public/06-post-choose-valid` (requires EN-1) |
| 2 | **Given** I have finished a run, **when** its id is sent for a new-run reset, **then** the next run has a new id and a new stable pool; retrying with the old id is a no-op. | Bruno `services/players/internal/03-post-new-run`, `04-post-new-run-retried` |

## US-09 — View my collection

**Dependencies:** US-01, US-06 · **Service:** monsters · **Phase:** P1

| # | Criterion | Tests |
|---|---|---|
| 1 | **Given** I own active monsters, **when** I request `/api/collection`, **then** I receive a paginated list of my own active instances only. | Bruno `services/monsters/public/13-get-collection-valid` (with `run_id` for the archived-run variant) |
| 2 | **Given** a run has been archived, **when** I request `/api/collection?run_id=<my-run-id>`, **then** its old instances remain viewable. | Bruno `services/monsters/internal/13-post-archive-valid`, `services/monsters/public/13-get-collection-valid` |
| 3 | **Given** an instance does not exist or is owned by another player, **when** I request it, **then** I receive 404. | Bruno `services/monsters/public/16-get-instance-missing` |

## US-10 — Browse the global catalogue

**Dependencies:** US-02 · **Service:** monsters · **Phase:** P1

| # | Criterion | Tests |
|---|---|---|
| 1 | **Given** an authenticated player, **when** I filter the catalogue by `element` and `rarity`, **then** I receive a stable, paginated list containing only matching species. | Bruno `services/monsters/public/01-get-catalogue-valid` |
| 2 | **Given** an invalid filter or pagination value, **when** I request the catalogue, **then** I receive `400 VALIDATION_ERROR`. | Bruno `services/monsters/public/02-get-catalogue-invalid-filter` |

## US-11 — View monster details

**Dependencies:** US-10 · **Service:** monsters · **Phase:** P1

| # | Criterion | Tests |
|---|---|---|
| 1 | **Given** an existing species, **when** I request its detail, **then** I see an image path, gameplay stats and its special ability. | Bruno `services/monsters/public/03-get-monster-valid` |
| 2 | **Given** an unknown species id, **when** I request its detail, **then** I receive 404. | Bruno `services/monsters/public/04-get-monster-missing` |
| 3 | **Given** a valid sprite URL, **when** it is loaded without a token, **then** it returns PNG image data; an absent sprite returns 404. | Bruno `services/monsters/public/11-get-sprite-valid`, `12-get-sprite-missing` |

## US-12 — Receive earned monsters

**Dependencies:** US-19, US-09 · **Service:** monsters (called internally by encounters) · **Phase:** P1

| # | Criterion | Tests |
|---|---|---|
| 1 | **Given** I won against a level 7 wild species, **when** encounters grants the reward, **then** one owned instance is saved at level 7 with its `encounter_id`. | Bruno `services/monsters/internal/07-post-reward-valid` |
| 2 | **Given** the same encounter reward is retried, **when** monsters processes it again, **then** it returns the original instance and no duplicate is created. | Bruno `services/monsters/internal/08-post-reward-idempotent` |
| 3 | **Given** the same XP award is retried, **when** monsters processes it again, **then** levels and XP are applied only once. | `LevelingTest#reachesSeveralLevelsInOneGrant`; Bruno `services/monsters/internal/09-post-xp-valid`, `10-post-xp-idempotent` |

## US-14 — Create a team

**Dependencies:** US-09 · **Service:** teams · **Phase:** P1

| # | Criterion | Tests |
|---|---|---|
| 1 | **Given** active monsters owned by me, **when** I create a team with 1–3 distinct instances, **then** it is saved with the order I selected. | Bruno `services/teams/00-setup/07-post-create-test-team`, `services/teams/public/05-post-create-valid` |
| 2 | **Given** four selected instances, **when** I create a team, **then** I receive `400 TEAM_INVALID` with `TEAM_SIZE`, and nothing is saved. | `TeamValidatorTest#sizeAndDuplicateAndOwnershipErrorsAreCollected`; Bruno `services/teams/public/06-post-create-invalid-size` |
| 3 | **Given** five teams already saved for the run, **when** I create a sixth, **then** `TEAM_INVALID` reports `TEAM_LIMIT`. | `TeamValidatorTest#validatesNameAndTeamLimit` |

## US-15 — Modify a team

**Dependencies:** US-14 · **Service:** teams · **Phase:** P1

| # | Criterion | Tests |
|---|---|---|
| 1 | **Given** a team I own, **when** I replace its name or member order, **then** GET reflects the new composition in the same team. | Bruno `services/teams/public/09-put-update-valid`, `07-get-get-valid` |
| 2 | **Given** a missing or foreign team, **when** I attempt to update or delete it, **then** the service returns 404 without revealing ownership. | Bruno `services/teams/public/10-put-update-invalid-foreign`, `12-delete-delete-missing` |

## US-16 — Validate a team

**Dependencies:** US-14 (related; can also validate before saving) · **Service:** teams · **Phase:** P1

| # | Criterion | Tests |
|---|---|---|
| 1 | **Given** one to three distinct active owned monsters from the same run, **when** I validate them, **then** I receive `200 {valid:true,errors:[]}` without saving anything. | `TeamValidatorTest#validOwnedActiveMembersInOrder`; Bruno `services/teams/public/03-post-validate-valid` |
| 2 | **Given** a duplicate, a foreign id, an archived monster, or mixed-run instances, **when** I validate, **then** `valid:false` identifies each applicable rule. | `TeamValidatorTest#sizeAndDuplicateAndOwnershipErrorsAreCollected`, `#rejectsArchivedAndMixedRunMembers`; Bruno `services/teams/public/04-post-validate-invalid-duplicate` |
| 3 | **Given** a previously saved team becomes invalid after a run change, **when** encounters requests it internally, **then** teams rejects it with `409 TEAM_INVALID`. | Bruno `services/teams/internal/01-get-team-revalidated` (valid counterpart); negative integration test when EN-1 is available |
