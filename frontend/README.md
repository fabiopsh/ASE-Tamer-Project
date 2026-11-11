# frontend

Web client of Monster Tamers (user story **US-28**). It talks only to the gateway over HTTPS and contains no
business logic: it shows what the backend returns and sends the player's intents.
Specification: [`docs/plan/09-frontend.md`](../docs/plan/09-frontend.md).

Stack: Vite + React + TypeScript, `react-router-dom`, plain CSS, fonts self-hosted from npm (`@fontsource-variable/*`).
All versions are exact in `package.json`; `package-lock.json` is committed.

The **Archivio d’ambra** reskin uses metric-compatible Amber pixel fonts derived from the bundled OFL sources.
Visual tokens, font regeneration, motion, accessibility and layout verification are documented in
[`docs/amber-reskin.md`](docs/amber-reskin.md).

## Requirements

- Node.js 22.12 or newer (developed with Node 24) and npm.
- For the real backend: the stack running with `docker compose up` (gateway on `https://localhost:8443`).

## Run against the real backend

1. Install the exact dependency versions from the lock file:

   ```sh
   npm ci
   ```

   Expected: `added N packages` and no error.

2. Create the environment file from the example (it contains `VITE_GATEWAY_URL=https://localhost:8443`):

   ```sh
   cp .env.example .env
   ```

3. Accept the gateway's self-signed certificate **once**: open <https://localhost:8443/health> in the browser you will
   use, choose *Advanced → Proceed to localhost*, and check that the page answers. Without this step every API call
   fails and the login page says *Cannot reach the game server*. (Alternative: trust the exported CA `tamer-ca.crt`,
   see `docs/plan/02-infrastructure.md`.)

4. Start the development server:

   ```sh
   npm run dev
   ```

   Expected: `Local: http://localhost:5173/`. Open it and register a new account.

5. Production build and local preview (the build injects the Content-Security-Policy `<meta>` tag):

   ```sh
   npm run build && npm run preview
   ```

   Expected: `dist/` is written with no type errors, then `Local: http://localhost:4173/`.
   The gateway allows CORS only from `http://localhost:5173` and `http://localhost:4173`.

Other scripts: `npm run lint` (ESLint, must report no problem), `npm run typecheck` (TypeScript only).

## Run without the backend (mock gateway)

`mock/server.mjs` is a zero-dependency Node server that imitates the gateway API with the same JSON shapes as the
Java DTOs. It reads the catalogue from `backend/monsters/seed/` and serves the sprites from
`backend/monsters/seed/images/monsters/`. State is kept in memory and is lost when it stops.

1. In one terminal start the mock:

   ```sh
   npm run mock
   ```

   Expected: `Monster Tamers mock gateway on http://localhost:8787`.

2. In a second terminal start the dev server pointed at the mock (bash; in PowerShell run
   `$env:VITE_GATEWAY_URL="http://localhost:8787"; npm run dev`):

   ```sh
   VITE_GATEWAY_URL=http://localhost:8787 npm run dev
   ```

3. Open <http://localhost:5173>. Either register a new account (you will pick starters), or log in with the built-in
   account `demo` / `demo-tamer-1`, which already has a run at stage 3, a collection and a past run.

The mock implements: register, token (password grant), players/me, starters, monsters (filters, `ids`, pagination),
abilities, elements, collection (current run and `run_id`), teams (CRUD and validate), encounters (the real stage
table from `backend/encounters/seed/encounter_templates.json`), runs (current, list, detail, abandon, new run after a
win), battles (start, current, actions) and history (list with `outcome` / `run_id` filters, detail). Battles use a
small simulation with the formulas of `docs/plan/07-encounters.md` (abilities reduced to damage boost / reduction and
dodge, no seeded RNG) and return events, `last_round` and `result` with the same shape as the Java engine. Defeat and
abandon reset the run (collection archived, teams removed, new starter pool), exactly as the real flow: you pick new
starters afterwards. Every finished or abandoned battle is stored in the history; the demo account starts with two
records.

Mock options (environment variables): `MOCK_PORT` (default 8787), `MOCK_DELAY_MS` (default 250, to see the loading
skeletons), `MOCK_NO_ENCOUNTERS=1` (runs and battles answer 502, as if the encounters service were not deployed),
`MOCK_DIFFICULTY` (multiplier of the wild monster's attack, default 1: use `3` to lose quickly, `0.3` to win),
`MOCK_REWARD_PENDING=1` (victories answer `reward.pending = true`, to see the pending-reward notice).

The production build only allows `https://localhost:8443` in its CSP, so use the mock with `npm run dev`, not with
`npm run preview`.

## Code map

```
src/
  main.tsx, App.tsx          router, providers, routes
  api/client.ts              base URL, bearer token, JSON / form bodies, ApiError, 401 handling
  api/types.ts               DTO types (snake_case, as on the wire)
  api/{auth,players,monsters,teams,encounters,history}.ts
  session/                   SessionContext (token + profile), GameStatusContext (run + battle), guards
  styles/tokens.css          design tokens (colours, elements, rarity, spacing, radii, shadows, motion)
  styles/base.css, ui.css    reset, background, shared utility classes
  components/                Sprite, MonsterCard, StatTable, HpBar/XpBar, Pagination, ErrorBanner, …
  layout/                    AppShell (top bar, navigation), AuthLayout, Logo
  lib/battleText.ts          battle events → readable sentences (battle log and history detail)
  pages/<area>/              one folder per page
mock/server.mjs              mock gateway for development
```

## Pages

| Route | What it does |
|---|---|
| `/login`, `/register`, `/starters` | Account and starter choice |
| `/` | Dashboard: run progress, companions, shortcuts, resume battle |
| `/catalog`, `/catalog/:id`, `/collection`, `/collection/:ownedId` | Catalogue and owned monsters |
| `/teams` | Saved teams in battle order; delete with confirmation |
| `/teams/new`, `/teams/:id` | Team editor: up to three active monsters, order with arrows or drag, live check with `POST /api/teams/validate` (errors shown as returned) |
| `/encounters` | Run strip (stage, wins, boss), encounters by stage (element, level range, rarity weights, boss cards), team picker with the backend check, start battle |
| `/battle` | Battle screen: arena, HP plates, bench, Attack / Switch / Flee (keys A, S, F, 1–3), round log revealed event by event (click to skip, ≤ 2 s), result panel |
| `/runs`, `/runs/:id` | Current run (abandon with confirmation, new run after a win), all runs, run detail with its latest battles |
| `/history`, `/history/:id` | Finished battles with outcome and run filters; detail with team snapshot, result and the full round log |

Animations on the battle screen are cosmetic and follow the events of `last_round`; with `prefers-reduced-motion`
the round is shown at once and the effects are off. Reloading `/battle` after a battle ended shows "no battle in
progress" (the server no longer serves it); the result stays in the history.

## Security notes

- The access token is stored in `localStorage` under `tamer.session` (`{access_token, expires_at, refresh_token?}`,
  `expires_at` in epoch milliseconds). A `401` clears it and returns to `/login`.
- `dangerouslySetInnerHTML` is never used (an ESLint rule forbids it): API text is always rendered as React text.
- No CDN or third-party script: fonts are bundled from npm. The CSP is added only to the production build because the
  dev server needs inline scripts for hot reload. Browsers ignore `frame-ancestors` inside a `<meta>` tag (a console
  message says so); it takes effect only if the same policy is also sent as an HTTP header.
