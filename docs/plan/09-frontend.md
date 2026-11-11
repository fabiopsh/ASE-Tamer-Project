# 09 — Frontend

Minimal React UI that lets a player perform the core loop without technical tools. User story: **US-28**.

Constraints from the requirements (§2):

- runs **outside** docker compose and is not attached to the backend Docker network;
- talks **only** to the gateway over HTTPS (`https://localhost:8443`);
- contains **no relevant business logic**: it displays what the backend returns and sends user intents.

## Stack

| Item | Choice |
|---|---|
| Build | Vite |
| UI | React + TypeScript (types mirror the API DTOs) |
| Routing | `react-router-dom` |
| State | React context for session + local component state; no state library |
| HTTP | `fetch` wrapper (`src/api/client.ts`) |
| Styling | Plain CSS (one file per page or CSS modules); sprites with `image-rendering: pixelated` |
| Versions | Exact versions in `package.json`, `package-lock.json` committed, `npm ci` in docs and CI |

## Layout

```
frontend/
  index.html
  vite.config.ts            dev server on 5173; CSP meta injected only in build (see Security)
  .env.example              VITE_GATEWAY_URL=https://localhost:8443
  src/
    main.tsx, App.tsx       router + SessionProvider
    api/client.ts           base URL, auth header, JSON/error handling, 401 handling
    api/types.ts            DTO types (snake_case as in the API)
    api/{auth,players,monsters,teams,encounters,history}.ts
    session/SessionContext.tsx
    components/             MonsterCard, Sprite, HpBar, Pagination, ErrorBanner, StatTable
    pages/                  one folder per page
```

## Pages

| Route | Page | API |
|---|---|---|
| `/register` | Registration form | `POST /api/auth/register` then token |
| `/login` | Login form | `POST /api/auth/token` (form-urlencoded) |
| `/starters` | Pool of 10, pick exactly 3 | `GET /api/players/me/starters`, `GET /api/monsters?ids=`, `POST /api/players/me/starters` |
| `/` | Dashboard: run stage, wins, boss availability, shortcuts | `GET /api/players/me`, `GET /api/runs/current` |
| `/catalog`, `/catalog/:id` | Catalogue with element/rarity filters and pagination; species detail | `GET /api/monsters`, `/api/monsters/{id}` |
| `/collection`, `/collection/:ownedId` | Owned monsters; past runs via run selector | `GET /api/collection`, `/api/collection/{id}`, `GET /api/runs` |
| `/teams`, `/teams/new`, `/teams/:id` | List, editor (choose up to 3, order with up/down), delete | `GET/POST/PUT/DELETE /api/teams`, `POST /api/teams/validate` |
| `/encounters` | Available encounters with characteristics, team picker, start | `GET /api/encounters`, `GET /api/teams`, `POST /api/battles` |
| `/battle` | Wild vs active monster, HP bars, bench, actions Attack / Switch / Flee, round log; result panel at the end | `GET /api/battles/current`, `POST /api/battles/current/actions` |
| `/runs`, `/runs/:id` | Past runs and outcome; abandon current run (with confirmation); start new run after a win | `GET /api/runs`, `POST /api/runs/current/abandon`, `POST /api/runs` |
| `/history`, `/history/:id` | History with outcome/run filters; detail with round log | `GET /api/history`, `/api/history/{id}` |

Navigation guard: after login, `GET /api/players/me`; if `starters_chosen` is false → redirect to `/starters`. If `/api/battles/current` returns a battle → show a "Resume battle" banner.

## Security

- **Token storage: `localStorage`** (P-17), key `tamer.session` = `{access_token, expires_at, refresh_token?}`. Mitigations, all mandatory:
  - never use `dangerouslySetInnerHTML`; all API text is rendered as React text;
  - Content-Security-Policy injected as `<meta>` in the production build (`transformIndexHtml` with `apply: 'build'`; the dev server needs inline scripts for hot reload): `default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' https://localhost:8443; connect-src https://localhost:8443; frame-ancestors 'none'; base-uri 'self'`;
  - no third-party scripts or CDNs.
- On `401`: clear the session and go to `/login` (P1). P2: try once `grant_type=refresh_token`, then fall back to login.
- Logout: P1 clears storage; P2 also calls `POST /api/auth/logout`.
- Self-signed certificate: the README tells the user to open `https://localhost:8443/health` once and accept it (or trust the exported CA).
- CORS origins allowed by the gateway: `http://localhost:8080` (frontend container, `docker-compose.frontend.yml`), `http://localhost:5173` (dev) and `http://localhost:4173` (`npm run preview`). Configurable with `CORS_ALLOWED_ORIGINS` on the gateway.

## Client-side assumptions (copy into the final report)

1. The starter pool returns only ids; the client loads the details with `GET /api/monsters?ids=`.
2. Sprite URLs from the API are relative (`/images/monsters/001.png`); the client prefixes `VITE_GATEWAY_URL`.
3. The client never computes stats, damage, XP, validity or availability: it shows backend fields (`stats`, `errors`, `result`, `boss_available`).
4. The team editor calls `POST /api/teams/validate` on every change to show errors; the backend validates again on save.
5. The battle screen renders `last_round.events` in order; animations are cosmetic only.

---

## FE-1 — Base, session, account and monsters · P1

**Depends on:** INFRA-5, AUTH-3, PL-1, MO-1, MO-2

- [ ] Scaffold with Vite React-TS template; pin versions; ESLint with the recommended React rules; `npm run build` must pass with no type errors.
- [ ] `api/client.ts`: base URL from `VITE_GATEWAY_URL`; JSON by default, form-urlencoded for the token endpoint; parses the error format (`error`, `message`, `details`) and the OAuth2 errors; throws a typed `ApiError`.
- [ ] `SessionContext`: login, logout, current player, guard component.
- [ ] Pages: register, login, starters, dashboard, catalogue (+ detail), collection (+ detail).
- [ ] Components: `Sprite` (pixelated, 64 → 128/192 px), `MonsterCard`, `StatTable`, `Pagination`, `ErrorBanner`.
- [ ] CSP plugin for the build.
- [ ] `frontend/README.md`: `npm ci`, `.env`, `npm run dev`, `npm run build && npm run preview`, certificate acceptance.

**Done when:** a new user registers, picks starters and sees the collection only through the UI.

## FE-2 — Teams, encounters, battle, runs, history · P1

**Depends on:** FE-1, TE-1, EN-1, EN-3, HI-1

- [ ] Team list and editor with live validation feedback.
- [ ] Encounters page: stage/wins/boss status; cards with element, level range, rarity weights, boss badge; start with a chosen team; handle `409 BATTLE_IN_PROGRESS` by redirecting to `/battle`.
- [ ] Battle page: HP bars, active/bench, KO markers; Attack, Switch (choose a bench slot), Flee; round log; disabled buttons while a request is in flight (no double submit); result panel (outcome, reward with sprite, XP, level-ups, pending reward notice).
- [ ] Defeat / abandon: explain the run is over, link to `/starters`.
- [ ] Runs page with abandon (confirm dialog) and "Start new run" after a win.
- [ ] History list with filters and detail with round log.

**Done when:** prepare → encounter → result → progress is playable end-to-end in the UI.

## FE-3 — Final polish · P2

- [ ] Refresh token flow and server logout.
- [ ] Profile page for US-04 (if implemented).
- [ ] Ability effects visible in the round log (`ability` events from EN-4).
- [ ] History integrity badge using `GET /api/history/verify`.
- [ ] `npm audit --omit=dev` clean of high/critical (also scanned by Trivy in CI-2).
