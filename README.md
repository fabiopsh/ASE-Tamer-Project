# Monster Tamers

[![CI](https://github.com/fabiopsh/ASE-Tamer-Project/actions/workflows/ci.yml/badge.svg?branch=main)](https://github.com/fabiopsh/ASE-Tamer-Project/actions/workflows/ci.yml)

A roguelike monster-taming browser game built on microservices.
Advanced Software Engineering lab project, University of Pisa, A.Y. 2026/27.

## Group

**Fantastic 4**

| Name | Surname | Email |
|---|---|---|
| Giacomo | Cesaretti | giacomocesaretti03@gmail.com |
| Federico | Lombardi | f.lombardi26@studenti.unipi.it |
| Fabio | Piscitelli | f.piscitelli4@studenti.unipi.it |
| Francesco | Polperio | f.polperio@studenti.unipi.it |

Monster Tamers is a browser game built on microservices. A player registers, picks three starter monsters, builds a team and fights wild monsters and stage bosses in roguelike runs. Victories grant experience and new monsters for the collection.
## Quick start

You only need **Docker** (Docker Desktop, or Engine ≥ 26 with Compose ≥ 2.30).

```bash
git clone https://github.com/fabiopsh/ASE-Tamer-Project.git
cd ASE-Tamer-Project
docker compose up -d --build --wait                               # backend (API on https://localhost:8443)
docker compose -f docker-compose.frontend.yml up -d --build       # frontend (http://localhost:8080)
```

1. Open **https://localhost:8443/health** once and accept the certificate (*Advanced → Proceed*). It is signed by a CA that is generated locally. To skip the warning, run `scripts/export-ca.sh` and add `tamer-ca.crt` to your trusted authorities.
2. Open **http://localhost:8080** and create an account.

The first build takes a few minutes, later starts take about 30 seconds.

```bash
docker compose -f docker-compose.frontend.yml down   # stop the frontend
docker compose down                                  # stop the backend, keep your progress
docker compose down -v                               # stop and delete all data and generated secrets
```

## Screenshots

| | |
|---|---|
| ![Camp](docs/screenshots/dashboard.png) | ![Encounters](docs/screenshots/encounters.png) |
| **Camp**: run progress across the 8 stages | **Encounters**: pick a place and a team |
| ![Battle](docs/screenshots/battle.png) | ![Team editor](docs/screenshots/team-editor.png) |
| **Battle**: turn by turn, computed by the server | **Teams**: up to three monsters, in battle order |

## The game

1. Register and pick **3 starters** from a pool of 10.
2. Build a **team** of 1–3 monsters.
3. Choose an **encounter** in the current stage and fight with *Attack*, *Switch* or *Flee*.
4. When you win, the wild monster **joins your collection** and your team gains **XP**.
5. Two wins unlock the **stage boss**. Beat it to reach the next stage. There are 8 stages in all.
6. A defeat ends the **run**: you pick new starters and start again. Past runs and battles stay in your history.

The catalogue has 100 monsters across several elements and rarities, each with its own ability.

## What we built

- **Six microservices** (Quarkus, Java 21), one PostgreSQL database each.
- **Headless backend**: an Nginx gateway is the only entry point (`https://localhost:8443`) and serves just the API. Any client can use it. Browsers are allowed by CORS from a configurable list of origins.
- **Separate frontend**: the web app runs in its own container and stack, and talks to the backend only through the public API.
- **Server-side battles**: the client only sends the chosen action. Rewards go through idempotent service-to-service calls with an outbox.
- **Security by default**: HTTPS on every hop, including the databases, with a local CA. OAuth2 tokens are JWTs (RS256), and every secret is generated at first start, so none is committed. Containers run as non-root with no capabilities, and login is rate-limited.
- **Web client**: React, TypeScript and Vite, with a custom pixel-art theme.
- **Quality**: unit and service tests, API tests (Bruno: component, integration, end-to-end), load tests (Locust), and CI with Semgrep and Trivy.

## Architecture

![Architecture diagram](docs/architecture.svg)

The diagram shows the planned architecture. All six services and the gateway are built; the features still missing are listed in [Implementation status](#implementation-status). A more detailed description (components, game rules, decisions) is in [`docs/architettura/architettura_backend.md`](docs/architettura/architettura_backend.md).

In the backend only the gateway publishes a port. Services and databases run on an internal Docker network, and `/internal/*` endpoints are never routed. Allowed browser origins are set with `CORS_ALLOWED_ORIGINS` on the gateway (default: `localhost` on ports 8080, 5173 and 4173).

## OpenAPI specifications

One OpenAPI file for the gateway (the public API, the one a client uses) and one for each microservice, all in [`docs/openapi/`](docs/openapi/):

| Component | File |
|---|---|
| API Gateway (public API) | [`gateway.yaml`](docs/openapi/gateway.yaml) |
| auth | [`auth.yaml`](docs/openapi/auth.yaml) |
| players | [`players.yaml`](docs/openapi/players.yaml) |
| monsters | [`monsters.yaml`](docs/openapi/monsters.yaml) |
| teams | [`teams.yaml`](docs/openapi/teams.yaml) |
| encounters | [`encounters.yaml`](docs/openapi/encounters.yaml) |
| history | [`history.yaml`](docs/openapi/history.yaml) |

## Implementation status

Phase 1 (prototype) is complete. Phase 2 (final delivery, 15/12/2026) is in progress.

**Working**
- Registration and login (OAuth2 password grant, JWT RS256), service-to-service tokens (`client_credentials`).
- Player profile, pool of 10 starters, choice of 3 starters at the start of each run.
- Catalogue of 100 monsters with sprites, abilities and elements; personal collection with levels and XP.
- Teams of 1–3 monsters with server-side validation (ownership, run, limit of five).
- Roguelike runs with 8 stages, wild encounters, stage bosses, abandon and defeat reset.
- Turn-based battle engine on the server (`attack`, `switch`, `flee`), elemental chart, simple abilities, rewards and XP through idempotent internal calls (outbox).
- History of every finished battle with the full turn log.
- HTTPS on every hop (local CA), a database per service, secrets generated at first start.
- Web UI covering the whole core loop: login, starters, catalogue, collection, teams, encounters, battle, runs, history.
- Tests: unit and service tests per service, Bruno API tests (component, integration, end-to-end), Locust load tests, CI with build, tests, Semgrep and Trivy.

**Still missing (planned for Phase 2)**
- Refresh token and logout (only the password grant exists).
- Encryption at rest (encrypted username, hashed refresh tokens).
- Complex abilities in the battle engine (`heal`, `reflect`, `stun` and similar are placeholders without effect).
- Scheduled sweep of expired battles and a more robust reset saga (today recovery is lazy: it happens when the player next touches the run).
- Integrity checks on the history (US-25).
- Optional stories: profile editing (US-04) and monster nicknames (US-13).

**Known limit.** If `encounters` restarts, battles in progress are closed as `interrupted` without reward and without losing the run. The service cannot be replicated on several instances.

## Additional information

Documents:

| What | Where |
|---|---|
| `auth` | Accounts, passwords (bcrypt), OAuth2 token endpoint, JWT signing |
| `players` | Player profile, starter pool, current run |
| `monsters` | Catalogue, abilities, sprites, collections, levels and XP |
| `teams` | Teams and their validation |
| `encounters` | Runs, stages, encounters, battle engine |
| `history` | Permanent record of every finished battle |
| `frontend` | Static web app (separate stack, calls the gateway from the browser) |

## Repository layout

```
backend/              Maven aggregator, one Quarkus module per service
  auth/ players/ monsters/ teams/ encounters/ history/
gateway/              Nginx configuration and Dockerfile
infra/init-secrets/   one-shot container that generates certificates, keys and passwords
frontend/             web UI (Vite + React)
scripts/              helper scripts (export the CA, prepare the API tests, security self-attack)
.github/workflows/    CI: build, tests, Semgrep, Trivy (copy in docs/ci/ci.yml)
docs/                 plan, architecture, backlog, OpenAPI, test collections
docker-compose.yml    the whole backend
docker-compose.test.yml  test override: exposes each service on localhost
```

## Running instructions

### 1. Prerequisites

| Tool | Version | Needed for |
|---|---|---|
| Docker Engine + Docker Compose | Engine ≥ 26, Compose ≥ 2.30 | running the backend (required) |
| Java and Maven | Java ≥ 21, Maven ≥ 3.9 | running the backend unit and service tests locally |
| Node.js | LTS (≥ 22) | the frontend and the Bruno CLI |

### 2. Build and run the backend

```bash
mvn -f backend/pom.xml verify                     # unit and service tests (Java 21, Maven, Docker)
```

API tests (Bruno) and load tests (Locust) run against the test stack. See [docs/tests/bruno](docs/tests/bruno/README.md) and [docs/locust](docs/locust/README.md).

## More

| | |
|---|---|
| Architecture and game rules | [docs/architettura](docs/architettura/architettura_backend.md) |
| Implementation plan and decisions | [docs/plan](docs/plan/README.md) |
| OpenAPI specs | [docs/openapi](docs/openapi/) |
| Security analysis | [docs/security](docs/security/README.md) |
| Backlog and acceptance criteria | [docs/backlog](docs/backlog/acceptance_criteria.md) |
| Frontend development (hot reload, mock server) | [frontend/README.md](frontend/README.md) |

<details>
<summary>Troubleshooting</summary>

- **Port 8443 in use**: stop the other process, or change the gateway port in `docker-compose.yml`.
- **Port 8080 in use**: start the frontend with `FRONTEND_PORT=8081`, and add `http://localhost:8081` to `CORS_ALLOWED_ORIGINS`.
- **The app says *Cannot reach the game server***: the backend is not running, or the certificate of https://localhost:8443 was not accepted in this browser.
- **`volume subpath` not supported**: update Docker (Compose ≥ 2.30).
- **`init-secrets` exits with 255 on Windows**: scripts need LF line endings. Run `git add --renormalize .`.
- **Certificate errors after `down -v`**: the CA was regenerated. Export it again with `scripts/export-ca.sh`.
- **A service stays `unhealthy`**: check `docker compose logs <service>`.
- **`429 Too Many Requests` on login**: login and registration allow 10 requests per minute per IP.

```bash
cd frontend
npm ci
npm run dev                                # http://localhost:5173
```

### 4. Stop and reset

```bash
docker compose down        # stop, keep data
docker compose down -v     # stop and delete all data and generated secrets
```

To regenerate only the certificates and keys, stop the stack and remove the `tamer_secrets` volume. The databases keep their old passwords, though, so in practice use `down -v`.

## Testing instructions

Once the backend is running (see above), the quickest manual check is the frontend: register, pick 3 starters, build a team, fight an encounter. The automated tests are:

### Unit and service tests

Unit and service tests (they need Docker, which starts a throw-away PostgreSQL):

```bash
mvn -f backend/pom.xml verify
```

### API tests

API tests with [Bruno](https://www.usebruno.com/) against the running containers (Node.js 20+ needed):

```bash
docker compose -f docker-compose.yml -f docker-compose.test.yml up -d --build --wait
scripts/test-env.sh                        # copies the CA and the test client secret (gitignored)
cd docs/tests/bruno
npm ci                                     # Bruno CLI, pinned version
npm run test:component                     # each service on its own port (9001…9006)
npm run test:integration                   # the public requests through the gateway (8443)
npm run test:e2e                           # core loop through the gateway
```

`npm test` runs all three. The exported collections (JSON) are in [docs/tests/json/](docs/tests/json/),
regenerated with `npm run export`. Details, layout and the coverage matrix:
[docs/tests/bruno/README.md](docs/tests/bruno/README.md).

### Performance tests

Performance tests with [Locust](https://locust.io/), against the gateway only (same test stack):

```bash
pip install -r docs/locust/requirements.txt
CA_PATH=.test-secrets/ca.crt locust -f docs/locust/locustfile.py --host https://localhost:8443 \
    --headless -u 50 -r 5 -t 3m --csv docs/locust/results/run
```

Results and their analysis: [docs/locust/README.md](docs/locust/README.md).

### Security scans

Semgrep, Trivy and a self-attack script: [docs/security/README.md](docs/security/README.md).

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
