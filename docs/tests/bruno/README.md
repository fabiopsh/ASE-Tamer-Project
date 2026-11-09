# Monster Tamers — API tests (Bruno)

Component, integration and end-to-end tests of [docs/plan/10-testing.md](../../plan/10-testing.md).
The exported JSON (Postman v2.1, deliverable) is in [`../json/`](../json/).

## Running the API tests

```bash
# from the repository root
docker compose -f docker-compose.yml -f docker-compose.test.yml up -d --build --wait
scripts/test-env.sh            # writes .test-secrets/ca.crt and docs/tests/bruno/.env (both gitignored)

cd docs/tests/bruno
npm ci                         # Bruno CLI 4.3.0, pinned in package.json
npm run test:component         # every service on its own port (9001…9006), public + internal
npm run test:integration       # the public requests again, through the gateway (8443)
npm run test:e2e               # core loop through the gateway
npm test                       # the three runs above, in this order
npm run export                 # re-export ../json/ (do it before each delivery)
```

The runs need a fresh-enough stack, not an empty one: every folder registers its own random users.

| Run | Environment | Folders |
|---|---|---|
| component | `component` (`authUrl=https://localhost:9001`, …) | `services/**` |
| integration | `integration` (every `*Url=https://localhost:8443`) | `services/<svc>/00-setup` + `services/<svc>/public` |
| e2e | `integration` | `e2e/` |

`internal/` folders run only in the component run: the gateway answers 404 for `/internal/*`
(checked in `e2e/15-internal-not-exposed.bru`).

## Layout

```
bruno.json  package.json  export-json.js  .env.example
environments/component.bru  environments/integration.bru
services/<svc>/
  00-setup/            public endpoints only, so it also works through the gateway
  public/              /api/* requests (valid + invalid), use only variables from 00-setup
  internal/
    00-setup/          a separate fresh player + service token (tests client)
    *.bru              /internal/* requests (valid + invalid)
e2e/                   01-register … 14-collection-after-battle, then the negative block
```

Folder order is fixed by `folder.bru` (`seq`): `00-setup` → `public` → `internal`. `internal/`
uses its own player because internal requests change state (archive, reset, new run) that the
public requests must not see. Requests inside a folder run by `meta.seq`.

## Shared setup snippets

**Player token** (every `00-setup`):

```
POST {{authUrl}}/api/auth/register     {"username":"{{setupUsername}}","password":"pikachu123"}
  pre-request: bru.setVar("setupUsername", "t_" + Math.random().toString(16).slice(2, 12));
  tests:       bru.setVar("playerId", res.getBody().player_id);
POST {{authUrl}}/api/auth/token        grant_type=password&username={{setupUsername}}&password=pikachu123
  tests:       bru.setVar("playerToken", res.getBody().access_token);
```

**Run with starters** (monsters, teams, encounters, history):

```
GET  {{playersUrl}}/api/players/me/starters      → starter1..3 (first three of the pool), starterPool
POST {{playersUrl}}/api/players/me/starters      {"monster_ids":[starter1, starter2, starter3]}
  tests: runId, owned1..3
POST {{teamsUrl}}/api/teams                      {"name":"Main","members":[owned1, …]} → teamId
```

**Service token** (`internal/00-setup` only, needs `TESTS_CLIENT_SECRET` from `.env`):

```
POST {{authUrl}}/internal/auth/token   grant_type=client_credentials&client_id=tests&client_secret={{process.env.TESTS_CLIENT_SECRET}}
  tests: bru.setVar("serviceToken", res.getBody().access_token);
```

**Other player** (monsters, teams): a second player with its own starters (`otherOwned1..3`) and,
in teams, a team (`foreignTeamId`). Used to check that foreign resources return 404 (P-11).

**Loops** use `bru.setNextRequest("<request name>")` with a guard: the history setup flees until
the battle ends (< 50 rounds), e2e attacks until it ends (< 100 rounds), teams fills up to five teams.

## Writing requests

- Name: `<METHOD> <path> - <valid|invalid: reason>`.
- URL with the per-service variable (`{{monstersUrl}}/api/monsters`), never a fixed host.
- Assert the status **and** the body shape; for errors also `error` (the error code).
- Battles: assert invariants (HP in range, allowed outcomes), never exact damage.
- No dependence on other service folders: each folder creates its own data.

## Coverage matrix (one valid + one invalid per endpoint)

| Service | Endpoint | Valid | Invalid |
|---|---|---|---|
| auth | `POST /api/auth/register` | 01 | 02 duplicate, 03 short password |
| auth | `POST /api/auth/token` | 04 | 05 wrong password, 06 unknown user (same error), 07 unsupported grant |
| auth | `POST /internal/auth/token` | internal 01 | internal 02 wrong secret |
| players | `GET /api/players/me` | 01 | 02 no token |
| players | `GET /api/players/me/starters` | 03 | 04 no token |
| players | `POST /api/players/me/starters` | 08 | 05 duplicate, 06 two starters, 07 outside pool, 09 second choice |
| players | `POST /internal/players` | internal 01 (idempotent) | internal 02 player token |
| players | `POST /internal/players/{pid}/new-run` | internal 03, 04 retried | internal 05 player token |
| monsters | `GET /api/monsters` | 01 | 02 bad filter |
| monsters | `GET /api/monsters/{id}` | 03 | 04 unknown id |
| monsters | `GET /api/abilities`, `/{id}` | 05, 07 | 06 no token, 08 unknown id |
| monsters | `GET /api/elements` | 09 | 10 no token |
| monsters | `GET /images/monsters/{file}` | 11 | 12 missing |
| monsters | `GET /api/collection` | 13 | 14 bad page |
| monsters | `GET /api/collection/{owned_id}` | 15 | 16 unknown, 17 foreign |
| monsters | `GET /internal/monsters` | internal 01 | internal 02 player token |
| monsters | `GET /internal/monsters/{id}/stats` | internal 03 | internal 04 level 51 |
| monsters | `POST /internal/collections/{pid}/starters` | internal 05, 06 retried | internal 15 two ids |
| monsters | `POST /internal/collections/{pid}/monsters` | internal 07, 08 retried (one instance) | internal 16 bad source |
| monsters | `POST /internal/collections/{pid}/xp` | internal 09, 10 retried | internal 17 negative xp |
| monsters | `GET /internal/collections/{pid}/instances` | internal 11 | internal 12 player token |
| monsters | `POST /internal/collections/{pid}/archive` | internal 13, 14 retried | internal 18 player token |
| teams | `GET /api/teams` | 01 | 02 no token |
| teams | `POST /api/teams/validate` | 03 | 04 duplicate member |
| teams | `POST /api/teams` | 05, 15 up to five | 06 four members, 13 foreign monster, 16 sixth team |
| teams | `GET /api/teams/{id}` | 07 | 08 unknown, 14 foreign |
| teams | `PUT /api/teams/{id}` | 09 | 10 foreign |
| teams | `DELETE /api/teams/{id}` | 11 | 12 unknown |
| teams | `GET /internal/teams/{id}` | internal 01 | internal 02 player token |
| teams | `POST /internal/teams/{pid}/reset` | internal 03 | internal 04 player token |
| encounters | `GET /api/runs/current` | 01 | 22 no active run |
| encounters | `GET /api/runs` | 02 | 03 bad size |
| encounters | `GET /api/runs/{run_id}` | 04 | 05 unknown |
| encounters | `GET /api/encounters` | 06 | 09 no token |
| encounters | `GET /api/encounters/{id}` | 07 | 08 boss locked |
| encounters | `POST /api/battles` | 13 | 11 unavailable template, 12 unknown team, 14 start twice |
| encounters | `GET /api/battles/current` | 15 | 20 no battle |
| encounters | `POST /api/battles/current/actions` | 18 attack | 10 no battle, 16 unknown field, 17 switch to active |
| encounters | `POST /api/runs/current/abandon` | 19 | 21 no run |
| encounters | `POST /api/runs` (new run) | — (needs a won run: JUnit `BattleApiTest.stageEightBossWinsTheRunAndANewRunCanStart`) | 23 run not won |
| encounters | `POST /internal/runs` | internal 01 (idempotent) | internal 02 second active, 03 missing field, 04 player token |
| history | `GET /api/history` | 01, 03 outcome filter | 02 no token, 04 bad outcome, 05 bad size |
| history | `GET /api/history/{id}` | 06 | 07 unknown, 08 no token, internal 11 foreign |
| history | `POST /internal/history` | internal 01, 02 duplicate (idempotent) | internal 03 bad outcome, 04 missing wild, 05 unknown field, 06 player token, 07 no token |

Not covered by Bruno: switching to a KO'd slot (needs a battle in a precise state; covered by
`BattleTest` in the engine unit tests).
