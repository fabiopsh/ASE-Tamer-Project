# Monster Tamers — ASE Lab Project

Progetto di laboratorio di Advanced Software Engineering (UniPi, A.A. 2026/27): gioco di collezione di mostri con backend distribuito a microservizi e REST API.

## Avvio rapido

Serve solo Docker (Desktop) con Compose.

```bash
cp .env.example .env        # opzionale: senza .env si usano password di sviluppo
docker compose up -d --build
```

Poi apri **https://localhost:8443** (certificato self-signed: il browser chiede di accettarlo). `http://localhost:8080` reindirizza su HTTPS.

Il primo build scarica le dipendenze Maven e richiede qualche minuto; i successivi usano la cache.

```bash
docker compose ps            # stato e healthcheck
docker compose logs -f encounters
docker compose down          # ferma (i dati restano nel volume pgdata)
docker compose down -v       # ferma e cancella dati, chiavi e certificato
```

## Architettura

```
browser ──HTTPS──▶ gateway (nginx: TLS, frontend, /api/* → servizi)
                     ├─ auth        account, login, JWT            US-01 02 05
                     ├─ players     profilo, stage, starter        US-03 04 06 07
                     ├─ monsters    catalogo, collezione, XP       US-09 10 11 12 13
                     ├─ teams       team e validazione             US-14 15 16
                     ├─ encounters  bacheca, battaglia, ricompense US-17 18 19 20
                     └─ history     storico con catena di hash     US-23 24 25
                   postgres: un database e un utente per servizio
```

- Backend: **Quarkus 3.27 / Java 21**, REST + JSON, Hibernate Panache, PostgreSQL 16. Comunicazione tra servizi via REST interna (`/internal/*`, token condiviso, non esposta dal gateway).
- Frontend (US-28): **HTML + CSS + JavaScript senza framework né build**, servito dal gateway. Nessuna logica di gioco nel client.
- Sicurezza: HTTPS, JWT RS256, bcrypt, rate limit, ownership 403, storico anti-manomissione → [analisi di sicurezza](docs/architettura/sicurezza.md).

Dettagli: [contratto API e regole di gioco](docs/architettura/api-contract.md). OpenAPI di ogni servizio su `https://localhost:8443/api/<servizio>/swagger-ui`.

## Il gioco

1. Registrati: ricevi un pool di 10 mostri common casuali e ne scegli 3.
2. Componi fino a 5 team da 1–3 mostri (l'ordine è l'ordine di battaglia).
3. Ogni stage ha 3 encounter wild e un guardian. La battaglia è simulata dal server e riprodotta con animazioni.
4. Vittoria: XP a tutto il team, possibile cattura del nemico. Battere il guardian fa avanzare di stage (10 stage, poi il gioco è completato).
5. Lo storico conserva ogni battaglia, rivedibile, con verifica di integrità.

## Test

| Tipo | Dove | Come |
|---|---|---|
| Unit (JUnit 5, 179) | `backend/*/src/test` | girano a ogni build Docker (`mvn package`) |
| API end-to-end (pytest, 132) | `tests/api` | `pytest tests/api -v` con lo stack avviato |
| Prestazioni (Locust) | `tests/performance` | vedi [tests/README.md](tests/README.md) |

## Backup dei dati

```bash
docker compose exec postgres pg_dumpall -U postgres > backup.sql
```

## Struttura

```
backend/<servizio>/   un servizio Quarkus per cartella (pom.xml, Dockerfile, src/, README)
backend/monsters/seed generato da tools/monster-gen: 100 mostri, 28 abilità, sprite 64×64
frontend/             UI del gioco
gateway/              nginx (TLS, reverse proxy, header di sicurezza)
infra/                script di inizializzazione (database, chiavi, certificato)
tests/                test API e di carico
tools/monster-gen/    pipeline di generazione dei mostri (SDXL locale)
docs/                 consegna, design, architettura
```
