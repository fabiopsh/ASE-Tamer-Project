# Performance tests (Locust)

TEST-3 of [docs/plan/10-testing.md](../plan/10-testing.md). Locust drives the **gateway only**
(`https://localhost:8443`), as the requirements ask.

## Running

```bash
docker compose -f docker-compose.yml -f docker-compose.test.yml up -d --build --wait
scripts/test-env.sh                                # .test-secrets/ca.crt
python3 -m venv .venv && .venv/bin/pip install -r docs/locust/requirements.txt
CA_PATH=.test-secrets/ca.crt .venv/bin/locust -f docs/locust/locustfile.py --host https://localhost:8443 \
    --headless -u 50 -r 5 -t 3m --csv docs/locust/results/run
```

Use the test override: it raises the gateway rate limit on `/api/auth/*`, otherwise 50 users
registering from one IP get `429`. Run once on a warm stack (or discard the first run): right
after `docker compose up` the JVMs are cold and the first registrations can time out.
Without `--headless`, the web UI is on <http://localhost:8089>.

## What a user does

`on_start`: register a unique account, get a token, choose three starters, save a team.
Then, with 1–3 s between tasks:

| Task | Weight | Requests |
|---|---|---|
| browse catalogue | 5 | `GET /api/monsters`, `/api/monsters/{id}`, sprite, sometimes `/api/elements`, `/api/abilities` |
| view collection | 3 | `GET /api/collection`, `/api/collection/{owned_id}` |
| list encounters | 2 | `GET /api/encounters`, `/api/runs/current` |
| full battle | 2 | `POST /api/battles`, then `attack` until the battle ends (max 100 rounds) |
| history | 2 | `GET /api/history`, `/api/history/{encounter_id}` |
| validate team | 1 | `POST /api/teams/validate` |

After a defeat the run is reset, so the user chooses new starters and a new team.

## Results — 09/11/2026 (P1 prototype)

Machine: MacBook, Docker Desktop with 8 CPUs and 3.8 GB of memory for 13 containers.
50 users, spawn rate 5/s, 3 minutes. Raw data: [`results/run_stats.csv`](results/run_stats.csv),
[`results/run_failures.csv`](results/run_failures.csv), [`results/run_stats_history.csv`](results/run_stats_history.csv).

| Request | Count | Fail | p50 ms | p95 ms | p99 ms |
|---|---:|---:|---:|---:|---:|
| `POST /api/auth/register` | 50 | 0 | 4400 | 8900 | 10000 |
| `POST /api/auth/token` | 50 | 0 | 5000 | 8400 | 9400 |
| `POST /api/players/me/starters` (choose) | 55 | 0 | 490 | 1000 | 1400 |
| `POST /api/teams` | 55 | 0 | 280 | 840 | 1600 |
| `POST /api/teams/validate` | 180 | 2 | 200 | 2800 | 3200 |
| `POST /api/battles` (start) | 355 | 21 | 490 | 4800 | 6300 |
| `POST /api/battles/current/actions` | 4635 | 0 | 43 | 850 | 2900 |
| `GET /api/monsters` | 914 | 0 | 74 | 920 | 1400 |
| `GET /api/monsters/{id}` | 914 | 0 | 50 | 730 | 1300 |
| `GET /images/monsters/{file}` | 914 | 0 | 47 | 590 | 1200 |
| `GET /api/collection` | 566 | 0 | 87 | 1200 | 2700 |
| `GET /api/encounters` | 757 | 0 | 93 | 1100 | 2400 |
| `GET /api/history` | 369 | 0 | 160 | 1200 | 2300 |
| `GET /api/history/{id}` | 316 | 0 | 76 | 960 | 1900 |
| **All** | **11667** | **23 (0.2%)** | **61** | **1100** | **4100** |

Throughput: about 65 requests/s overall, 26 battle rounds/s.

### Analysis

- **Battle rounds are cheap** (p50 43 ms): the battle state is in memory in `encounters` and the
  engine is pure Java, so the most frequent request is also one of the fastest.
- **Login and registration are the slowest requests** (p50 4–5 s while 50 users start). bcrypt
  is deliberately expensive and runs once per registration and once per login. This is
  the intended cost against password guessing, and it only affects `on_start`: a real player logs
  in once per session. If needed, the bcrypt work could move to a worker pool sized on the CPUs.
- **All 23 failures are `503 DEPENDENCY_UNAVAILABLE`** while the load was highest (18:09–18:10):
  `encounters` → `teams` (`GET /internal/teams/{id}` when a battle starts) and `encounters` →
  `monsters` (`POST …/xp` when a battle ends) exceeded their timeout. After a few timeouts the
  circuit breakers opened, so battle starts failed fast with 503 instead of waiting. The client
  sees a clean error and can retry. XP awards were postponed (log: `xp postponed`): they stay in the
  encounters outbox and are retried the next time the player touches the run (lazy recovery,
  P-20), so no progress is lost. The resilience design works as intended.
- **Tail latency (p95 ≈ 1 s, p99 ≈ 4 s) comes from CPU contention**, not from one slow service:
  13 JVM and Postgres containers share 8 CPUs with Locust itself, and `teams` logged one
  Vert.x event loop blocked for 2 s. On separate hosts, or with fewer containers per host, the tail
  should shrink. To check in P2: run Locust from another machine, and raise the
  `teams`/`monsters` client timeouts slightly, or add a `@Bulkhead`, if the 503s persist.

Run the test again on the final P2 system (refresh tokens, encryption at rest) and compare it with
this baseline in the report.
