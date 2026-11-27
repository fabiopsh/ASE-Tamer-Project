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
| 3 | **Given** any state, **when** I register with a password shorter than 8 characters, longer than 72 characters or 72 bytes, equal to the username, or in the common-password denylist, **then** I receive 400 `VALIDATION_ERROR` and no account is created. | `RegistrationResourceTest#shortPasswordIsRejected`, `#passwordLongerThan72CharactersIsRejected`, `#passwordLongerThan72BytesIsRejected`, `#passwordEqualToUsernameIsRejected`, `#commonWeakPasswordIsRejected`; Bruno `services/auth/public/03-register-invalid-short-password` |
| 4 | **Given** any state, **when** I register a username that is shorter than 3 or longer than 20 characters, or contains characters other than letters, digits and `_`, **then** I receive 400 `VALIDATION_ERROR`. | `RegistrationResourceTest#invalidUsernameIsRejected` |
| 5 | **Given** any state, **when** the request body contains unknown fields or is not valid JSON, **then** I receive 400 `VALIDATION_ERROR`. | `RegistrationResourceTest#unknownFieldIsRejected`, `#malformedJsonIsRejected` |
| 6 | **Given** the players service is unavailable, **when** I register, **then** I receive 503 `DEPENDENCY_UNAVAILABLE` and no account is created. | `RegistrationResourceTest#playersUnavailableRollsBackTheAccount` |
| 7 | **Given** I registered, **then** my password is stored only as a bcrypt hash and my username is encrypted at rest (AES-256-GCM) with an HMAC blind index. | `RegistrationResourceTest#validRegistrationCreatesAccountAndProfile`, `PasswordHasherTest#hashIsBcryptAndNotThePassword`, `UsernameCryptoTest#encryptAndDecryptRoundtrip` |

## US-02 — Log in

**Dependencies:** US-01 · **Service:** auth · **Phase:** P1, P2 (AUTH-7: 300 s access tokens, rotating refresh tokens, server logout)

| # | Criterion | Tests |
|---|---|---|
| 1 | **Given** a registered account, **when** I request a token with `grant_type=password` and the correct credentials (username in any case), **then** I receive 200 with a Bearer access token (`expires_in` = 300 s), a rotating `refresh_token` and `Cache-Control: no-store`. | `TokenResourceTest#correctCredentialsReturnAPlayerToken`, `RefreshAndLogoutTest#passwordLoginReturns300sAccessTokenAndStoresOnlyRefreshHash`; Bruno `services/auth/public/04-token-valid` |
| 2 | **Given** a registered account, **when** I use a wrong password or an unknown username, **then** I receive the same 400 `invalid_grant` error. | `TokenResourceTest#wrongPasswordAndUnknownUserGiveTheSameError`; Bruno `services/auth/public/05-token-invalid-wrong-password`, `06-token-invalid-unknown-user` |
| 3 | **Given** any state, **when** the request misses `grant_type`, `username` or `password`, or uses an unsupported grant type, **then** I receive 400 `invalid_request` or `unsupported_grant_type`. | `TokenResourceTest#missingGrantTypeIsInvalidRequest`, `#missingPasswordIsInvalidRequest`, `#unsupportedGrantTypeIsRejected`; Bruno `services/auth/public/07-token-invalid-unsupported-grant` |
| 4 | **Given** a token I received, **when** I call a protected endpoint of any service, **then** the gateway (`auth_request`) and the service accept the token and identify me (`sub` = my `player_id`). | `TokenVerificationTest#playerTokenIsAccepted`, `AuthorizeResourceTest#validPlayerTokenOnPlayerRouteReturns204WithSubject`, `CommonsTest#playerIdComesFromSubject` (players); Bruno `access/09-players-valid-200` |
| 5 | **Given** no token, an expired token, a tampered or unsigned token, or a token from another issuer or for another audience, **when** I call a protected endpoint, **then** I receive 401 `UNAUTHORIZED`. | `TokenVerificationTest#missingTokenIs401`, `#tamperedTokenIs401`, `#unsignedTokenIs401`, `#expiredTokenIs401`, `#wrongIssuerIs401`, `#wrongAudienceIs401`, `AuthorizeResourceTest#forgedExpiredUnsignedAndMissingSubTokensAre401`, `CommonsTest#noTokenIs401WithErrorBody`; Bruno `access/04`…`08` |
| 6 | **Given** a player token, **when** I call an internal endpoint, **then** I receive 403 `FORBIDDEN` (and through the gateway, 404). | `TokenVerificationTest#playerTokenOnInternalEndpointIs403`, `AuthorizeResourceTest#internalManagementTraversalAndUnmappedRoutesAre403`, `CommonsTest#playerOnInternalIs403`, `AccessPolicyTest` (all 6 services) |
| 7 | **Given** a valid refresh token, **when** I exchange it with `grant_type=refresh_token`, **then** I receive a rotated access/refresh pair and the old token is revoked; reusing a consumed token revokes the entire session family; `POST /api/auth/logout` revokes the family idempotently. | `RefreshAndLogoutTest#rotationIssuesNewPairRevokesOldAndPreservesFamilyAndExpiry`, `#reuseOfConsumedTokenRevokesEntireFamilyAndCommitsDespite400`, `#logoutRevokesWholeFamilyIdempotentlyAndValidatesMissingParam`; Bruno `services/auth/public/08-token-refresh-valid`, `09-logout-valid`, `10-logout-invalid-missing`, `access/10`…`17` |

## US-03 — Resume saved progress

**Dependencies:** US-01, US-02 · **Service:** players (with monsters/encounters/history) · **Phase:** P1

| # | Criterion | Tests |
|---|---|---|
| 1 | **Given** I have a player profile, **when** I sign in again and request `/api/players/me`, **then** I receive my same `player_id`, `current_run_id`, and starter-choice state. | Bruno `services/players/public/01-get-profile-valid`; `services/players/00-setup/03-get-profile-and-run` |
| 2 | **Given** I am not authenticated, **when** I request my profile, **then** the backend returns 401. | Bruno `services/players/public/02-get-profile-unauthorized` |
| 3 | **Given** I have chosen starters, **when** I consult my collection or return after a restart, **then** the saved instances and run remain available through the owning services. | Bruno `services/monsters/public/13-get-collection-valid`; e2e `e2e/06-collection`, `e2e/14-collection-after-battle` |
| 4 | **Given** my run was lost or abandoned (roguelike rule R14), **when** I come back, **then** I start a new run with a new starter pool, and I can still consult my past runs, their archived monsters and their history. | `BattleApiTest#abandonClosesTheBattleAndResetsTheRun`; Bruno `services/encounters/public/19-post-abandon-valid`, `services/encounters/public/02-get-runs-list-valid`, `services/monsters/internal/19-get-collection-archived-valid` |

## US-04 — Choose or update my player name and avatar

**Dependencies:** US-01, US-02 · **Service:** players · **Phase:** P2 (optional green story)

| # | Criterion | Tests |
|---|---|---|
| 1 | **Given** I am authenticated, **when** I send `PATCH /api/players/me` with a valid `display_name` and/or `avatar`, **then** my profile is updated and subsequent `GET /api/players/me` returns the new values. | `PlayersResourceTest#patchProfileUpdatesAndValidatesDisplayNameAndAvatar`; Bruno `services/players/public/10-patch-profile-valid` |
| 2 | **Given** I send an invalid `display_name` or disallowed `avatar`, **when** I call `PATCH /api/players/me`, **then** I receive 400 `VALIDATION_ERROR` and my profile does not change. | `PlayersResourceTest#patchProfileUpdatesAndValidatesDisplayNameAndAvatar`; Bruno `services/players/public/11-patch-profile-invalid-avatar` |
| 3 | **Given** I am not authenticated, **when** I call `PATCH /api/players/me`, **then** I receive 401 `UNAUTHORIZED`. | `PlayersResourceTest#profileWithoutTokenReturns401WithBearerChallenge`; Bruno `access/04-players-no-token-401` |

## US-05 — Protect my account data

**Dependencies:** US-01, US-02 · **Service:** auth, gateway, every service · **Phase:** P1 (access control), P2 (completed: gateway auth_request, shared access policy, refresh/logout, encryption at rest, login backoff, audit log)

| # | Criterion | Tests |
|---|---|---|
| 1 | **Given** another player's team, monster, run or encounter, **when** I request, change or use it with my token, **then** I receive 404, never 403, and nothing changes (P-11). | Bruno `services/teams/public/10-put-update-invalid-foreign`, `14-get-get-invalid-foreign`, `13-post-create-invalid-foreign-monster`, `services/monsters/public/17-get-instance-invalid-foreign`, `services/history/internal/11-get-detail-invalid-foreign`; `scripts/security-check.sh` §3 |
| 2 | **Given** I registered, **then** my password is stored only as a bcrypt hash and never returned by any API. | `PasswordHasherTest#hashIsBcryptAndNotThePassword`, `RegistrationResourceTest#validRegistrationCreatesAccountAndProfile`; e2e `e2e/01-register` |
| 3 | **Given** no token, a tampered, unsigned or expired token, **when** I call any protected endpoint, **then** I receive 401 (enforced both at the gateway via `auth_request` and in each service via the shared `access-policy.json`); a player token on an internal endpoint gets 403 (404 through the gateway). | `TokenVerificationTest`, `AuthorizeResourceTest`, `AccessPolicyTest` (all 6 services); Bruno `access/04`…`09`, `services/auth/internal/03`…`05`, e2e `e2e/15-internal-not-exposed`, `16-no-token`, `17-bad-token`; `scripts/check-policy-sync.sh`; `scripts/security-check.sh` §2, §4 |
| 4 | **Given** repeated failed login attempts from the same IP or against the same account, **when** I keep trying, **then** the gateway rate-limits (`/api/auth/*` and `/api/`) with 429 and `auth` locks the account for 15 minutes after 5 consecutive failures. | `LoginBackoffTest#fiveWrongAttemptsLockSixthEvenWithCorrectPassword`, `#successfulLoginClearsFailureCounter`; `scripts/security-check.sh` §10 |
| 5 | **Given** any client, **when** it talks to the backend, **then** every hop uses TLS (client→gateway, gateway→service, service→service, service→database with `pg_hba.conf` `hostssl`) and plain HTTP is refused. | CI job `compose-smoke` (plain HTTP refused); `scripts/security-check.sh` §9 |
| 6 | **Given** the auth database is read by an attacker, **then** usernames are encrypted (AES-256-GCM with random IV and HMAC blind index) and refresh tokens are stored only as SHA-256 hashes. | `UsernameCryptoTest#encryptAndDecryptRoundtrip`, `#randomIvProducesDifferentCiphertextsForSamePlaintext`, `#tamperedCiphertextOrIvFailsDecryption`; `RefreshAndLogoutTest#passwordLoginReturns300sAccessTokenAndStoresOnlyRefreshHash` |
| 7 | **Given** I log out or a consumed refresh token is reused, **when** any refresh token from that session family is used again, **then** it is rejected with `400 invalid_grant` while independent sessions remain unaffected. | `RefreshAndLogoutTest#logoutRevokesWholeFamilyIdempotentlyAndValidatesMissingParam`, `#reuseOfConsumedTokenRevokesEntireFamilyAndCommitsDespite400`, `#independentSessionRemainsUsableWhenAnotherFamilyIsRevoked`; Bruno `services/auth/public/09-logout-valid`, `access/11`…`17` |
| 8 | **Given** authentication events or gateway requests occur, **then** structured JSON audit lines record verified identity (`player_id` or HMAC blind index), action, timestamp and outcome without leaking any secret. | `AuditLogTest`, `AuditTrailTest`, `UsernameBlindIndexTest`; `scripts/check-audit-log.sh` (18/18 passed) |

## US-06 — Receive a starter collection

**Dependencies:** US-01, US-10 · **Service:** players, monsters · **Phase:** P1

| # | Criterion | Tests |
|---|---|---|
| 1 | **Given** I registered, **when** I request the starter pool twice, **then** the same 10 distinct common species are offered for the current run. | `PoolGeneratorTest#picksTenDistinctCommonsWithSeededRandom`; Bruno `services/players/public/03-get-pool-valid` |
| 2 | **Given** I have not chosen starters, **when** I choose three distinct offered species, **then** each is added once at level 1 and my run is opened. | Bruno `services/players/public/08-post-choose-valid` (requires EN-1), `services/monsters/internal/05-post-starters-valid` |
| 3 | **Given** I choose fewer than three, duplicates, or an outside species, **when** I submit, **then** I receive `400 INVALID_STARTERS`, with no collection change. | Bruno `services/players/public/05-post-choose-invalid-duplicate`, `06-post-choose-invalid-two`, `07-post-choose-invalid-outside-pool` |
| 4 | **Given** I already chose starters, **when** I submit again, **then** I receive `409 STARTERS_ALREADY_CHOSEN`; a repeated internal grant must not add more instances. | Bruno `services/players/public/09-post-choose-invalid-second`, `services/monsters/internal/06-post-starters-idempotent` |

## US-07 — Choose initial monsters (optional)

**Dependencies:** US-06 · **Service:** players · **Phase:** P1 (green story implemented)

| # | Criterion | Tests |
|---|---|---|
| 1 | **Given** a persistent pool of ten common species, **when** I select three different listed species, **then** exactly those three become my starters. | Bruno `services/players/public/08-post-choose-valid` (requires EN-1) |
| 2 | **Given** I have finished a run, **when** its id is sent for a new-run reset, **then** the next run has a new id and a new stable pool; retrying with the old id is a no-op. | Bruno `services/players/internal/03-post-new-run`, `04-post-new-run-retried` |

## US-09 — View my collection

**Dependencies:** US-01, US-06 · **Service:** monsters · **Phase:** P1

| # | Criterion | Tests |
|---|---|---|
| 1 | **Given** I own active monsters, **when** I request `/api/collection`, **then** I receive a paginated list of my own active instances only. | Bruno `services/monsters/public/13-get-collection-valid`, `17-get-instance-invalid-foreign` |
| 2 | **Given** a run has been archived, **when** I request `/api/collection?run_id=<my-run-id>`, **then** its old instances remain viewable. | Bruno `services/monsters/internal/13-post-archive-valid`, `19-get-collection-archived-valid` |
| 3 | **Given** an instance does not exist or is owned by another player, **when** I request it, **then** I receive 404. | Bruno `services/monsters/public/16-get-instance-missing`, `17-get-instance-invalid-foreign` |

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

## US-13 — Improve or customize my monsters

**Dependencies:** US-09, US-12 · **Service:** monsters · **Phase:** P1 (XP and levels), P2 (nickname customization)

| # | Criterion | Tests |
|---|---|---|
| 1 | **Given** my team wins an encounter, **when** XP is awarded, **then** each member gains XP and levels up automatically with recalculated effective stats. | `LevelingTest#reachesSeveralLevelsInOneGrant`, `StatCalculatorTest`; Bruno `services/monsters/internal/09-post-xp-valid`, e2e `e2e/14-collection-after-battle` |
| 2 | **Given** I own a monster instance, **when** I send `PATCH /api/collection/{owned_id}` with a valid `nickname` (or `null` to clear it), **then** the nickname is persisted and returned by `GET /api/collection/{owned_id}`. | `MonstersResourceTest#playerCollectionViewsAndNicknamePatch`; Bruno `services/monsters/public/18-patch-nickname-valid` |
| 3 | **Given** an invalid nickname or a monster owned by another player, **when** I call `PATCH /api/collection/{owned_id}`, **then** I receive 400 `VALIDATION_ERROR` (invalid nickname) or 404 `NOT_FOUND` (foreign/missing). | `MonstersResourceTest#playerCollectionViewsAndNicknamePatch`; Bruno `services/monsters/public/19-patch-nickname-invalid` |

## US-14 — Create a team

**Dependencies:** US-09 · **Service:** teams · **Phase:** P1

| # | Criterion | Tests |
|---|---|---|
| 1 | **Given** active monsters owned by me, **when** I create a team with 1–3 distinct instances, **then** it is saved with the order I selected. | Bruno `services/teams/00-setup/05-post-create-test-team`, `services/teams/public/05-post-create-valid` |
| 2 | **Given** four selected instances, **when** I create a team, **then** I receive `400 TEAM_INVALID` with `TEAM_SIZE`, and nothing is saved. | `TeamValidatorTest#sizeAndDuplicateAndOwnershipErrorsAreCollected`; Bruno `services/teams/public/06-post-create-invalid-size` |
| 3 | **Given** five teams already saved for the run, **when** I create a sixth, **then** `TEAM_INVALID` reports `TEAM_LIMIT`. | `TeamValidatorTest#validatesNameAndTeamLimit`; Bruno `services/teams/public/15-post-create-fill-to-limit`, `16-post-create-invalid-sixth-team` |

## US-15 — Modify a team

**Dependencies:** US-14 · **Service:** teams · **Phase:** P1

| # | Criterion | Tests |
|---|---|---|
| 1 | **Given** a team I own, **when** I replace its name or member order, **then** GET reflects the new composition in the same team. | Bruno `services/teams/public/09-put-update-valid`, `07-get-get-valid` |
| 2 | **Given** a missing or foreign team, **when** I attempt to update or delete it, **then** the service returns 404 without revealing ownership. | Bruno `services/teams/public/10-put-update-invalid-foreign`, `12-delete-delete-missing`, `14-get-get-invalid-foreign` |

## US-16 — Validate a team

**Dependencies:** US-14 (related; can also validate before saving) · **Service:** teams · **Phase:** P1

| # | Criterion | Tests |
|---|---|---|
| 1 | **Given** one to three distinct active owned monsters from the same run, **when** I validate them, **then** I receive `200 {valid:true,errors:[]}` without saving anything. | `TeamValidatorTest#validOwnedActiveMembersInOrder`; Bruno `services/teams/public/03-post-validate-valid` |
| 2 | **Given** a duplicate, a foreign id, an archived monster, or mixed-run instances, **when** I validate, **then** `valid:false` identifies each applicable rule. | `TeamValidatorTest#sizeAndDuplicateAndOwnershipErrorsAreCollected`, `#rejectsArchivedAndMixedRunMembers`; Bruno `services/teams/public/04-post-validate-invalid-duplicate`, `13-post-create-invalid-foreign-monster` |
| 3 | **Given** a previously saved team becomes invalid after a run change, **when** encounters requests it internally, **then** teams rejects it with `409 TEAM_INVALID`. | Bruno `services/teams/internal/01-get-team-revalidated` (valid counterpart); negative integration test when EN-1 is available |

## US-17 — Browse encounters

**Dependencies:** US-06 (a run is opened when the starters are chosen) · **Service:** encounters · **Phase:** P1

| # | Criterion | Tests |
|---|---|---|
| 1 | **Given** a new run at stage 1, **when** I list the encounters, **then** I see the three normal templates of stage 1 with name, element, level range and rarity weights, and no boss. | `RunRulesTest#stageOneShowsOnlyItsNormalTemplates`, `RunsApiTest#encountersOfStageOne`; Bruno `services/encounters/public/06-get-encounters-valid` |
| 2 | **Given** two wins in the current stage, **when** I list the encounters, **then** the stage boss is also available; before that, opening the boss template gives 404. | `RunRulesTest#bossUnlocksAfterTwoWins`, `RunsApiTest#encountersOfStageOne`; Bruno `services/encounters/public/08-get-encounter-boss-locked` |
| 3 | **Given** I beat the boss of a stage, **when** I list the encounters, **then** the normal templates of the next stage are added, those of the cleared stages stay playable, and the beaten boss is not offered again. | `RunRulesTest#afterTheBossTheNextStageOpensAndClearedStagesStayPlayable`, `#bossAlreadyBeatenIsNotOfferedAgain`, `BattleApiTest#bossVictoryMovesToTheNextStage` |
| 4 | **Given** no active run, **when** I list the encounters, **then** I receive `409 NO_ACTIVE_RUN`; my runs and run details are visible only to me (another player's run gives 404). | `RunsApiTest#withoutActiveRun`, `#runsAreScopedAndPaginated`; Bruno `services/encounters/public/05-get-run-detail-missing` |
| 5 | **Given** players opens my run after the starters, **when** the call is retried, **then** the same run is returned (`200`) and a second active run is refused (`409`). | `RunsApiTest#openRunIsIdempotent`; Bruno `services/encounters/internal/01-post-open-run-idempotent`, `02-post-open-run-invalid-second-active` |

## US-18 — Start an encounter

**Dependencies:** US-17, US-14 · **Service:** encounters (calls teams, monsters) · **Phase:** P1

| # | Criterion | Tests |
|---|---|---|
| 1 | **Given** an available template and a valid saved team, **when** I start a battle, **then** I receive `201` with the wild monster (level in the template range, full HP, sprite), my team in order with the first member active, and no result. | `BattleApiTest#startReturnsTheInitialState`; Bruno `services/encounters/public/13-post-battle-valid` |
| 2 | **Given** a battle in progress, **when** I start another one, **then** I receive `409 BATTLE_IN_PROGRESS`. | `BattleApiTest#secondStartIs409`; Bruno `services/encounters/public/14-post-battle-invalid-twice` |
| 3 | **Given** a team that teams rejects or that belongs to another run, **when** I start a battle, **then** I receive `409 TEAM_INVALID`; an unknown team gives 404. | `BattleApiTest#invalidOrMissingTeam`, `#teamOfAnotherRunIsInvalid`; Bruno `services/encounters/public/12-post-battle-invalid-team` |
| 4 | **Given** a template that is not available (stage not reached, boss locked), **when** I start a battle, **then** I receive 404. | `BattleApiTest#unavailableTemplatesAre404`; Bruno `services/encounters/public/11-post-battle-invalid-template` |
| 5 | **Given** a normal template, **when** the wild monster is chosen, **then** it is never one of the 8 boss species. | `WildSelectorTest`, `BattleApiTest#startReturnsTheInitialState` |

## US-19 — Resolve an encounter

**Dependencies:** US-18 · **Service:** encounters · **Phase:** P1

| # | Criterion | Tests |
|---|---|---|
| 1 | **Given** a battle in progress, **when** I send `attack`, **then** the response contains the round events computed by the server (including P1 and P2 monster abilities in engine v2) and the updated HP. | `BattleApiTest#validActionsResolveRounds`, `AbilityEngineTest` (all 7 P2 abilities); Bruno `services/encounters/public/18-post-action-attack-valid` |
| 2 | **Given** a battle in progress, **when** the wild monster's HP reaches 0, **then** the outcome is `victory`; **when** all my team is KO, **then** the outcome is `defeat`, the run is `lost` and it is reset (collection archived, teams removed, new starter pool). | `BattleApiTest#victoryGrantsRewardXpAndHistoryWithTheSameEncounterId`, `#defeatLosesTheRunAndResetsIt`, `BattleTest#victoryIsPossible`, `#defeatIsPossible` |
| 3 | **Given** any state, **when** I try to send an outcome or HP values, **then** the request is rejected (400) because only actions are accepted. | `BattleApiTest#invalidActionsAreRejected`; Bruno `services/encounters/public/16-post-action-invalid-outcome-field` |
| 4 | **Given** a victory, **when** the battle ends, **then** the wild monster joins my collection at its level, every team member gets the XP, and the record is stored in history, all with the same `encounter_id`; if monsters is down the reward is shown as pending and retried both lazily and by the scheduled sweep. | `BattleApiTest#victoryGrantsRewardXpAndHistoryWithTheSameEncounterId`, `#rewardServiceDownMeansPendingRewardRetriedOnNextRequest`, `EncounterSweepSchedulerTest#sweepRetriesPendingBattleCompletions` |
| 5 | **Given** I beat a stage boss, **when** the battle ends, **then** the run moves to the next stage; beating the stage-8 boss wins the run and `POST /api/runs` lets me choose new starters. | `RunRulesTest#stageEightBossWinsTheRun`, `BattleApiTest#bossVictoryMovesToTheNextStage`, `#stageEightBossWinsTheRunAndANewRunCanStart` |
| 6 | **Given** a battle left idle for more than 30 minutes, or lost because the service restarted, **when** the scheduled sweep runs or I make my next request, **then** it is closed as `expired` or `interrupted` without reward and my run continues. | `BattleApiTest#idleBattleExpires`, `#battleMissingFromMemoryIsClosedAsInterrupted`, `EncounterSweepSchedulerTest#sweepExpiresIdleBattleWithoutPlayerRequest` |
| 7 | **Given** the run reset fails because a service is down, **when** the scheduled sweep runs or I make my next request, **then** the reset is resumed; until it succeeds, starting a battle gives `409 RESET_PENDING`. | `BattleApiTest#failedResetIsResumedLater`, `EncounterSweepSchedulerTest#sweepResumesPendingRunResets`, `#permanent4xxDuringRunResetDoesNotWedgeResetPendingForever` |

## US-20 — View encounter result details

**Dependencies:** US-19 · **Service:** encounters (the same detail is stored in history, US-24) · **Phase:** P1

| # | Criterion | Tests |
|---|---|---|
| 1 | **Given** my last action ends the battle, **when** I read the response, **then** `status` is `finished` and `result` holds the outcome, the reward (or `null`), the XP gained, the level-ups and the run progress (stage, wins, boss availability, status). | `BattleApiTest#victoryGrantsRewardXpAndHistoryWithTheSameEncounterId`, `#defeatLosesTheRunAndResetsIt`, `#bossVictoryMovesToTheNextStage` |
| 2 | **Given** a finished battle, **when** its record is stored, **then** it contains template, wild monster, team snapshot with stats, outcome, reward, XP, every round, seed and engine version. | `HistoryRecordBuilderTest#victoryRecordHasTheHistoryContract`, `BattleApiTest#victoryGrantsRewardXpAndHistoryWithTheSameEncounterId` |

## US-21 — Leave or escape an encounter

**Dependencies:** US-18 · **Service:** encounters · **Phase:** P1

| # | Criterion | Tests |
|---|---|---|
| 1 | **Given** a battle in progress, **when** I flee successfully, **then** the battle ends as `fled`, I get no reward and my run continues. | `BattleTest#fleeSuccessAndFailure`; `BattleApiTest#validActionsResolveRounds` |
| 2 | **Given** a battle in progress, **when** the flee fails, **then** the wild monster attacks and the battle continues. | `BattleTest#fleeSuccessAndFailure` |
| 3 | **Given** an active run, **when** I abandon it, **then** any battle is recorded as `abandoned`, the run is `abandoned`, my collection is archived and I must choose new starters. | `BattleApiTest#abandonClosesTheBattleAndResetsTheRun`, `#abandonWithoutBattle`; Bruno `services/encounters/public/19-post-abandon-valid`, `22-get-run-current-missing` |
| 4 | **Given** no active run, **when** I abandon, **then** I receive `409 NO_ACTIVE_RUN`. | `BattleApiTest#abandonClosesTheBattleAndResetsTheRun`; Bruno `services/encounters/public/21-post-abandon-invalid-no-run` |

## US-22 — Choose encounter actions

**Dependencies:** US-18 · **Service:** encounters · **Phase:** P1

| # | Criterion | Tests |
|---|---|---|
| 1 | **Given** a battle in progress, **when** I choose `attack`, `switch` to a living member or `flee`, **then** the round is resolved with that action (a switched-in monster does not attack in that round). | `BattleApiTest#validActionsResolveRounds`, `BattleTest#switchedInMonsterDoesNotAttack` |
| 2 | **Given** a battle in progress, **when** I switch to the active member, to a knocked-out member or to a slot outside my team, or send `slot` with a non-switch action, **then** I receive `400 INVALID_ACTION` and nothing changes. | `BattleApiTest#invalidActionsAreRejected`; Bruno `services/encounters/public/17-post-action-invalid-switch-active` |
| 3 | **Given** no battle in progress, **when** I send an action, **then** I receive 404. | `BattleApiTest#invalidActionsAreRejected`; Bruno `services/encounters/public/10-post-action-invalid-no-battle`, `20-get-battle-current-missing` |

## US-23 — View my previous encounters

**Dependencies:** US-19 · **Service:** history (written by encounters) · **Phase:** P1

| # | Criterion | Tests |
|---|---|---|
| 1 | **Given** I finished 3 encounters, **when** I list my history, **then** I see 3 summaries, newest first, with outcome, wild monster and date. | `HistoryResourceTest#listIsNewestFirstSummariesOnly`; Bruno `services/history/public/01-get-list-valid` |
| 2 | **Given** another player finished encounters, **when** I list my history, **then** I see none of theirs. | `HistoryResourceTest#playerAOnlySeesOwnRecords`, `#playerBOnlySeesOwnRecords` |
| 3 | **Given** history across two runs, **when** I filter by `run_id` (and optionally by `outcome`), **then** I see only the matching encounters. | `HistoryResourceTest#listFiltersByRunAndOutcome`; Bruno `services/history/public/03-get-list-filter-outcome` |
| 4 | **Given** more encounters than one page, **when** I list with `page` and `size`, **then** I receive the requested page with `total_items` and `total_pages`; a negative page, a size below 1 or above 50 gives 400 `VALIDATION_ERROR`. | `HistoryResourceTest#paginationAndOutOfRange`; Bruno `services/history/public/05-get-list-invalid-size` |
| 5 | **Given** any state, **when** I filter by an unknown outcome or a `run_id` that is not a UUID, **then** I receive 400 `VALIDATION_ERROR`. | `HistoryResourceTest#invalidFiltersAre400`; Bruno `services/history/public/04-get-list-invalid-outcome` |
| 6 | **Given** no token, **when** I list my history, **then** I receive 401 `UNAUTHORIZED`. | `HistoryResourceTest#publicEndpointsWithoutTokenAre401`; Bruno `services/history/public/02-get-list-unauthorized` |
| 7 | **Given** the encounters service finishes an encounter, **when** it stores the record twice (retry), **then** the first call returns 201, the second 200, and the history holds one record. Only a service token may store (a player token gets 403). | `HistoryResourceTest#insertIsIdempotent`, `#internalEndpointWithPlayerTokenIs403`; Bruno `services/history/internal/01-post-store-valid`, `02-post-store-idempotent`, `06-post-store-wrong-role` |
| 8 | **Given** an invalid record (unknown outcome, more than 500 rounds, missing wild monster, unknown field), **when** encounters stores it, **then** I receive 400 `VALIDATION_ERROR` and nothing is saved. | `HistoryResourceTest#invalidOutcomeInBodyIs400`, `#tooManyTurnsIs400`, `#otherInvalidBodiesAre400`; Bruno `services/history/internal/03-post-store-invalid-outcome`, `04-post-store-invalid-missing-wild`, `05-post-store-invalid-unknown-field` |

## US-24 — View the details of a previous encounter

**Dependencies:** US-23 · **Service:** history · **Phase:** P1

| # | Criterion | Tests |
|---|---|---|
| 1 | **Given** a finished encounter, **when** I open it, **then** I see team, wild monster, outcome, reward, XP and every round's events. | `HistoryResourceTest#detailReturnsFullRecord`, `#storedJsonIsReturnedUntouched`; Bruno `services/history/public/06-get-detail-valid` |
| 2 | **Given** an encounter of another player (or an id that does not exist), **when** I open its id, **then** I receive 404 `NOT_FOUND`, without revealing whether it exists. | `HistoryResourceTest#foreignOrMissingRecordIs404`; Bruno `services/history/public/07-get-detail-missing`, `services/history/internal/11-get-detail-invalid-foreign` |
| 3 | **Given** no token, **when** I open an encounter, **then** I receive 401 `UNAUTHORIZED`. | `HistoryResourceTest#publicEndpointsWithoutTokenAre401`; Bruno `services/history/public/08-get-detail-unauthorized` |

## US-25 — Protect my saved progress from tampering

**Dependencies:** US-12, US-19, US-23 · **Service:** encounters, monsters, history · **Phase:** P1 (server-side rules, internal-only writes, idempotency), P2 (completed: append-only trigger, SHA-256 hash chain, `/api/history/verify` — HI-2)

| # | Criterion | Tests |
|---|---|---|
| 1 | **Given** a battle in progress, **when** the client sends an outcome, HP or any field other than the action, **then** it receives 400 and the result is computed only by the server. | Bruno `services/encounters/public/16-post-action-invalid-outcome-field`; `scripts/security-check.sh` §5 |
| 2 | **Given** a player token, **when** it calls the internal endpoints that grant rewards, XP or history records, **then** it receives 403 (404 through the gateway). | Bruno `services/monsters/internal/02-get-candidates-wrong-role`, `12-get-instances-wrong-role`, `18-post-archive-wrong-role`, `services/history/internal/06-post-store-wrong-role`; e2e `e2e/15-internal-not-exposed` |
| 3 | **Given** a reward, XP grant or history record is retried, **when** the service processes it again, **then** nothing is duplicated. | Bruno `services/monsters/internal/08-post-reward-idempotent`, `10-post-xp-idempotent`, `services/history/internal/02-post-store-idempotent` |
| 4 | **Given** a stored encounter, **then** no API can change or delete it. | No update/delete endpoint exists in history (OpenAPI `docs/openapi/history.yaml`) |
| 5 | **Given** two actions sent at the same time, **then** the battle advances one round per accepted action. | `scripts/security-check.sh` §6 |
| 6 | **Given** a record is changed directly in the database, **when** I call `GET /api/history/verify`, **then** the broken link of the SHA-256 hash chain is reported; `UPDATE`/`DELETE` statements are rejected by the PostgreSQL append-only trigger (`V2__append_only.sql`). | `HashChainTest#hashIsDeterministic64HexAndKeyOrderInvariant`, `#hashChangesWhenPayloadSeqOrPrevHashChanges`, `HistoryResourceTest#verifyReturnsValidForEmptyAndPopulatedChain`, `#databaseTriggerBlocksUpdateAndDelete`, `#verifyDetectsTamperedRow`, `#concurrentInsertsForSamePlayerProduceValidSequentialChain`; Bruno `services/history/public/09-get-verify-valid`, `10-get-verify-unauthorized` |

## US-28 — Play through a simple interface

**Dependencies:** US-01, US-02, US-06, US-09, US-14, US-17, US-18, US-19, US-23 · **Service:** frontend (React) through the gateway · **Phase:** P1, P2 (completed: FE-3 refresh coordinator, server logout, multi-tab sync, ErrorBoundary, HTTP CSP, US-04/US-13 UI, Vitest)

| # | Criterion | Tests |
|---|---|---|
| 1 | **Given** a browser, **when** I open the frontend, **then** I can register, log in, edit my profile (US-04), choose starters, nickname monsters (US-13), build a team, fight battles through Stage 8 (`Omnivoid`), verify my history hash chain, log out and resume on re-login without technical tools. | Full interactive browser E2E playtest (27/11/2026: Stage 1 → Stage 8 `Omnivoid`, 30 battles, hash chain verified); API loop: e2e `e2e/01-register` … `14-collection-after-battle` |
| 2 | **Given** an action is refused (invalid team, no active run, expired access token, network error), **when** the API returns an error, **then** the UI transparently refreshes tokens or shows a clear error / ErrorBoundary fallback instead of failing silently. | Vitest (`session-refresh.test.ts`, `storage.test.ts`, `battleText.test.ts`, 16/16 passed); browser E2E playtest |
| 3 | **Given** a monster or team name containing HTML, **when** the UI shows it, **then** it is rendered as text and protected by an HTTP Content-Security-Policy header. | `scripts/security-check.sh` §8 (no raw HTML rendering in `frontend/src`, CSP & `X-Content-Type-Options: nosniff`) |
| 4 | **Given** the frontend source, **when** CI runs, **then** typecheck, lint, unit tests, production build and dependency audit succeed. | CI job `frontend` (`tsc -b` 0 errors, `eslint .` 0 errors, Vitest 16/16, `vite build` clean, `npm audit --omit=dev` 0 vulnerabilities) |
