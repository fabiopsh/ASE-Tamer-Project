# Security analysis and self-attack

CI-2 and SEC-1 of [docs/plan/11-ci-security.md](../plan/11-ci-security.md). The CI runs Semgrep and
Trivy on every push and pull request ([.github/workflows/ci.yml](../../.github/workflows/ci.yml)).

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

`.trivyignore` is empty. **To verify** on a machine with enough memory: the image scan after the
fixes above, and `trivy fs` with the offline mode.

## Self-attack (SEC-1)

`scripts/security-check.sh` runs the checklist of the plan against the gateway and prints PASS/FAIL
per check: SQL injection, JWT (`alg:none`, wrong signature, altered payload, garbage, missing),
IDOR on team/owned/run/encounter ids, `/internal/*` and `/q/*` through the gateway, forged battle
results, concurrent actions and starter choices, oversized body, XSS payloads, TLS (plain HTTP,
TLS 1.1, untrusted CA, no trust-all) and brute force (429). Expired tokens, wrong issuer/audience
and internal replay are covered by JUnit (`TokenVerificationTest`) and Bruno (`monsters/internal/08`, `10`).

Results: not run yet. Record the output here and in the report before 15/12.
