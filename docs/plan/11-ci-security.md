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

Branch protection (Settings → Branches → Add rule for `main`, or a ruleset): *Require a pull request before merging*, *Require status checks to pass* with `Backend build and tests`, `Frontend lint and build`, `Compose smoke and API tests`, `Workflow copy in docs/ci`, and, from P2, the four security jobs.

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

## SEC-1 — Requirements §6 compliance and self-attack · P2

Map every security requirement to its implementation (this table goes into the report):

| Requirement (§6) | Implementation | Plan task |
|---|---|---|
| Containers in user mode | Non-root `USER` in every image, `user:` in compose, `no-new-privileges`, `cap_drop: ALL` | INFRA-2, INFRA-6 |
| HTTPS/TLS everywhere, self-signed certificates per service | Local CA + one certificate per container, TLS on every hop including Postgres | INFRA-4, INFRA-5, INFRA-3 |
| Input sanitisation | Bean Validation on every DTO and parameter, unknown JSON fields rejected, body size limit at the gateway, parameterised queries | conventions, every service |
| Sensitive data encrypted in the DB | Username AES-GCM + HMAC index, passwords bcrypt, refresh tokens hashed | AUTH-8, AUTH-7 |
| OAuth2 + JWT, secure credentials | Token endpoint (password, refresh, client credentials), RS256, short-lived tokens, rotation | AUTH-3, AUTH-4, AUTH-7 |
| Static and dependency analysis | Semgrep + Trivy in CI | CI-2 |
| Images without critical/high vulnerabilities | Trivy image scan gate | CI-2 |

Self-attack checklist (run against the full stack before 15/12, record results in the report):

- [ ] SQL injection payloads in every string/query parameter → 400, no error leakage.
- [ ] JWT: `alg: none`, wrong signature, expired, wrong audience, player token on `/internal/*` → 401/403.
- [ ] IDOR: foreign `team_id`, `owned_id`, `encounter_id`, `run_id` → 404.
- [ ] `/internal/*` and `/q/*` through the gateway → 404.
- [ ] Forged results: send `outcome`/`hp` fields to battle actions → 400 (unknown fields).
- [ ] Replay: repeat a reward/xp internal call → no duplicates (component test with `tests` client).
- [ ] Brute force: many token requests → 429, then backoff.
- [ ] Oversized bodies → 413.
- [ ] XSS: script tags in `display_name` / team name → rendered as text in the UI.
- [ ] Concurrency: two `actions` requests at once, two starter choices at once → consistent state.
- [ ] TLS: plain HTTP to the gateway refused; service certificates verified (no `trust-all`).
