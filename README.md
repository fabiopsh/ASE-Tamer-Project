# Monster Tamers — ASE Lab Project

Advanced Software Engineering lab project (University of Pisa, A.Y. 2026/27).

Monster Tamers is a browser game built on microservices. A player registers, picks three starter monsters, builds a team and fights wild monsters and stage bosses in roguelike runs. Victories grant experience and new monsters for the collection.

## Architecture

```
Browser (Vite + React) ──HTTPS──▶ gateway (Nginx, :8443) ──HTTPS──▶ auth · players · monsters · teams · encounters · history
                                                                       │ each service has its own PostgreSQL (TLS)
```

- Six Quarkus (Java 21) services, one PostgreSQL database each, schema managed by Flyway.
- Nginx is the only entry point (`https://localhost:8443`). Internal endpoints (`/internal/*`) are never routed.
- HTTPS on every hop with a local CA. Certificates, JWT keys and passwords are generated at first start, so no secret is committed.
- OAuth2 token endpoint and JWT (RS256), checked locally by every service.

Documents:

- [`docs/plan/`](docs/plan/README.md): implementation plan and decision log (the reference for development)
- [`docs/architettura/architettura_backend.md`](docs/architettura/architettura_backend.md): architecture and game rules
- [`docs/Monster_Tamer_Functional_Requirements.pdf`](docs/Monster_Tamer_Functional_Requirements.pdf): official requirements

## Repository layout

```
backend/              Maven aggregator, one Quarkus module per service
  auth/ players/ monsters/ teams/ encounters/ history/
gateway/              Nginx configuration and Dockerfile
infra/init-secrets/   one-shot container that generates certificates, keys and passwords
frontend/             web UI (Vite + React)
scripts/              helper scripts (export the CA, prepare the API tests)
docs/                 plan, architecture, backlog, OpenAPI, test collections
docker-compose.yml    the whole backend
docker-compose.test.yml  test override: exposes each service on localhost
```

## Get Started

### 1. Prerequisites

| Tool | Version | Needed for |
|---|---|---|
| Docker Engine + Docker Compose | Engine ≥ 26, Compose ≥ 2.30 | running the backend (required) |
| Java and Maven | Java ≥ 21, Maven ≥ 3.9 | running the backend unit and service tests locally |
| Node.js | LTS (≥ 22) | the frontend and the Bruno CLI |

### 2. Build and run the backend

```bash
git clone <repository-url>
cd ASE-Tamer-Project
docker compose up --build
```

The first start builds the images (a few minutes) and generates every secret into the `secrets` Docker volume. Wait until all containers are `healthy`, for example with `docker compose up -d --build --wait`.

The gateway listens on **https://localhost:8443**. Check it with:

```bash
scripts/export-ca.sh                       # writes ./tamer-ca.crt (the local CA)
curl --cacert tamer-ca.crt https://localhost:8443/health
```

In the browser, open https://localhost:8443/health once and accept the certificate. You can also import `tamer-ca.crt` as a trusted authority.

### 3. Run the frontend

```bash
cd frontend
npm ci
npm run dev                                # http://localhost:5173
```

### 4. Run the tests

Unit and service tests (they need Docker, which starts a throw-away PostgreSQL):

```bash
mvn -f backend/pom.xml verify
```

API tests with [Bruno](https://www.usebruno.com/) against the running containers:

```bash
docker compose -f docker-compose.yml -f docker-compose.test.yml up -d --build --wait
scripts/test-env.sh                        # copies the CA and the test client secret (gitignored)
cd docs/tests/bruno
npx @usebruno/cli@4.3.0 run services -r --env component --cacert ../../../.test-secrets/ca.crt
```

See [docs/plan/10-testing.md](docs/plan/10-testing.md) for the integration and end-to-end runs.

### 5. Stop and reset

```bash
docker compose down        # stop, keep data
docker compose down -v     # stop and delete all data and generated secrets
```

To regenerate only the certificates and keys, stop the stack and remove the `tamer_secrets` volume. The databases keep their old passwords, though, so in practice use `down -v`.

## Ports

| Port | What | When |
|---|---|---|
| 8443 | Gateway (HTTPS) | always |
| 9001–9006 | auth, players, monsters, teams, encounters, history (HTTPS) | only with `docker-compose.test.yml`, bound to 127.0.0.1 |
| 5173 | Frontend dev server | `npm run dev` |
| 8081–8086 | Services in `quarkus:dev` mode (HTTP, Dev Services database) | local development |

## Troubleshooting

- **Port 8443 already in use**: stop the other process, or change the published port of `gateway` in `docker-compose.yml`.
- **`volume subpath` not supported**: update Docker Desktop (Compose ≥ 2.30, Engine ≥ 26).
- **curl on Windows says `CRYPT_E_NO_REVOCATION_CHECK` / error 60**: add `--ssl-no-revoke` to the curl command.
- **`init-secrets` exits with 255 on Windows**: shell scripts must have LF line endings (`.gitattributes` enforces it; run `git add --renormalize .` after pulling).
- **Certificate errors in curl or Bruno**: run `scripts/export-ca.sh` (or `scripts/test-env.sh`) again after a `down -v`, because the CA has changed.
- **A service stays `unhealthy`**: `docker compose logs <service>`. After changing database credentials or migrations during development, reset with `docker compose down -v`.
- **`429 Too Many Requests` on login**: the gateway allows 10 requests per minute per IP (burst 5) on `/api/auth/token` and `/api/auth/register`. The test override raises the limit.
