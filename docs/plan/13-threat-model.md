# 13 — Threat model and audit log

Follow-up of the Security Assessment lab (slides of 18/11/2026). The lab asks for three things, adapted here to our stack (Quarkus services behind an Nginx gateway instead of the lab's Flask application):

1. **Model** the system as it is today: data flow diagram, assets, attack surfaces, trust boundaries (SEC-2).
2. **Threats** with STRIDE on every element and every arrow; each threat ends with a control and a real test, or with the word GAP (SEC-2).
3. **Rate the gaps** and close the top one with an **audit log** and its tests (SEC-3 in auth, SEC-4 at the gateway).

Deliverable: `THREAT_MODEL.md` in the repository root, linked from the root README and from [`docs/security/README.md`](../security/README.md) (P-25). It is a living document: every P2 security task that changes a control updates its rows.

Decisions: P-25…P-28 in the [plan README](README.md#decision-log).

---

## SEC-2 — Threat model document · P2

**Status:** done on 18/11/2026 ([`THREAT_MODEL.md`](../../THREAT_MODEL.md): 25 threats, all six letters), except the read-aloud review by a team member.

**Depends on:** none (model the current system; controls still planned appear as Gap/Partial with the task that will close them)

### Step 1 — Model

- [x] Ask an AI agent for a **first draft** of the DFD in Mermaid (`flowchart LR`, numbered arrows) from `docker-compose.yml`, `docker-compose.frontend.yml`, `gateway/templates/default.conf.template` and the REST clients in `backend/*/src/main/java/**/client/`. Never give it files from the secrets volume, keys or passwords.
- [x] Check every box and every arrow against the compose files and the code: does it exist, is it a real call? Remove invented parts, add the missing ones (databases, `init-secrets` and its volume, the CI pipeline, the frontend stack). Write a short "What the draft got wrong" list in the document.
- [x] Number each arrow and say how it travels (HTTPS + bearer JWT, HTTPS + service token, JDBC over TLS + password, file mount).
- [x] Draw the trust boundaries as `subgraph`:
  - TB1 Internet / browser | gateway (only `8443` published);
  - TB2 gateway | compose network (services, service-to-service calls);
  - TB3 each service | its own database;
  - TB4 build (repository, CI, images) | what runs, including the generated secrets.
- [x] Check the preview on GitHub or in VS Code.

### Step 2 — Assets, attack surfaces, boundaries

- [x] Assets, one line each with an ID (`A1`, `A2`…). At least: password hashes; usernames (encrypted at rest from AUTH-8); JWT signing key; service client secrets; DB passwords and the local CA key; player game state (collection, teams, runs); history integrity; availability of the API; the audit evidence (SEC-3/4).
- [x] Attack surfaces, one line each: public API through the gateway (register/token/logout, player APIs, public images); the frontend stack; the internal endpoints (reachable only inside the compose network); the test override ports (`docker-compose.test.yml`, tests only); dependencies and images; repository and pipeline.
- [x] Trust boundaries TB1…TB4 with one line on what changes when data crosses each one.
- [ ] Read the diagram aloud to a team member who did not write it: is there an arrow they do not understand?

### Step 3 — STRIDE table

Walk the diagram: for each element and each arrow ask the letters that apply (external entity: S, R; process: all six; data store: T, R, I, D; data flow: T, I, D). Write each threat as a sentence: **who** does **what** to **which asset**.

- [x] One row per threat with the columns `#`, `STRIDE`, `Where` (element or arrow number), `Threat`, `Control (plan task)`, `Test`, `Status`.
- [x] `Test` names a real file or a real Bruno request. A control without such a test is **Partial**. A test that runs only in the manual `scripts/security-check.sh` and not in CI is **Partial** until CI-3 runs it. No control: **GAP**. No cell is empty and no row says "n/a".
- [x] At least **10 threats**, with **all six letters** present (the lab's reference solution has 22).

Candidate threats used as a starting list (all checked and included in `THREAT_MODEL.md`, which is now the reference):

| STRIDE | Candidate threat | Likely control (task) |
|---|---|---|
| S | An attacker guesses a player's password with many logins | bcrypt, uniform `invalid_grant`, per-IP 429 (AUTH-3, INFRA-5): per-IP only, distributed guessing remains |
| S | A forged JWT (`alg:none`, wrong signature, expired) is sent to a service directly | local RS256 verification (INFRA-7), `TokenVerificationTest`; real-endpoint matrix in TEST-5 |
| S | A stolen service client secret is used to mint service tokens | one secret per caller, generated at start, mounted only in that container (INFRA-4) |
| T | A player sends `outcome`/`hp` fields to forge a battle result | unknown JSON fields rejected (conventions), Bruno request |
| T | Hostile input: huge body, control characters, out-of-range numbers | Bean Validation, 16 KB body limit → 413 (INFRA-5) |
| T | Someone on the compose network alters service-to-service traffic | TLS on every hop with the local CA, no trust-all (INFRA-4/5) |
| T | A modified dependency or base image reaches the running stack | pinned versions/digests, Trivy (CI-2), dependency PRs (CI-4) |
| R | A player or a service denies an operation; nobody can say who did what | **none today** → audit log (SEC-3, SEC-4) |
| R | Repeated failed logins cannot be attributed to an account | **none today**: `TokenService` logs only "Failed login" → SEC-3 |
| I | A player reads another player's team, encounter or run by ID | ownership check → 404 (P-11), Bruno IDOR requests |
| I | A database dump reveals usernames | AES-GCM + HMAC index (AUTH-8): gap until implemented |
| I | A token or password ends up in the logs | logging convention (01), SEC-3 test that greps the captured log |
| D | A login flood exhausts CPU with bcrypt | Nginx rate limit 429 (INFRA-5) |
| D | Many open battles fill the memory of the single `encounters` instance | one active battle per run? to verify (R19) |
| E | A player token is used on `/internal/*` | gateway 404 for `/internal/*` (INFRA-5) + `service` role check in each service |
| E | A request reaches a business service without any gateway authorization | INFRA-9: gap until implemented |
| E | A stolen access token is used after the theft | token lifetime (300 s from AUTH-7), refresh rotation and logout |

### Step 4 — Rate and decide

- [x] Rate every Gap and Partial row: likelihood and impact (low/medium/high), each with one line of reason, using the lab's scale (likelihood high = anyone with a script today; impact high = personal data leaked or the service stops for everybody). Rate what is left after the existing control.
- [x] Put the rows on the 3×3 grid (a small table is enough) and choose for each: **Mitigate** (which task), **Accept** (why, and when to look again), **Transfer** or **Avoid**.
- [x] Expected top gap: the R rows (no evidence of who did what). Reason, as in the lab: a whole STRIDE category without control; cheap; it changes no behaviour; without it the other attacks (S guessing, D flooding) cannot be seen and a rate limit cannot be tuned. If the rating disagrees, write why.
- [x] Write the **residual risk** of every mitigated row.

**Done when:** someone who has never seen the system can tell from `THREAT_MODEL.md` where the data goes and where trust changes; the table has ≥ 10 threats with all six letters, every row has a control and a test or says GAP; every Gap/Partial row has a rating and a written decision.

---

## SEC-3 — Audit log in auth · P2

**Status:** done on 18/11/2026. `AuditLog` is a static utility (like the lab's `audit()`), not a CDI bean, so it can be unit-tested without Quarkus; the blind index is the CDI bean `UsernameBlindIndex`. The Quarkus checks live in `AuditTrailTest` instead of `TokenResourceTest`. `init-secrets` writes the HMAC key, and appends it to an existing secrets volume. Refresh/logout events follow AUTH-7.

**Depends on:** SEC-2 (rating that selects the gap). The `user` value of a denied login uses the AUTH-8 HMAC (P-27); if SEC-3 lands first, compute it with the same normalizer and the `username-hmac-key` secret that AUTH-8 introduces (INFRA-4).

The login is the second entry point of the lab (the first is the gateway, SEC-4). Business services behind the gateway do not write audit lines (P-26).

### Code

- [x] `AuditLog` (static utility in `auth`, `it.unipi.ase.tamer.auth.audit`): `audit(String event, Outcome outcome, String user, Map<String, String> facts)` writes **one JSON line** on the JBoss Logging category `audit` at INFO:
  - fields `ts` (UTC, ISO-8601, seconds), `event`, `user`, `outcome` (`success` | `denied` | `failure`), then the facts;
  - serialized with Jackson (escapes newlines and control characters; ASCII only), never with string formatting;
  - every value cut to 64 characters; a null user becomes `-`.
- [x] Console format of the `audit` category: message only (`quarkus.log.category."audit".level=INFO` and a handler or format without the default prefix), so each line is valid JSON that `jq` can read.
- [x] Calls in `TokenService` and the registration flow:

  | Event | Outcome | `user` |
  |---|---|---|
  | `login` (password grant) | `success` | `player_id` (the JWT `sub`) |
  | `login` | `denied` (wrong password, unknown account) | username blind index (HMAC, P-27); `?` if the username does not pass the normalizer |
  | `service_token` (client credentials) | `success` / `denied` | the `client_id` (not personal data), sanitized |
  | `register` | `success` / `denied` (duplicate) | `player_id` on success, blind index on denial |
  | `refresh`, `logout`, `refresh_reuse` (**open, with AUTH-7**) | as appropriate | `player_id` of the token family |

- [x] Facts allowed: `request_id` (from the MDC), `reason` as a fixed code (`invalid_grant`, `invalid_client`, `duplicate`). Never: password, any token, client secret, request body, the plain username.
- [x] Replace the current free-text `"Failed login"` and `"Failed client authentication for client_id=…"` lines with the audit calls (one event, one line).

### Tests (`backend/auth/src/test/java/.../audit/`)

The Java equivalent of pytest `caplog`: in `@BeforeEach`, add an in-memory `java.util.logging.Handler` to `Logger.getLogger("audit")` (JBoss LogManager is JUL compatible), remove it in `@AfterEach`.

- [x] `AuditLogTest` (unit): fields and UTC timestamp; one record for one call; a user containing `\n{"user":"root"}` produces exactly one line with no newline; values longer than 64 characters are cut; null user → `-`.
- [x] `AuditTrailTest` (`@QuarkusTest`, 9 tests; planned as `TokenResourceTest` additions): a wrong password produces exactly one `login`/`denied` line whose `user` is the expected blind index; a correct login produces `login`/`success` with the `player_id`; the captured text contains neither the password nor the issued `access_token`; a wrong client secret produces `service_token`/`denied` and the secret is not in the text.
- [x] Mutation check by hand, recorded in `THREAT_MODEL.md`: delete the `audit(...)` call in `TokenService` → the tests turn red (done 18/11/2026: 4 of 9 red).

**Done when:** tests green in `mvn verify` (so in CI-1); removing the audit call makes them red; the R rows of `THREAT_MODEL.md` say *Gap → fixed* with the test names and the residual risk.

---

## SEC-4 — Request audit at the gateway · P2

**Status:** done on 18/11/2026 except the `user` field, which stays `-` until INFRA-9 (P-28): the JSON format, the outcome/health maps, `scripts/check-audit-log.sh` and the CI step are in place. The path is cut to 128 characters and the line also carries `rt` (request time).

**Depends on:** INFRA-9 (the user must come from a verified token, P-28), SEC-3 (same line format)

The equivalent of the lab's `@app.after_request`: Nginx writes one JSON line per request after the response.

- [ ] **(open, with INFRA-9)** INFRA-9 adapter: on `204` return the verified `sub` in a response header (`X-Auth-Subject`). In the protected locations, `auth_request_set $audit_user $upstream_http_x_auth_subject;`. The header is read only from the adapter's reply, is never forwarded to the backend, and is never accepted from the client. Public routes and denied requests log `-`.
- [x] `map $status $audit_outcome { ~^[23] success; ~^40[13]$ denied; default failure; }` and `map $uri $audit_loggable { /health 0; default 1; }` (health checks are noise).
- [x] `log_format audit escape=json '{"ts":"$time_iso8601","event":"request","user":"$audit_user","outcome":"$audit_outcome","method":"$request_method","path":"$uri","status":"$status","request_id":"$request_id","ip":"$remote_addr"}';` with `access_log /dev/stdout audit if=$audit_loggable;`, replacing the current text format `tamer`. Use `$uri` (no query string), never `$request` or any header; the container runs in UTC.
- [x] Test `scripts/check-audit-log.sh`, added as a step of the `compose-smoke` job (update `.github/workflows/ci.yml` and `docs/ci/ci.yml` together): perform a request without token (401), a request with a valid token (200), a wrong login; then read `docker compose logs --no-log-prefix gateway auth` and check with `jq` that every audit line is valid JSON, that the expected `user`/`outcome` pairs are there, and that no line contains `Bearer`, the test password or the issued token. Bruno cannot read logs.
- [x] Mutation check by hand: remove the `access_log ... audit` line → the script fails (done 18/11/2026: 3 gateway checks fail; 14/14 again after restoring it).

**Done when:** every non-health request through the gateway produces one JSON line with the verified user and the outcome; the CI step is green and turns red without the log line; `THREAT_MODEL.md` updated.

### Residual risk (to copy into `THREAT_MODEL.md`)

Audit lines go to the containers' stdout (Docker `json-file` driver). Anyone with access to the Docker host can read, edit or delete them, and they are lost with the containers. Accepted for the project: sending them to a collector that the application cannot change (append-only storage) is out of scope; look again if the system is deployed outside a single development host. No alerting (for example many `denied` in a minute) is implemented; the lines are structured so that it can be added.
