# Security analysis and self-attack

CI-2, CI-3, CI-4 and SEC-1 of [docs/plan/11-ci-security.md](../plan/11-ci-security.md). The CI runs
Semgrep, Trivy, policy-sync (`scripts/check-policy-sync.sh`), access-security regression (`TEST-5` /
`CI-3`) and audit-log verification on every push and pull request
([.github/workflows/ci.yml](../../.github/workflows/ci.yml)), while `.github/dependabot.yml`
automates dependency updates (`CI-4`).

The threat model (data flow diagram, STRIDE threats with their control, test and status, rated
gaps) is [THREAT_MODEL.md](../../THREAT_MODEL.md) in the repository root (SEC-2). The audit log
of the gateway and auth (SEC-3, SEC-4) is checked on the running stack by
`scripts/check-audit-log.sh [gateway-url] [ca-file]`, also run by the CI `compose-smoke` job.

## Running the scans locally

```bash
# SAST (Semgrep), same rule sets as the CI
docker run --rm -v "$PWD:/src" -w /src semgrep/semgrep:1.182.0 semgrep scan --metrics=off \
  --config p/java --config p/typescript --config p/react --config p/dockerfile \
  --config p/secrets --config p/owasp-top-ten

# Dependencies (Trivy fs): resolve the Maven dependencies first, then scan offline
mvn -B -q -f backend/pom.xml dependency:resolve
docker run --rm -v "$HOME/.m2:/root/.m2:ro" -v "$PWD:/src" -w /src aquasec/trivy:0.76.0 \
  fs --offline-scan --scanners vuln --severity CRITICAL,HIGH --ignore-unfixed --ignorefile .trivyignore .

# Images (Trivy image), after docker compose build
for image in $(docker compose config --images | sort -u); do
  docker run --rm -v /var/run/docker.sock:/var/run/docker.sock aquasec/trivy:0.76.0 \
    image --scanners vuln --severity CRITICAL,HIGH --ignore-unfixed "$image"
done

# Misconfigurations (Trivy config)
docker run --rm -v "$PWD:/src" -w /src aquasec/trivy:0.76.0 config --severity CRITICAL,HIGH .

# Self-attack (SEC-1) against the normal stack (without docker-compose.test.yml: real rate limit)
docker compose up -d --build --wait && scripts/export-ca.sh && scripts/security-check.sh
```

On Docker Desktop give the VM at least 6 GB of memory: the build of six Quarkus services plus the
running stack does not fit in 4 GB.

## Triage — 09/11/2026

### Semgrep

| Finding | Where | Decision |
|---|---|---|
| `request-host-used` (WARNING) | `gateway/snippets/proxy.conf` | **Fixed**: the gateway now sends `Host: <service name>` instead of forwarding the client's `Host` header |
| `dynamic-proxy-host` (WARNING) | `gateway/snippets/proxy.conf` | False positive, `# nosemgrep`: `$upstream_host` is always a literal set by the `location`, never request data |
| `cors-misconfiguration` (WARNING) | `frontend/mock/server.mjs` | False positive, `// nosemgrep`: dev-only mock, the origin is echoed only after an allow-list check |
| `github-actions-mutable-action-tag` (WARNING ×16) | `.github/workflows/ci.yml` | **Fixed**: actions pinned to commit SHAs |

No ERROR findings.

### Trivy

| Finding | Where | Decision |
|---|---|---|
| Jackson core/databind 2.22.2: CVE-2026-89407, -89425, -91776, -91777 (HIGH) | the six service images (from the Quarkus 3.39.5 BOM) | **Fixed**: `jackson-bom` 2.22.4 imported before the Quarkus BOM in `backend/pom.xml` |
| Alpine packages (brotli CRITICAL, curl, expat, musl, zlib HIGH) | `tamer/gateway` (base image built on Alpine 3.24.2) | **Fixed**: `apk upgrade --no-cache` in `gateway/Dockerfile` |
| Go stdlib CVEs in `/usr/local/bin/gosu` (1 CRITICAL, 25 HIGH) | `postgres:17.11-alpine3.24` | **Fixed**: own image `infra/postgres` without `gosu`. The entrypoint uses it only when started as root; our databases start as `70:70` |
| DS-0002 no `USER` (HIGH) | `gateway/Dockerfile` | **Fixed**: explicit `USER 101` (the base image user) |
| DS-0031 secret in `ENV` (CRITICAL) | `gateway/Dockerfile` | False positive on the name `AUTH_RATE_*`: renamed `LOGIN_RATE_LIMIT` / `LOGIN_RATE_BURST` |
| Trivy fs: Maven Central `429 Too Many Requests` | `backend/pom.xml` | Not a vulnerability: Trivy downloaded every parent pom. Now it reads `~/.m2` with `--offline-scan` |

### Trivy — 13/11/2026

The image scan failed on `main` with 27 HIGH findings in each of the six service images. They are
RHEL 9 packages of the base image `ubi9/openjdk-21-runtime:1.24`, whose fixes were published after
the last build of the base image (10/11/2026).

| Finding | Where | Decision |
|---|---|---|
| expat, libxml2, python3, python3-libs, libblkid, libmount, libsmartcols, libuuid (13 HIGH) | the six service images | **Fixed**: `microdnf -y update` in the runtime stage of every service Dockerfile |
| krb5-libs 1.21.1-10.el9_8.1: CVE-2026-107364, -112315 … -112323, -118380, -119079, -71199, -71200 (14 HIGH) | the six service images | **Accepted until 30/11/2026** in `.trivyignore`. The fix (`el9_9.2`) is only in the subscription RHEL repositories, not in the public UBI ones, so the update cannot install it. The package cannot be removed (libcurl, cyrus-sasl and cups-libs link it). Every CVE is in the KDC or kadmind, Kerberos server daemons not present in the images, or in Kerberos authentication, which no service uses. The entries expire so that the CI checks again |

`.trivyignore` holds only the krb5-libs entries above.

## Self-attack and P2 verification — 27/11/2026

### Self-attack (`scripts/security-check.sh`, SEC-1)

`scripts/security-check.sh` runs the plan's attack checklist against the gateway (`https://localhost:8443`)
and prints PASS/FAIL per check. Executed on **27/11/2026** against the complete P2 stack:

- **Result: 47/47 passed, 0 failed** (1 expected warning when run against the `docker-compose.test.yml` override because the override raises the auth rate limit for Locust/Bruno; real rate limits on both `/api/auth/*` and `/api/` are active in `docker-compose.yml`).
- **Covered vectors:**
  - SQL injection on auth and query parameters
  - JWT attacks (`alg:none`, invalid signature, tampered payload, garbage token, missing token) — rejected at the gateway (`INFRA-9` `auth_request`) and independently by every service (`INFRA-10` `AccessPolicyFilter`)
  - IDOR on foreign team, owned monster, run and encounter IDs (404, never 403)
  - Direct `/internal/*` and `/q/*` access through the gateway (404)
  - Forged battle outcomes and unexpected JSON fields (400)
  - Concurrent battle actions and starter choices
  - Oversized request body (`413 Payload Too Large`)
  - XSS payload handling, `X-Content-Type-Options: nosniff` and HTTP `Content-Security-Policy` headers
  - TLS 1.2+ enforcement (plain HTTP and legacy TLS refused, local CA verification, no trust-all)

Expired tokens, wrong issuer/audience, refresh reuse detection, login backoff and internal replay
are also covered by JUnit (`TokenVerificationTest`, `AuthorizeResourceTest`, `RefreshAndLogoutTest`,
`LoginBackoffTest`, `AccessPolicyTest` across all 6 services) and Bruno (`access/` + `services/*/internal/`).

### Audit log verification (`scripts/check-audit-log.sh`, SEC-3 & SEC-4)

Executed on **27/11/2026** against the running P2 stack:

- **Result: 18/18 passed, 0 failed.**
- Verified that gateway audit lines populate `user == player_id` from the verified `X-Auth-Subject`
  returned by `GET /internal/auth/authorize` (`INFRA-9`), that `auth` emits structured JSON audit
  lines for `register`, `login`, `refresh` and `logout`, and that no passwords, tokens or secrets
  appear in any log stream.

### Access policy, hardening and dependencies (P2)

- **Single access policy (`INFRA-10`):** `backend/security/access-policy.json` (52 rules) is kept in
  sync across all 6 services (`scripts/check-policy-sync.sh`) and enforced by both the gateway
  `auth_request` adapter and each service's `AccessPolicyFilter`.
- **Access-security Bruno suite (`TEST-5` / `CI-3`):** `17/17` component + `17/17` integration passed.
- **Database and container hardening (`INFRA-3`, `INFRA-4`):** PostgreSQL enforces `hostssl` in
  `pg_hba.conf`, each service uses an isolated DB network, application containers run with
  `read_only: true`, usernames are encrypted at rest with AES-256-GCM (`username-enc-key`) + HMAC
  blind index (`AUTH-8`), and encounter history enforces PostgreSQL append-only triggers plus a
  SHA-256 hash chain verified by `GET /api/history/verify` (`HI-2`).
- **Frontend dependency audit:** `npm audit --omit=dev` reports **0 vulnerabilities**; automated
  updates are configured in `.github/dependabot.yml` (`CI-4`).
