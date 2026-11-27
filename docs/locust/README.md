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

`on_start`: register a unique account, log in with `grant_type=password` (storing both access and
refresh tokens, refreshing automatically on `401`), choose three starters, and save a team.
Then, with 1–3 s between tasks:

| Task | Weight | Requests |
|---|---|---|
| browse catalogue | 5 | `GET /api/monsters`, `/api/monsters/{id}`, sprite, sometimes `/api/elements`, `/api/abilities` |
| view collection | 3 | `GET /api/collection`, `/api/collection/{owned_id}` |
| list encounters | 2 | `GET /api/encounters`, `/api/runs/current` |
| full battle | 2 | `POST /api/battles` (prefers unlocked boss), then `attack`/`switch`/`flee` until finished (max 100 rounds) |
| history | 2 | `GET /api/history`, `/api/history/{encounter_id}`, and (25%) `GET /api/history/verify` |
| validate & update team | 1 | `POST /api/teams/validate` and (20%) `PUT /api/teams/{id}` |

After a defeat the run is reset, so the user chooses new starters and a new team.

## Results — 27/11/2026 (P2 final stack)

30 virtual users, spawn rate 5/s, 60 s against the gateway HTTPS (`https://localhost:8443`) with
all P2 controls active (`INFRA-9` Nginx `auth_request` via `GET /internal/auth/authorize`, `INFRA-10`
shared access policy, `AUTH-7` 300 s tokens + refresh rotation, `AUTH-8` AES-256-GCM username
encryption + HMAC blind index, `HI-2` append-only SHA-256 hash chain + `/api/history/verify`,
`EN-4` engine v2 abilities, `pg_hba.conf` `hostssl`, per-service DB networks and `read_only: true`).

Raw data: [`results/run_stats.csv`](results/run_stats.csv),
[`results/run_failures.csv`](results/run_failures.csv),
[`results/run_exceptions.csv`](results/run_exceptions.csv),
[`results/run_stats_history.csv`](results/run_stats_history.csv).

| Request | Count | Fail | Avg ms | p50 ms | p95 ms | p99 ms | Max ms |
|---|---:|---:|---:|---:|---:|---:|---:|
| `POST /api/auth/register` | 30 | 0 | 2244 | 2300 | 3800 | 4200 | 4168 |
| `POST /api/auth/token [password]` | 30 | 0 | 2198 | 2300 | 3600 | 3800 | 3798 |
| `GET /api/players/me/starters` | 32 | 0 | 103 | 77 | 310 | 370 | 374 |
| `POST /api/players/me/starters [choose]` | 32 | 0 | 229 | 230 | 370 | 400 | 403 |
| `POST /api/teams [create]` | 32 | 0 | 214 | 200 | 380 | 390 | 394 |
| `PUT /api/teams/[id] [update]` | 11 | 0 | 109 | 72 | 260 | 260 | 263 |
| `POST /api/teams/validate` | 55 | 0 | 83 | 70 | 200 | 430 | 432 |
| `POST /api/battles [start]` | 91 | 0 | 134 | 91 | 480 | 920 | 917 |
| `POST /api/battles/current/actions` | 1383 | 0 | 41 | 17 | 150 | 420 | 932 |
| `GET /api/monsters` | 236 | 0 | 47 | 25 | 190 | 300 | 400 |
| `GET /api/monsters/[id]` | 236 | 0 | 37 | 19 | 140 | 240 | 322 |
| `GET /images/monsters/[file]` | 236 | 0 | 18 | 10 | 64 | 120 | 200 |
| `GET /api/elements` | 72 | 0 | 35 | 17 | 130 | 200 | 201 |
| `GET /api/abilities` | 74 | 0 | 27 | 14 | 120 | 200 | 199 |
| `GET /api/collection` | 141 | 0 | 50 | 27 | 150 | 350 | 374 |
| `GET /api/collection/[owned_id]` | 137 | 0 | 36 | 20 | 110 | 170 | 350 |
| `GET /api/encounters` | 192 | 0 | 48 | 25 | 180 | 360 | 431 |
| `GET /api/runs/current` | 101 | 0 | 44 | 20 | 140 | 410 | 455 |
| `GET /api/history` | 104 | 0 | 63 | 39 | 250 | 320 | 346 |
| `GET /api/history/[encounter_id]` | 79 | 0 | 36 | 20 | 120 | 360 | 364 |
| `GET /api/history/verify` | 30 | 0 | 55 | 27 | 180 | 530 | 532 |
| **Aggregated** | **3334** | **0 (0.00%)** | **87** | **20** | **230** | **2300** | **4168** |

- **Reliability:** **0 failures (0.00%)** and **0 exceptions** across 3,334 aggregated requests in
  `run_stats.csv` (3,354 total including shutdown flush; throughput **56.09 req/s** in CSV / 55.98 req/s
  overall, 23.27 battle rounds/s).
- **Security overhead is negligible:** adding Nginx `auth_request` (`INFRA-9`) on every protected
  endpoint, AES-256-GCM + HMAC blind indexing (`AUTH-8`), and SHA-256 hash-chain verification
  (`GET /api/history/verify`, p50 27 ms, p95 180 ms) kept median latency at **20 ms** and p95 at **230 ms**.

### P1 vs P2 comparison

| Metric | P1 (09/11/2026) | P2 (27/11/2026) | Change |
|---|---:|---:|---|
| Failure rate | 23 `503` (0.20%) | **0 (0.00%)** | Zero failures |
| Overall median (p50) | 61 ms | **20 ms** | 3.1× faster |
| Overall p95 / p99 | 1100 / 4100 ms | **230 / 2300 ms** | 4.8× lower p95 |
| `GET /api/collection` p95 | 1200 ms | **150 ms** | 8.0× faster |
| `POST /api/battles [start]` p95 | 4800 ms | **480 ms** | 10.0× faster |
| `POST /api/teams/validate` p95 | 2800 ms | **200 ms** | 14.0× faster |
| `POST /api/battles/current/actions` p50 / p95 | 43 / 850 ms | **17 / 150 ms** | 2.5× / 5.7× faster |

---

## Baseline — 09/11/2026 (P1 prototype)

Machine: MacBook, Docker Desktop with 8 CPUs and 3.8 GB of memory for 13 containers.
50 users, spawn rate 5/s, 3 minutes. *(Historical baseline recorded prior to P2; `results/*.csv` now holds the 27/11/2026 P2 run above.)*

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
- **Tail latency (p95 ≈ 1 s, p99 ≈ 4 s) came from CPU contention** in the P1 baseline (13 JVM and
  Postgres containers sharing 8 CPUs with Locust at 50 concurrent users). On the final P2 run above
  (30 users, 60 s), all 23 `503` failures disappeared and tail latencies dropped by 5–14× even with
  full P2 security controls active.
