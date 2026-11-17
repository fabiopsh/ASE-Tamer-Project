# 11 — CI and security

GitHub Actions runs builds and tests from P1 (P-18); security scans are added in P2 with **Semgrep** (static analysis of our code) and **Trivy** (known vulnerabilities in dependencies and Docker images) (P-15).

The requirements also ask for **a copy of the workflow YAML in `docs/`**: the CI checks that `docs/ci/ci.yml` is identical to `.github/workflows/ci.yml`.

All actions are pinned to a commit SHA (the version tag is in a comment); the Semgrep and Trivy images are pinned to a version and a digest. Semgrep itself flags mutable action tags (`github-actions-mutable-action-tag`).

## CI-1 — Build and test workflow · P1

**Depends on:** INFRA-1, FE-1

`.github/workflows/ci.yml`, triggered on `push` to `main` and on every `pull_request`:

| Job | Steps |
|---|---|
| `backend` | `actions/checkout`, `actions/setup-java` (Temurin 21, Maven cache), `mvn -B -f backend/pom.xml verify` (Dev Services use the runner's Docker) |
| `frontend` | `actions/checkout`, `actions/setup-node` (pinned LTS, npm cache), `npm ci`, `npm run lint`, `npm run build` in `frontend/` |
| `compose-smoke` | `docker compose -f docker-compose.yml -f docker-compose.test.yml up -d --build --wait` (waits for health checks), `scripts/test-env.sh`, `curl --cacert … https://localhost:8443/health`, plain HTTP refused, **Bruno component + integration + e2e** (`npm test` in `docs/tests/bruno`), `docker compose logs` on failure, `docker compose down -v` |
| `docs-sync` | `diff .github/workflows/ci.yml docs/ci/ci.yml` |

- [x] Workflow file + copy in `docs/ci/ci.yml`.
- [ ] Branch protection on `main`: PR required, CI green required (repository admin, GitHub settings: see below).
- [x] Badge in the README.

Branch protection (Settings → Branches → Add rule for `main`, or a ruleset): *Require a pull request before merging*, *Require status checks to pass* with `Backend build and tests`, `Frontend lint and build`, `Compose smoke and API tests`, `Workflow copy in docs/ci`, and, from P2, the four security scans plus the access-security regression gate (CI-3).

**Done when:** a PR that breaks a test or the frontend build is red.

## CI-2 — Security analysis · P2

**Depends on:** CI-1

| Job | Tool | Configuration | Fails on |
|---|---|---|---|
| `sast` | Semgrep (container `semgrep/semgrep:1.182.0@sha256:…`) | `semgrep scan --config p/java --config p/typescript --config p/react --config p/dockerfile --config p/secrets --config p/owasp-top-ten --text --sarif-output --json-output`, then `jq` counts the ERROR findings (WARNINGs are reported, not blocking) | any finding of severity ERROR |
| `deps` | Trivy `fs` mode (container `aquasec/trivy:0.76.0@sha256:…`, no third-party action) | `mvn dependency:resolve` first, then `trivy fs --offline-scan --scanners vuln --severity CRITICAL,HIGH --ignore-unfixed --exit-code 1` with `~/.m2` mounted (Maven poms, `package-lock.json`, `requirements.txt`). Without the local repository Trivy downloads every parent pom and Maven Central answers 429 | any CRITICAL/HIGH |
| `images` | Trivy `image` mode, one job looping over `docker compose config --images` (a matrix would rebuild the images once per entry) | `docker compose build`, then scan `gateway`, the six services, `init-secrets` and `postgres`; `--severity CRITICAL,HIGH --ignore-unfixed --exit-code 1` | any CRITICAL/HIGH |
| `config` | Trivy `config` mode | Dockerfiles and compose misconfigurations | HIGH+ |

`--ignore-unfixed`: a vulnerability without a fixed version cannot be removed by upgrading; it is reported by a run without the flag and discussed in the report if relevant.

- [x] Add the jobs to `ci.yml` (and its copy).
- [ ] Upload SARIF results to GitHub code scanning: not available, the repository is private and code scanning needs GitHub Advanced Security. The Semgrep SARIF/JSON are uploaded as a workflow artifact instead.
- [x] Triage (09/11/2026): see [docs/security/README.md](../security/README.md).
- [ ] Re-run before each delivery; save the final clean output for the report.

**Done when:** all security jobs green on `main`; images have no CRITICAL/HIGH vulnerabilities.

## CI-3 — Access-security pipeline gate · P2

**Status:** planned after the Access Security review of 17/11/2026.

**Depends on:** CI-1, TEST-5, AUTH-7, INFRA-9, INFRA-10

- [ ] Add a mandatory `Access security regression` check on the same push/PR triggers as CI-1. Run TEST-5's direct-service and gateway Bruno matrix, auth refresh/logout tests, gateway upstream-stub checks, policy consistency checks and FE-3 client tests. This is additional to the existing `/probe` unit/service tests and general e2e flow.
- [ ] Start an isolated stack, wait for readiness, register the suite's users, and generate ephemeral signed fixtures before the access tests. Use the normal final 300 s access TTL; test expiry with controlled signed fixtures/clock handling instead of sleeping five minutes for every case.
- [ ] Include a correctly formed, otherwise valid token with a bad signature, an expired token and `alg:none` on real APIs. Reuse the existing manual script's relevant JWT checks through TEST-5 so they cannot silently remain outside CI.
- [ ] Add a separate `Access verification mutation` job/workflow for controlled verification changes (TEST-5). Run it on changes to JWT/policy/gateway enforcement or its tests and allow manual dispatch. The job succeeds only when its baseline passes and each isolated bypass is rejected by the intended negative test; an arbitrary nonzero test exit is insufficient.
- [ ] Keep mutation images/configuration confined to the disposable job; do not add runtime bypass switches to deployable images. Save sanitized baseline/negative-test reports and service logs, excluding passwords, bearer/refresh tokens, signing keys and raw auth request bodies.
- [ ] Keep the existing SAST/dependency/image/configuration jobs covering auth. Update `.github/workflows/ci.yml` and `docs/ci/ci.yml` together, and add the regression check to the required-check list when configuring branch protection.

**Done when:** the ordinary pipeline fails on real endpoint authentication/authorization or refresh/logout regressions, and the controlled mutation run proves that removing the gateway check or an individual service's signature check is detected. Archive the final results for the report.

## CI-4 — Automated dependency updates · P2

**Status:** planned after the Access Security review of 17/11/2026.

**Depends on:** CI-1, CI-2

- [ ] Add `.github/dependabot.yml` with a weekly update schedule and coverage for committed dependency manifests: Maven (`backend`, including auth through the aggregator), npm (`frontend`, `docs/tests/bruno`, `scripts/openapi`), pip (`docs/locust` and any other committed requirements), GitHub Actions, and every committed Dockerfile directory (`gateway`, the six backend services, `infra/init-secrets`, `infra/postgres`, and the frontend image).
- [ ] Include the exact dependency-manifest/Dockerfile inventory in the configuration review; keep pinned package versions, lockfiles and action/image digests consistent when update PRs are generated.
- [ ] Run the regular build/API/security gates for dependency PRs. Keep grouped update PRs small enough to identify which upgrade caused a failure; merging follows the existing PR/green-CI workflow.
- [ ] Verify that GitHub accepts the configuration and detects the auth Maven/runtime-image dependencies. Document successful configuration validation and the first scheduled/manual update check in the delivery notes.

**Done when:** scheduled update checks cover auth and the other repository ecosystems, and generated dependency PRs are evaluated by the same CI gates as code changes.

## SEC-1 — Requirements §6 compliance and self-attack · P2

Map every security requirement to its implementation (this table goes into the report):

| Requirement (§6) | Implementation | Plan task |
|---|---|---|
| Containers in user mode | Non-root `USER` in every image, `user:` in compose, `no-new-privileges`, `cap_drop: ALL` | INFRA-2, INFRA-6 |
| HTTPS/TLS everywhere, self-signed certificates per service | Local CA + one certificate per container, TLS on every hop including Postgres | INFRA-4, INFRA-5, INFRA-3 |
| Input sanitisation | Bean Validation on every DTO and parameter, unknown JSON fields rejected, body size limit at the gateway, parameterised queries | conventions, every service |
| Sensitive data encrypted in the DB | Username AES-GCM + HMAC index, passwords bcrypt, refresh tokens hashed | AUTH-8, AUTH-7 |
| OAuth2 + JWT, secure credentials | Token endpoint (password, refresh, client credentials), RS256, 300 s access tokens, rotation, family revocation on logout/reuse | AUTH-3, AUTH-4, AUTH-7 |
| Authentication and operation authorization | Gateway adapter plus independent service verification, one executable method/path/role policy | INFRA-9, INFRA-10 |
| Access-security regression proof | Real API positive/401/403 matrix, refresh/logout flows, controlled disabled-verification checks | TEST-5, CI-3 |
| Static and dependency analysis and updates | Semgrep + Trivy in CI, scheduled dependency-update PRs | CI-2, CI-4 |
| Images without critical/high vulnerabilities | Trivy image scan gate | CI-2 |

Self-attack checklist (run against the full stack before 15/12, record results in the report):

- [ ] SQL injection payloads in every string/query parameter → 400, no error leakage.
- [ ] JWT: `alg: none`, wrong signature, expired, wrong audience, player token on `/internal/*` → 401/403.
- [ ] Complete TEST-5/CI-3 baseline and mutation checks on real gateway/service endpoints; record which verifier each negative test protects.
- [ ] Refresh/logout: access TTL 300 s, rotation, reuse revocation, idempotent server logout after access expiry, independent sessions and concurrency. Confirm that issued access JWTs remain valid only until expiry plus configured clock skew.
- [ ] IDOR: foreign `team_id`, `owned_id`, `encounter_id`, `run_id` → 404.
- [ ] `/internal/*` and `/q/*` through the gateway → 404.
- [ ] Forged results: send `outcome`/`hp` fields to battle actions → 400 (unknown fields).
- [ ] Replay: repeat a reward/xp internal call → no duplicates (component test with `tests` client).
- [ ] Brute force: many token requests → 429, then backoff.
- [ ] Oversized bodies → 413.
- [ ] XSS: script tags in `display_name` / team name → rendered as text in the UI.
- [ ] Concurrency: two `actions` requests at once, two starter choices at once → consistent state.
- [ ] TLS: plain HTTP to the gateway refused; service certificates verified (no `trust-all`).
