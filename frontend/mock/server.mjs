#!/usr/bin/env node
/**
 * Zero-dependency mock of the gateway API, for frontend development without the backend.
 *
 *   npm run mock                      # http://localhost:8787
 *   VITE_GATEWAY_URL=http://localhost:8787 npm run dev
 *
 * Data comes from backend/monsters/seed/*.json and backend/encounters/seed/encounter_templates.json; sprites are served from
 * backend/monsters/seed/images/monsters/. State lives in memory and is lost on restart.
 * Response shapes mirror the Java DTO records (snake_case).
 *
 * Environment:
 *   MOCK_PORT=8787            port
 *   MOCK_DELAY_MS=250         artificial latency, to see loading states
 *   MOCK_NO_ENCOUNTERS=1      answer 502 on /api/runs*, /api/battles*, /api/encounters* (service not deployed)
 *   MOCK_DIFFICULTY=1         multiplier of the wild monster's attack (2 = easy to lose, 0.5 = easy to win)
 *   MOCK_REWARD_PENDING=1     victories answer reward.pending = true (monsters "unreachable")
 *
 * Built-in account with progress: username "demo", password "demo-tamer-1".
 */
import { createServer } from 'node:http';
import { readFileSync, existsSync, createReadStream } from 'node:fs';
import { randomUUID, randomBytes } from 'node:crypto';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const HERE = dirname(fileURLToPath(import.meta.url));
const SEED = resolve(HERE, '../../backend/monsters/seed');
const PORT = Number(process.env.MOCK_PORT ?? 8787);
const DELAY = Number(process.env.MOCK_DELAY_MS ?? 250);
const NO_ENCOUNTERS = process.env.MOCK_NO_ENCOUNTERS === '1';
const DIFFICULTY = Number(process.env.MOCK_DIFFICULTY ?? 1) || 1;
const REWARD_PENDING = process.env.MOCK_REWARD_PENDING === '1';
const ORIGINS = new Set(['http://localhost:5173', 'http://localhost:4173', 'http://127.0.0.1:5173', 'http://127.0.0.1:4173']);
const TOKEN_TTL = 3600;
const ELEMENTS = ['dream', 'echo', 'time', 'gravity', 'silence', 'memory', 'chaos'];
const RARITIES = ['common', 'rare', 'legendary'];

// ------------------------------------------------------------------ seed

const monstersSeed = JSON.parse(readFileSync(join(SEED, 'monsters.json'), 'utf8'));
const abilitiesSeed = JSON.parse(readFileSync(join(SEED, 'abilities.json'), 'utf8'));
const elementsSeed = JSON.parse(readFileSync(join(SEED, 'elements.json'), 'utf8'));
const templatesSeed = JSON.parse(readFileSync(resolve(HERE, '../../backend/encounters/seed/encounter_templates.json'), 'utf8'));
const abilityById = new Map(abilitiesSeed.map((a) => [a.ability_id, a]));
const monsterById = new Map(monstersSeed.map((m) => [m.monster_id, m]));
const commonIds = monstersSeed.filter((m) => m.rarity === 'common').map((m) => m.monster_id);

/** AbilityResponse.java */
const abilityDto = (a) => ({
  ability_id: a.ability_id,
  name: a.name,
  description: a.description,
  scope: a.scope,
  element: a.element,
  effect_type: a.effect_type,
  effect_value: a.effect_value,
  trigger: a.trigger,
});
/** MonsterSummary.java */
const summaryDto = (m) => ({
  monster_id: m.monster_id,
  name: m.name,
  element: m.element,
  rarity: m.rarity,
  image_url: m.image_url,
});
/** MonsterDetail.java */
const detailDto = (m) => ({
  ...summaryDto(m),
  description: m.description,
  base_stats: { hp: m.base_hp, attack: m.base_attack, defense: m.base_defense, speed: m.base_speed },
  growth: { hp: m.growth_hp, attack: m.growth_attack, defense: m.growth_defense, speed: m.growth_speed },
  ability: abilityDto(abilityById.get(m.ability_id)),
});
const statAt = (base, growth, level) => Math.floor(base + growth * (level - 1));
const xpToNext = (level) => (level >= 50 ? 0 : 15 * level);
/** OwnedMonsterResponse.java */
function ownedDto(o) {
  const m = monsterById.get(o.monster_id);
  return {
    owned_id: o.owned_id,
    monster_id: m.monster_id,
    name: m.name,
    element: m.element,
    rarity: m.rarity,
    image_url: m.image_url,
    level: o.level,
    xp: o.xp,
    xp_to_next_level: xpToNext(o.level),
    stats: {
      hp: statAt(m.base_hp, m.growth_hp, o.level),
      attack: statAt(m.base_attack, m.growth_attack, o.level),
      defense: statAt(m.base_defense, m.growth_defense, o.level),
      speed: statAt(m.base_speed, m.growth_speed, o.level),
    },
    ability: abilityDto(abilityById.get(m.ability_id)),
    source: o.source,
    encounter_id: o.encounter_id,
    run_id: o.run_id,
    status: o.status,
    obtained_at: o.obtained_at,
  };
}

// ------------------------------------------------------------------ state

/** username(lowercase) -> {player_id, password} */
const accounts = new Map();
/** token -> {player_id, exp} */
const tokens = new Map();
/** player_id -> {player_id, display_name, avatar, current_run_id, starter_pool, starters_chosen, created_at} */
const players = new Map();
/** player_id -> owned[] */
const collections = new Map();
/** player_id -> run[] (newest first) */
const runs = new Map();
/** player_id -> team[] */
const teams = new Map();

const now = () => new Date().toISOString();
const daysAgo = (d) => new Date(Date.now() - d * 86400000).toISOString();

function pickPool() {
  const ids = [...commonIds];
  for (let i = ids.length - 1; i > 0; i--) {
    const j = Math.floor(Math.random() * (i + 1));
    [ids[i], ids[j]] = [ids[j], ids[i]];
  }
  return ids.slice(0, 10);
}

function createPlayer(playerId) {
  const p = {
    player_id: playerId,
    display_name: `Tamer-${playerId.replace(/-/g, '').slice(0, 6)}`,
    avatar: null,
    current_run_id: randomUUID(),
    starter_pool: pickPool(),
    starters_chosen: false,
    created_at: now(),
  };
  players.set(playerId, p);
  collections.set(playerId, []);
  runs.set(playerId, []);
  teams.set(playerId, []);
  return p;
}

function owned(runId, monsterId, level, source, status, obtainedAt, xp = 0) {
  return {
    owned_id: randomUUID(),
    run_id: runId,
    monster_id: monsterId,
    level,
    xp,
    source,
    encounter_id: source === 'starter' ? null : randomUUID(),
    status,
    obtained_at: obtainedAt,
  };
}

function seedDemo() {
  const playerId = randomUUID();
  accounts.set('demo', { player_id: playerId, password: 'demo-tamer-1' });
  const p = createPlayer(playerId);
  p.display_name = 'Tamer-demo42';
  p.starters_chosen = true;
  p.created_at = daysAgo(9);
  const oldRun = randomUUID();
  const run = p.current_run_id;
  collections.set(playerId, [
    owned(oldRun, 2, 4, 'starter', 'archived', daysAgo(9)),
    owned(oldRun, 23, 5, 'starter', 'archived', daysAgo(9), 31),
    owned(oldRun, 45, 3, 'starter', 'archived', daysAgo(9), 12),
    owned(oldRun, 57, 6, 'encounter', 'archived', daysAgo(8), 40),
    owned(run, 3, 9, 'starter', 'active', daysAgo(4), 88),
    owned(run, 41, 8, 'starter', 'active', daysAgo(4), 15),
    owned(run, 66, 9, 'starter', 'active', daysAgo(4), 120),
    owned(run, 52, 7, 'encounter', 'active', daysAgo(3), 20),
    owned(run, 31, 5, 'boss', 'active', daysAgo(2), 60),
    owned(run, 89, 8, 'boss', 'active', daysAgo(1), 5),
    owned(run, 75, 9, 'encounter', 'active', daysAgo(1), 0),
    owned(run, 16, 20, 'encounter', 'active', daysAgo(0), 140),
  ]);
  runs.set(playerId, [
    {
      run_id: run,
      status: 'active',
      stage: 3,
      wins_in_stage: 2,
      boss_available: true,
      bosses_defeated: [1, 2],
      battle_in_progress: false,
      reset_pending: false,
      started_at: daysAgo(4),
      ended_at: null,
    },
    {
      run_id: oldRun,
      status: 'lost',
      stage: 2,
      wins_in_stage: 1,
      boss_available: false,
      bosses_defeated: [1],
      battle_in_progress: false,
      reset_pending: false,
      started_at: daysAgo(9),
      ended_at: daysAgo(5),
    },
  ]);
}
seedDemo();

// ------------------------------------------------------------------ http helpers

class HttpError extends Error {
  constructor(status, body) {
    super(body?.message ?? String(status));
    this.status = status;
    this.body = body;
  }
}
const apiError = (status, error, message, details) =>
  new HttpError(status, details ? { error, message, details } : { error, message });
const validation = (message, field, code = 'INVALID') => apiError(400, 'VALIDATION_ERROR', message, [{ field, code }]);
const notFound = (message = 'Resource not found.') => apiError(404, 'NOT_FOUND', message);

function cors(req, res) {
  const origin = req.headers.origin;
  if (origin && ORIGINS.has(origin)) {
    res.setHeader('Access-Control-Allow-Origin', origin);
    res.setHeader('Vary', 'Origin');
    res.setHeader('Access-Control-Allow-Methods', 'GET, POST, PUT, PATCH, DELETE, OPTIONS');
    res.setHeader('Access-Control-Allow-Headers', 'Authorization, Content-Type, Accept');
    res.setHeader('Access-Control-Max-Age', '600');
  }
}

function send(res, status, body, headers = {}) {
  if (body === undefined) {
    res.writeHead(status, headers);
    res.end();
    return;
  }
  const json = JSON.stringify(body);
  res.writeHead(status, { 'Content-Type': 'application/json', ...headers });
  res.end(json);
}

async function readBody(req) {
  const chunks = [];
  for await (const c of req) chunks.push(c);
  return Buffer.concat(chunks).toString('utf8');
}

async function jsonBody(req) {
  const raw = await readBody(req);
  if (!raw) throw validation('Request body is required.', 'body', 'REQUIRED');
  try {
    return JSON.parse(raw);
  } catch {
    throw validation('Malformed JSON.', 'body', 'MALFORMED');
  }
}

function auth(req) {
  const h = req.headers.authorization ?? '';
  const m = /^Bearer (.+)$/.exec(h);
  const t = m && tokens.get(m[1]);
  if (!t || t.exp < Date.now()) throw apiError(401, 'UNAUTHORIZED', 'Missing, invalid or expired token.');
  const p = players.get(t.player_id);
  if (!p) throw notFound('Player not found.');
  return p;
}

function pageSpec(url) {
  const page = url.searchParams.has('page') ? Number(url.searchParams.get('page')) : 0;
  const size = url.searchParams.has('size') ? Number(url.searchParams.get('size')) : 20;
  if (!Number.isInteger(page) || !Number.isInteger(size) || page < 0 || size < 1 || size > 50) {
    throw validation('Page must be >= 0 and size must be between 1 and 50.', 'pagination');
  }
  return { page, size };
}

/** PageResponse.java */
function paginate(list, { page, size }) {
  return {
    items: list.slice(page * size, page * size + size),
    page,
    size,
    total_items: list.length,
    total_pages: Math.ceil(list.length / size),
  };
}

function currentRun(p) {
  return (runs.get(p.player_id) ?? []).find((r) => r.run_id === p.current_run_id) ?? null;
}

// ------------------------------------------------------------------ routes

const routes = [];
const route = (method, pattern, handler) => {
  const keys = [];
  const re = new RegExp(`^${pattern.replace(/:(\w+)/g, (_, k) => (keys.push(k), '([^/]+)'))}$`);
  routes.push({ method, re, keys, handler });
};

route('GET', '/health', () => [200, { status: 'UP' }]);

// ---- auth
route('POST', '/api/auth/register', async (req) => {
  const body = await jsonBody(req);
  const username = typeof body.username === 'string' ? body.username : '';
  const password = typeof body.password === 'string' ? body.password : '';
  if (username.length < 3 || username.length > 20 || !/^[a-zA-Z0-9_]+$/.test(username)) {
    throw validation('Invalid username.', 'username', 'PATTERN');
  }
  if (password.length < 8 || Buffer.byteLength(password, 'utf8') > 72) {
    throw validation('Password must be 8-72 characters.', 'password', 'SIZE');
  }
  const norm = username.trim().toLowerCase();
  if (password.toLowerCase() === norm) throw validation('Password must differ from username.', 'password', 'EQUALS_USERNAME');
  if (accounts.has(norm)) throw apiError(409, 'USERNAME_TAKEN', 'This username is already taken.');
  const playerId = randomUUID();
  accounts.set(norm, { player_id: playerId, password });
  createPlayer(playerId);
  return [201, { player_id: playerId, username: norm }];
});

route('POST', '/api/auth/token', async (req) => {
  const type = req.headers['content-type'] ?? '';
  if (!type.startsWith('application/x-www-form-urlencoded')) {
    return [400, { error: 'invalid_request', error_description: 'Form body expected.' }];
  }
  const form = new URLSearchParams(await readBody(req));
  const grant = form.get('grant_type');
  if (!grant) return [400, { error: 'invalid_request', error_description: 'grant_type is required.' }];
  if (grant !== 'password') return [400, { error: 'unsupported_grant_type', error_description: 'Unsupported grant type.' }];
  const acc = accounts.get((form.get('username') ?? '').trim().toLowerCase());
  if (!acc || acc.password !== form.get('password')) {
    return [400, { error: 'invalid_grant', error_description: 'Invalid username or password.' }];
  }
  const token = `mock.${randomBytes(24).toString('base64url')}`;
  tokens.set(token, { player_id: acc.player_id, exp: Date.now() + TOKEN_TTL * 1000 });
  return [
    200,
    { access_token: token, token_type: 'Bearer', expires_in: TOKEN_TTL, scope: 'player' },
    { 'Cache-Control': 'no-store', Pragma: 'no-cache' },
  ];
});

// ---- players
const playerDto = (p) => ({
  player_id: p.player_id,
  display_name: p.display_name,
  avatar: p.avatar,
  current_run_id: p.current_run_id,
  starters_chosen: p.starters_chosen,
  created_at: p.created_at,
});
route('GET', '/api/players/me', (req) => [200, playerDto(auth(req))]);
route('GET', '/api/players/me/starters', (req) => {
  const p = auth(req);
  return [200, { run_id: p.current_run_id, pool: p.starter_pool, chosen: p.starters_chosen }];
});
route('POST', '/api/players/me/starters', async (req) => {
  const p = auth(req);
  const body = await jsonBody(req);
  if (p.starters_chosen) throw apiError(409, 'STARTERS_ALREADY_CHOSEN', 'Starters have already been chosen for this run.');
  const ids = body.monster_ids;
  if (
    !Array.isArray(ids) ||
    ids.length !== 3 ||
    new Set(ids).size !== 3 ||
    !ids.every((id) => Number.isInteger(id) && p.starter_pool.includes(id))
  ) {
    throw apiError(400, 'INVALID_STARTERS', 'Choose exactly 3 distinct monsters from your starter pool.');
  }
  const list = collections.get(p.player_id);
  const starters = ids.map((id) => {
    const o = owned(p.current_run_id, id, 1, 'starter', 'active', now());
    list.push(o);
    return { owned_id: o.owned_id, monster_id: id, level: 1 };
  });
  runs.get(p.player_id).unshift({
    run_id: p.current_run_id,
    status: 'active',
    stage: 1,
    wins_in_stage: 0,
    boss_available: false,
    bosses_defeated: [],
    battle_in_progress: false,
    reset_pending: false,
    started_at: now(),
    ended_at: null,
  });
  p.starters_chosen = true;
  return [201, { run_id: p.current_run_id, starters }];
});

// ---- catalogue
route('GET', '/api/monsters', (req, url) => {
  auth(req);
  const element = url.searchParams.get('element');
  const rarity = url.searchParams.get('rarity');
  const idsParam = url.searchParams.get('ids');
  if (element && !ELEMENTS.includes(element)) throw validation('Unknown element.', 'element');
  if (rarity && !RARITIES.includes(rarity)) throw validation('Unknown rarity.', 'rarity');
  let ids = null;
  if (idsParam) {
    const parts = idsParam.split(',');
    if (parts.length > 50) throw validation('Invalid number of monster ids.', 'ids');
    ids = new Set(
      parts.map((s) => {
        const n = Number(s.trim());
        if (!Number.isInteger(n) || n <= 0) throw validation('Monster ids must be positive integers.', 'ids');
        return n;
      }),
    );
  }
  const spec = pageSpec(url);
  const list = monstersSeed
    .filter((m) => (!element || m.element === element) && (!rarity || m.rarity === rarity) && (!ids || ids.has(m.monster_id)))
    .map(summaryDto);
  return [200, paginate(list, spec)];
});
route('GET', '/api/monsters/:id', (req, _url, { id }) => {
  auth(req);
  const m = monsterById.get(Number(id));
  if (!m) throw notFound('Monster not found.');
  return [200, detailDto(m)];
});
route('GET', '/api/abilities', (req) => (auth(req), [200, abilitiesSeed.map(abilityDto)]));
route('GET', '/api/abilities/:id', (req, _url, { id }) => {
  auth(req);
  const a = abilityById.get(Number(id));
  if (!a) throw notFound('Ability not found.');
  return [200, abilityDto(a)];
});
route('GET', '/api/elements', (req) => (auth(req), [200, elementsSeed]));

// ---- collection
route('GET', '/api/collection', (req, url) => {
  const p = auth(req);
  const runId = url.searchParams.get('run_id');
  const spec = pageSpec(url);
  const list = (collections.get(p.player_id) ?? [])
    .filter((o) => (runId ? o.run_id === runId : o.status === 'active'))
    .sort((a, b) => a.obtained_at.localeCompare(b.obtained_at))
    .map(ownedDto);
  return [200, paginate(list, spec)];
});
route('GET', '/api/collection/:ownedId', (req, _url, { ownedId }) => {
  const p = auth(req);
  const o = (collections.get(p.player_id) ?? []).find((x) => x.owned_id === ownedId);
  if (!o) throw notFound('Monster not found.');
  return [200, ownedDto(o)];
});

// ---- teams (TeamsResource.java)
function teamDto(p, t) {
  const list = collections.get(p.player_id) ?? [];
  return {
    team_id: t.team_id,
    name: t.name,
    run_id: t.run_id,
    members: t.members.map((ownedId, i) => {
      const o = list.find((x) => x.owned_id === ownedId);
      const m = monsterById.get(o.monster_id);
      return {
        position: i + 1,
        owned_id: o.owned_id,
        monster_id: m.monster_id,
        name: m.name,
        level: o.level,
        element: m.element,
        image_url: m.image_url,
      };
    }),
    created_at: t.created_at,
    updated_at: t.updated_at,
  };
}
function validateTeam(p, name, members, isCreate) {
  const errors = [];
  const list = collections.get(p.player_id) ?? [];
  if (name !== undefined && (typeof name !== 'string' || !/^[A-Za-z0-9 _-]{1,30}$/.test(name))) {
    errors.push({ code: 'NAME_INVALID', message: 'Name must be 1-30 letters, digits, spaces, _ or -.' });
  }
  if (!Array.isArray(members) || members.length < 1 || members.length > 3) {
    errors.push({ code: 'TEAM_SIZE', message: 'Team must contain between 1 and 3 monsters.' });
  }
  const arr = Array.isArray(members) ? members : [];
  const seen = new Set();
  for (const id of arr) {
    if (seen.has(id)) errors.push({ code: 'DUPLICATE_MEMBER', message: 'The same monster appears twice.', owned_id: id });
    seen.add(id);
    const o = list.find((x) => x.owned_id === id);
    if (!o) errors.push({ code: 'NOT_OWNED', message: 'This monster is not in your collection.', owned_id: id });
    else if (o.status !== 'active') errors.push({ code: 'NOT_ACTIVE', message: 'This monster belongs to a past run.', owned_id: id });
  }
  if (isCreate && (teams.get(p.player_id) ?? []).filter((t) => t.run_id === p.current_run_id).length >= 5) {
    errors.push({ code: 'TEAM_LIMIT', message: 'At most 5 teams per run.' });
  }
  return { valid: errors.length === 0, errors };
}
const teamInvalid = (v) =>
  apiError(
    400,
    'TEAM_INVALID',
    v.errors[0]?.message ?? 'Team is invalid.',
    v.errors.map((e) => ({ field: 'members', code: e.code })),
  );
route('GET', '/api/teams', (req) => {
  const p = auth(req);
  return [200, (teams.get(p.player_id) ?? []).map((t) => teamDto(p, t))];
});
route('POST', '/api/teams/validate', async (req) => {
  const p = auth(req);
  const body = await jsonBody(req);
  return [200, validateTeam(p, undefined, body.members, false)];
});
route('POST', '/api/teams', async (req) => {
  const p = auth(req);
  const body = await jsonBody(req);
  const v = validateTeam(p, body.name ?? '', body.members, true);
  if (!v.valid) throw teamInvalid(v);
  const t = { team_id: randomUUID(), name: body.name, run_id: p.current_run_id, members: body.members, created_at: now(), updated_at: now() };
  teams.get(p.player_id).push(t);
  return [201, teamDto(p, t)];
});
const findTeam = (p, id) => {
  const t = (teams.get(p.player_id) ?? []).find((x) => x.team_id === id);
  if (!t) throw notFound('Team not found.');
  return t;
};
route('GET', '/api/teams/:id', (req, _url, { id }) => {
  const p = auth(req);
  return [200, teamDto(p, findTeam(p, id))];
});
route('PUT', '/api/teams/:id', async (req, _url, { id }) => {
  const p = auth(req);
  const t = findTeam(p, id);
  const body = await jsonBody(req);
  const v = validateTeam(p, body.name ?? '', body.members, false);
  if (!v.valid) throw teamInvalid(v);
  Object.assign(t, { name: body.name, members: body.members, updated_at: now() });
  return [200, teamDto(p, t)];
});
route('DELETE', '/api/teams/:id', (req, _url, { id }) => {
  const p = auth(req);
  findTeam(p, id);
  teams.set(p.player_id, teams.get(p.player_id).filter((x) => x.team_id !== id));
  return [204, undefined];
});

// ---- encounters, runs, battles (backend/encounters/.../api) and history (backend/history/.../api)
//
// A small battle simulation with the formulas of docs/plan/07-encounters.md (no seeded RNG, abilities reduced to
// damage_boost, damage_reduction and dodge). It produces events with the same shape as engine/BattleEvent.java.

const encountersDown = () => apiError(502, 'BAD_GATEWAY', 'The encounters service is not available.');
const noActiveRun = (status = 409) => apiError(status, 'NO_ACTIVE_RUN', 'There is no active run.');
const WINS_FOR_BOSS = 2;
const FINAL_STAGE = 8;
const RARITY_XP = { common: 1, rare: 1.5, legendary: 2 };
const BOSS_IDS = new Set(templatesSeed.filter((t) => t.is_boss).map((t) => t.boss_monster_id));

/** RunRules.bossAvailable */
const bossAvailable = (r) => r.status === 'active' && r.wins_in_stage >= WINS_FOR_BOSS && !r.bosses_defeated.includes(r.stage);
/** RunResponse.java */
const runDto = (r) => ({ ...r, boss_available: bossAvailable(r) });

/** EncounterResponse.java */
const templateDto = (t) => ({
  template_id: t.template_id,
  name: t.name,
  stage: t.stage,
  element: t.element,
  is_boss: t.is_boss,
  boss_monster_id: t.boss_monster_id ?? null,
  level_min: t.level_min,
  level_max: t.level_max,
  rarity_weights: t.rarity_weights,
});
const templateRef = (t) => ({ template_id: t.template_id, name: t.name, stage: t.stage, element: t.element, is_boss: t.is_boss });

function activeRunOf(p) {
  const r = currentRun(p);
  return r && r.status === 'active' ? r : null;
}
function availableTemplates(r) {
  if (!r || r.status !== 'active') return [];
  return templatesSeed.filter((t) => (t.is_boss ? t.stage === r.stage && bossAvailable(r) : t.stage <= r.stage));
}

/** End of a run (lost, abandoned, or a new run after a win): archive, clear teams, new starter pool. */
function resetRun(p) {
  for (const o of collections.get(p.player_id) ?? []) if (o.run_id === p.current_run_id) o.status = 'archived';
  teams.set(p.player_id, (teams.get(p.player_id) ?? []).filter((t) => t.run_id !== p.current_run_id));
  p.current_run_id = randomUUID();
  p.starter_pool = pickPool();
  p.starters_chosen = false;
}

route('GET', '/api/encounters', (req) => {
  if (NO_ENCOUNTERS) throw encountersDown();
  const r = activeRunOf(auth(req));
  if (!r) throw noActiveRun();
  return [200, availableTemplates(r).map(templateDto)];
});
route('GET', '/api/encounters/:id', (req, _url, { id }) => {
  if (NO_ENCOUNTERS) throw encountersDown();
  const r = activeRunOf(auth(req));
  if (!r) throw noActiveRun();
  const t = availableTemplates(r).find((x) => x.template_id === Number(id));
  if (!t) throw notFound('Encounter not available.');
  return [200, templateDto(t)];
});

route('GET', '/api/runs/current', (req) => {
  if (NO_ENCOUNTERS) throw encountersDown();
  const p = auth(req);
  const r = currentRun(p);
  if (!r) throw noActiveRun(404);
  return [200, runDto(r)];
});
route('GET', '/api/runs', (req, url) => {
  if (NO_ENCOUNTERS) throw encountersDown();
  const p = auth(req);
  return [200, paginate((runs.get(p.player_id) ?? []).map(runDto), pageSpec(url))];
});
route('GET', '/api/runs/:id', (req, _url, { id }) => {
  if (NO_ENCOUNTERS) throw encountersDown();
  const p = auth(req);
  const r = (runs.get(p.player_id) ?? []).find((x) => x.run_id === id);
  if (!r) throw notFound('Run not found.');
  return [200, runDto(r)];
});
route('POST', '/api/runs/current/abandon', (req) => {
  if (NO_ENCOUNTERS) throw encountersDown();
  const p = auth(req);
  const r = activeRunOf(p);
  if (!r) throw noActiveRun();
  const b = battles.get(p.player_id);
  if (b) closeBattle(p, b, 'abandoned');
  r.status = 'abandoned';
  r.ended_at = now();
  r.battle_in_progress = false;
  resetRun(p);
  return [200, runDto(r)];
});
route('POST', '/api/runs', (req) => {
  if (NO_ENCOUNTERS) throw encountersDown();
  const p = auth(req);
  const r = currentRun(p);
  if (!r || r.status !== 'won') {
    if (!r && !p.starters_chosen) return [202, { status: 'choose_starters' }]; // idempotent after the reset
    throw apiError(409, 'NEW_RUN_NOT_ALLOWED', 'A new run can start only after a won run.');
  }
  resetRun(p);
  return [202, { status: 'choose_starters' }];
});

// ---- battle engine (simplified)

/** player_id -> live battle */
const battles = new Map();
/** player_id -> history records, newest first */
const history = new Map();

const rand = (n) => Math.floor(Math.random() * n);
const randIn = (min, max) => min + rand(max - min + 1);

function fighterStats(m, level) {
  return {
    hp: statAt(m.base_hp, m.growth_hp, level),
    attack: statAt(m.base_attack, m.growth_attack, level),
    defense: statAt(m.base_defense, m.growth_defense, level),
    speed: statAt(m.base_speed, m.growth_speed, level),
  };
}

function drawWild(t) {
  if (t.is_boss) return { m: monsterById.get(t.boss_monster_id), level: t.level_min };
  const candidates = monstersSeed.filter((m) => m.element === t.element && !BOSS_IDS.has(m.monster_id));
  const weights = RARITIES.map((r) => [r, candidates.some((m) => m.rarity === r) ? Number(t.rarity_weights[r] ?? 0) : 0]);
  const total = weights.reduce((s, [, w]) => s + w, 0);
  let roll = Math.random() * (total || 1);
  let rarity = 'common';
  for (const [r, w] of weights) {
    if (w <= 0) continue;
    rarity = r;
    if ((roll -= w) < 0) break;
  }
  const pool = candidates.filter((m) => m.rarity === rarity);
  const list = pool.length > 0 ? pool : candidates;
  return { m: list[rand(list.length)], level: randIn(t.level_min, t.level_max) };
}

function elemFactor(att, def) {
  if (elementsSeed.strong_against[att] === def) return { elem: 1.5, effectiveness: 'strong' };
  if (elementsSeed.weak_against[att] === def) return { elem: 0.67, effectiveness: 'weak' };
  return { elem: 1, effectiveness: 'neutral' };
}

/** One attack: dodge (dodge abilities only), damage formula, ability names that changed it. */
function attack(side, attacker, target) {
  const abilities = [];
  const aAb = abilityById.get(attacker.ability_id);
  const tAb = abilityById.get(target.ability_id);
  if (tAb?.effect_type === 'dodge' && Math.random() * 100 < tAb.effect_value) {
    return { type: 'attack', side, attacker: attacker.name, target: target.name, damage: 0, effectiveness: elemFactor(attacker.element, target.element).effectiveness, dodged: true, abilities: [tAb.name] };
  }
  const { elem, effectiveness } = elemFactor(attacker.element, target.element);
  let boost = 1;
  if (aAb?.effect_type === 'damage_boost') {
    boost *= 1 + aAb.effect_value / 100;
    abilities.push(aAb.name);
  }
  if (tAb?.effect_type === 'damage_reduction') {
    boost *= 1 - tAb.effect_value / 100;
    abilities.push(tAb.name);
  }
  const atk = attacker.stats.attack * DIFFICULTY_FOR(side);
  const def = target.stats.defense;
  const variance = 0.9 + Math.random() * 0.2;
  const raw = Math.max(1, Math.round(((atk * atk) / (atk + def)) * 0.6 * elem * variance * boost));
  const damage = Math.min(raw, target.hp);
  target.hp -= damage;
  return { type: 'attack', side, attacker: attacker.name, target: target.name, damage, effectiveness, dodged: false, abilities };
}
// MOCK_DIFFICULTY scales the wild monster's attack (2 = lose easily, 0.5 = win easily).
const DIFFICULTY_FOR = (side) => (side === 'wild' ? DIFFICULTY : 1);

/** BattleStateResponse.java */
function battleDto(b) {
  return {
    encounter_id: b.encounter_id,
    status: b.status,
    turn: b.turn,
    template: templateRef(b.template),
    wild: {
      monster_id: b.wild.monster_id,
      name: b.wild.name,
      element: b.wild.element,
      rarity: b.wild.rarity,
      level: b.wild.level,
      hp: b.wild.hp,
      max_hp: b.wild.max_hp,
      image_url: `/images/monsters/${String(b.wild.monster_id).padStart(3, '0')}.png`,
      ability: b.wild.ability,
    },
    team: b.team.map((m) => ({
      position: m.position,
      owned_id: m.owned_id,
      monster_id: m.monster_id,
      name: m.name,
      level: m.level,
      element: m.element,
      hp: m.hp,
      max_hp: m.max_hp,
      active: b.status === 'in_progress' && m.position === b.active,
      knocked_out: m.hp <= 0,
      image_url: `/images/monsters/${String(m.monster_id).padStart(3, '0')}.png`,
    })),
    last_round: b.last_round,
    result: b.result,
  };
}

route('GET', '/api/battles/current', (req) => {
  if (NO_ENCOUNTERS) throw encountersDown();
  const p = auth(req);
  const b = battles.get(p.player_id);
  if (!b) throw notFound('No battle in progress.');
  return [200, battleDto(b)];
});

route('POST', '/api/battles', async (req) => {
  if (NO_ENCOUNTERS) throw encountersDown();
  const p = auth(req);
  const body = await jsonBody(req);
  const unknown = Object.keys(body).filter((k) => k !== 'template_id' && k !== 'team_id');
  if (unknown.length) throw apiError(400, 'VALIDATION_ERROR', 'Unknown field.', [{ field: unknown[0], code: 'UNKNOWN_FIELD' }]);
  if (!Number.isInteger(body.template_id)) throw validation('template_id is required.', 'template_id', 'NotNull');
  if (typeof body.team_id !== 'string') throw validation('team_id is required.', 'team_id', 'NotNull');
  const r = activeRunOf(p);
  if (!r) throw noActiveRun();
  if (r.reset_pending) throw apiError(409, 'RESET_PENDING', 'The previous run is still being closed; retry in a moment.');
  if (battles.has(p.player_id)) throw apiError(409, 'BATTLE_IN_PROGRESS', 'A battle is already in progress.');
  const t = availableTemplates(r).find((x) => x.template_id === body.template_id);
  if (!t) throw notFound('Encounter not available.');
  const team = (teams.get(p.player_id) ?? []).find((x) => x.team_id === body.team_id);
  if (!team) throw notFound('Team not found.');
  const v = validateTeam(p, undefined, team.members, false);
  if (!v.valid) throw apiError(409, 'TEAM_INVALID', v.errors[0].message, [{ field: 'team_id', code: v.errors[0].code }]);

  const { m, level } = drawWild(t);
  const ws = fighterStats(m, level);
  const wildMax = Math.round((2 * ws.hp + 10) * 1.5);
  const ab = abilityById.get(m.ability_id);
  const list = collections.get(p.player_id);
  const b = {
    encounter_id: randomUUID(),
    run_id: r.run_id,
    template: t,
    status: 'in_progress',
    turn: 0,
    started_at: now(),
    wild: {
      monster_id: m.monster_id, name: m.name, element: m.element, rarity: m.rarity, level, stats: ws,
      hp: wildMax, max_hp: wildMax, ability_id: m.ability_id, ability: ab ? { name: ab.name, description: ab.description } : null,
    },
    team: team.members.map((ownedId, i) => {
      const o = list.find((x) => x.owned_id === ownedId);
      const sp = monsterById.get(o.monster_id);
      const st = fighterStats(sp, o.level);
      return {
        position: i + 1, owned_id: o.owned_id, monster_id: sp.monster_id, name: sp.name, level: o.level, element: sp.element,
        rarity: sp.rarity, stats: st, hp: 2 * st.hp + 10, max_hp: 2 * st.hp + 10, ability_id: sp.ability_id,
      };
    }),
    active: 1,
    rounds: [],
    last_round: null,
    result: null,
  };
  battles.set(p.player_id, b);
  r.battle_in_progress = true;
  return [201, battleDto(b)];
});

route('POST', '/api/battles/current/actions', async (req) => {
  if (NO_ENCOUNTERS) throw encountersDown();
  const p = auth(req);
  const body = await jsonBody(req);
  const b = battles.get(p.player_id);
  if (!b) throw notFound('No battle in progress.');
  const unknown = Object.keys(body).filter((k) => k !== 'type' && k !== 'slot');
  if (unknown.length) throw apiError(400, 'VALIDATION_ERROR', 'Unknown field.', [{ field: unknown[0], code: 'UNKNOWN_FIELD' }]);
  if (!['attack', 'switch', 'flee'].includes(body.type)) throw validation('type must be attack, switch or flee.', 'type', 'Pattern');
  if (body.type === 'switch' && body.slot == null) throw apiError(400, 'INVALID_ACTION', 'switch requires a slot.');
  if (body.type !== 'switch' && body.slot != null) throw apiError(400, 'INVALID_ACTION', 'slot is only allowed with switch.');

  const events = [];
  const active = () => b.team.find((m) => m.position === b.active);
  let outcome = null;

  if (body.type === 'switch') {
    const target = b.team.find((m) => m.position === body.slot);
    if (!target) throw apiError(400, 'INVALID_ACTION', `There is no monster in slot ${body.slot}.`);
    if (target.hp <= 0) throw apiError(400, 'INVALID_ACTION', `${target.name} is knocked out.`);
    if (target.position === b.active) throw apiError(400, 'INVALID_ACTION', `${target.name} is already fighting.`);
    events.push({ type: 'switch', side: 'team', from: b.active, to: target.position });
    b.active = target.position;
  } else if (body.type === 'flee') {
    const a = active();
    const pFlee = Math.min(0.95, a.stats.speed / (a.stats.speed + b.wild.stats.speed) + 0.25);
    const success = Math.random() < pFlee;
    events.push({ type: 'flee', success });
    if (success) outcome = 'fled';
  }

  if (!outcome) {
    const me = active();
    const order =
      body.type === 'attack'
        ? me.stats.speed > b.wild.stats.speed || (me.stats.speed === b.wild.stats.speed && Math.random() < 0.5)
          ? ['team', 'wild']
          : ['wild', 'team']
        : ['wild'];
    for (const side of order) {
      if (side === 'team') {
        events.push(attack('team', me, b.wild));
        if (b.wild.hp <= 0) {
          events.push({ type: 'knockout', side: 'wild' });
          outcome = 'victory';
          break;
        }
      } else {
        events.push(attack('wild', b.wild, me));
        if (me.hp <= 0) {
          events.push({ type: 'knockout', side: 'team', position: me.position });
          break;
        }
      }
    }
    if (!outcome && active().hp <= 0) {
      const next = b.team.find((m) => m.hp > 0);
      if (next) {
        b.active = next.position;
        events.push({ type: 'auto_switch', to: next.position });
      } else {
        outcome = 'defeat';
      }
    }
  }

  b.turn += 1;
  if (outcome) events.push({ type: 'end', outcome });
  b.last_round = { turn: b.turn, events };
  b.rounds.push(b.last_round);
  if (outcome) finishBattle(p, b, outcome);
  return [200, battleDto(b)];
});

/** End of a battle: XP, level-ups, reward, run progress (RunRules), history record (BattleCompletionService). */
function finishBattle(p, b, outcome) {
  const r = (runs.get(p.player_id) ?? []).find((x) => x.run_id === b.run_id);
  const list = collections.get(p.player_id);
  let xp = 0;
  const levelUps = [];
  let reward = null;
  if (outcome === 'victory') {
    xp = Math.round(10 * b.wild.level * (RARITY_XP[b.wild.rarity] ?? 1));
    for (const m of b.team) {
      const o = list.find((x) => x.owned_id === m.owned_id);
      if (!o) continue;
      const before = o.level;
      o.xp += xp;
      while (xpToNext(o.level) > 0 && o.xp >= xpToNext(o.level)) {
        o.xp -= xpToNext(o.level);
        o.level += 1;
      }
      if (o.level > before) levelUps.push({ owned_id: o.owned_id, level_before: before, level_after: o.level });
    }
    if (REWARD_PENDING) {
      reward = { owned_id: null, monster_id: b.wild.monster_id, level: b.wild.level, pending: true };
    } else {
      const o = owned(r.run_id, b.wild.monster_id, b.wild.level, b.template.is_boss ? 'boss' : 'encounter', 'active', now());
      o.encounter_id = b.encounter_id;
      list.push(o);
      reward = { owned_id: o.owned_id, monster_id: b.wild.monster_id, level: b.wild.level, pending: false };
    }
    if (!b.template.is_boss) {
      r.wins_in_stage += 1;
    } else {
      if (!r.bosses_defeated.includes(b.template.stage)) r.bosses_defeated.push(b.template.stage);
      if (b.template.stage >= FINAL_STAGE) {
        r.status = 'won';
        r.wins_in_stage += 1;
        r.ended_at = now();
      } else {
        r.stage = Math.max(r.stage, b.template.stage + 1);
        r.wins_in_stage = 0;
      }
    }
  } else if (outcome === 'defeat') {
    r.status = 'lost';
    r.ended_at = now();
  }
  b.status = 'finished';
  b.result = {
    outcome,
    reward,
    xp_gained: xp,
    level_ups: levelUps,
    run: { stage: r.stage, wins_in_stage: r.wins_in_stage, boss_available: bossAvailable(r), status: r.status },
  };
  closeBattle(p, b, outcome);
  if (outcome === 'defeat') resetRun(p);
}

/** Removes the live battle and writes the history record (HistoryRecordBuilder.java). */
function closeBattle(p, b, outcome) {
  battles.delete(p.player_id);
  const r = (runs.get(p.player_id) ?? []).find((x) => x.run_id === b.run_id);
  if (r) r.battle_in_progress = false;
  const reward = b.result?.reward ? { owned_id: b.result.reward.owned_id, monster_id: b.result.reward.monster_id, level: b.result.reward.level } : null;
  const rec = {
    encounter_id: b.encounter_id,
    run_id: b.run_id,
    template: templateRef(b.template),
    wild: { monster_id: b.wild.monster_id, name: b.wild.name, level: b.wild.level, rarity: b.wild.rarity, element: b.wild.element },
    team_snapshot: b.team.map((m) => {
      const a = abilityById.get(m.ability_id);
      return {
        position: m.position, owned_id: m.owned_id, monster_id: m.monster_id, name: m.name, level: m.level,
        element: m.element, rarity: m.rarity, stats: m.stats, ...(a ? { ability: { ability_id: a.ability_id, name: a.name } } : {}),
      };
    }),
    outcome,
    reward,
    xp_gained: b.result?.xp_gained ?? 0,
    turns: b.rounds,
    rng_seed: String(BigInt.asIntN(64, BigInt('0x' + randomBytes(8).toString('hex')))),
    engine_version: 1,
    started_at: b.started_at,
    ended_at: now(),
  };
  if (!history.has(p.player_id)) history.set(p.player_id, []);
  history.get(p.player_id).unshift(rec);
}

// ---- history (HistoryResource.java)
const HISTORY_OUTCOMES = ['victory', 'defeat', 'fled', 'expired', 'interrupted', 'abandoned'];
/** RecordSummary.java */
const summaryOf = (h) => ({
  encounter_id: h.encounter_id,
  run_id: h.run_id,
  outcome: h.outcome,
  template: h.template,
  wild: h.wild,
  reward: h.reward,
  xp_gained: h.xp_gained,
  started_at: h.started_at,
  ended_at: h.ended_at,
});
route('GET', '/api/history', (req, url) => {
  const p = auth(req);
  const runId = url.searchParams.get('run_id');
  const outcome = url.searchParams.get('outcome');
  if (outcome && !HISTORY_OUTCOMES.includes(outcome)) throw validation('Unknown outcome.', 'outcome');
  const list = (history.get(p.player_id) ?? []).filter((h) => (!runId || h.run_id === runId) && (!outcome || h.outcome === outcome));
  return [200, paginate(list.map(summaryOf), pageSpec(url))];
});
route('GET', '/api/history/:id', (req, _url, { id }) => {
  const p = auth(req);
  const h = (history.get(p.player_id) ?? []).find((x) => x.encounter_id === id);
  if (!h) throw notFound('Encounter not found.');
  return [200, recordDto(h)];
});
/** EncounterRecordResponse.java */
const recordDto = (h) => ({
  encounter_id: h.encounter_id,
  run_id: h.run_id,
  template: h.template,
  wild: h.wild,
  team_snapshot: h.team_snapshot,
  outcome: h.outcome,
  reward: h.reward,
  xp_gained: h.xp_gained,
  turns: h.turns,
  rng_seed: h.rng_seed,
  engine_version: h.engine_version,
  started_at: h.started_at,
  ended_at: h.ended_at,
});

/** Two past battles for the demo account, so /history is not empty. */
function seedDemoHistory() {
  const playerId = accounts.get('demo').player_id;
  const [current, old] = runs.get(playerId);
  const list = collections.get(playerId);
  const snap = (o, position) => {
    const m = monsterById.get(o.monster_id);
    const a = abilityById.get(m.ability_id);
    return { position, owned_id: o.owned_id, monster_id: m.monster_id, name: m.name, level: o.level, element: m.element, rarity: m.rarity, stats: fighterStats(m, o.level), ability: { ability_id: a.ability_id, name: a.name } };
  };
  const mine = list.filter((o) => o.run_id === current.run_id);
  const past = list.filter((o) => o.run_id === old.run_id);
  const t2 = templatesSeed.find((t) => t.template_id === 22);
  const w1 = monsterById.get(89);
  const w2 = monsterById.get(57);
  const a = monsterById.get(mine[0].monster_id).name;
  const b2 = monsterById.get(past[0].monster_id).name;
  const c2 = monsterById.get(past[1].monster_id).name;
  history.set(playerId, [
    {
      encounter_id: list.find((o) => o.monster_id === 89)?.encounter_id ?? randomUUID(),
      run_id: current.run_id,
      template: templateRef(templatesSeed.find((t) => t.template_id === 24)),
      wild: { monster_id: 89, name: w1.name, level: 8, rarity: w1.rarity, element: w1.element },
      team_snapshot: mine.slice(0, 3).map((o, i) => snap(o, i + 1)),
      outcome: 'victory',
      reward: { owned_id: list.find((o) => o.monster_id === 89)?.owned_id ?? null, monster_id: 89, level: 8 },
      xp_gained: 120,
      turns: [
        { turn: 1, events: [{ type: 'attack', side: 'team', attacker: a, target: w1.name, damage: 14, effectiveness: 'strong', dodged: false, abilities: [] }, { type: 'attack', side: 'wild', attacker: w1.name, target: a, damage: 9, effectiveness: 'neutral', dodged: false, abilities: [] }] },
        { turn: 2, events: [{ type: 'attack', side: 'team', attacker: a, target: w1.name, damage: 0, effectiveness: 'strong', dodged: true, abilities: ['Mist Step'] }, { type: 'attack', side: 'wild', attacker: w1.name, target: a, damage: 11, effectiveness: 'neutral', dodged: false, abilities: [] }] },
        { turn: 3, events: [{ type: 'attack', side: 'team', attacker: a, target: w1.name, damage: 31, effectiveness: 'strong', dodged: false, abilities: [] }, { type: 'knockout', side: 'wild' }, { type: 'end', outcome: 'victory' }] },
      ],
      rng_seed: '-3912874401827364451',
      engine_version: 1,
      started_at: daysAgo(1),
      ended_at: daysAgo(1),
    },
    {
      encounter_id: randomUUID(),
      run_id: old.run_id,
      template: templateRef(t2),
      wild: { monster_id: 57, name: w2.name, level: 6, rarity: w2.rarity, element: w2.element },
      team_snapshot: past.slice(0, 2).map((o, i) => snap(o, i + 1)),
      outcome: 'defeat',
      reward: null,
      xp_gained: 0,
      turns: [
        { turn: 1, events: [{ type: 'attack', side: 'wild', attacker: w2.name, target: b2, damage: 21, effectiveness: 'strong', dodged: false, abilities: [] }, { type: 'attack', side: 'team', attacker: b2, target: w2.name, damage: 6, effectiveness: 'weak', dodged: false, abilities: [] }] },
        { turn: 2, events: [{ type: 'attack', side: 'wild', attacker: w2.name, target: b2, damage: 19, effectiveness: 'strong', dodged: false, abilities: [] }, { type: 'knockout', side: 'team', position: 1 }, { type: 'auto_switch', to: 2 }] },
        { turn: 3, events: [{ type: 'flee', success: false }, { type: 'attack', side: 'wild', attacker: w2.name, target: c2, damage: 28, effectiveness: 'neutral', dodged: false, abilities: [] }, { type: 'knockout', side: 'team', position: 2 }, { type: 'end', outcome: 'defeat' }] },
      ],
      rng_seed: '8812734650012938475',
      engine_version: 1,
      started_at: daysAgo(5),
      ended_at: daysAgo(5),
    },
  ]);
}
seedDemoHistory();

// ------------------------------------------------------------------ server

const SPRITE_DIR = join(SEED, 'images', 'monsters');

const server = createServer(async (req, res) => {
  const started = Date.now();
  cors(req, res);
  const url = new URL(req.url ?? '/', `http://localhost:${PORT}`);
  const log = (status) =>
    console.log(`${new Date().toISOString().slice(11, 19)} ${req.method} ${url.pathname}${url.search} → ${status} (${Date.now() - started} ms)`);

  if (req.method === 'OPTIONS') {
    res.writeHead(204);
    res.end();
    return;
  }

  // Sprites: public, like SpriteResource.java.
  const sprite = /^\/images\/monsters\/(\d{3}\.png)$/.exec(url.pathname);
  if (req.method === 'GET' && sprite) {
    const file = join(SPRITE_DIR, sprite[1]);
    if (!existsSync(file)) {
      res.writeHead(404);
      res.end();
      log(404);
      return;
    }
    res.writeHead(200, { 'Content-Type': 'image/png', 'Cache-Control': 'public, max-age=86400' });
    createReadStream(file).pipe(res);
    log(200);
    return;
  }

  if (DELAY > 0 && url.pathname.startsWith('/api/')) await new Promise((r) => setTimeout(r, DELAY));

  try {
    for (const r of routes) {
      const m = r.method === req.method ? r.re.exec(url.pathname) : null;
      if (!m) continue;
      const params = Object.fromEntries(r.keys.map((k, i) => [k, decodeURIComponent(m[i + 1])]));
      const [status, body, headers] = await r.handler(req, url, params);
      send(res, status, body, headers);
      log(status);
      return;
    }
    throw notFound(`No route for ${req.method} ${url.pathname}.`);
  } catch (e) {
    if (e instanceof HttpError) {
      send(res, e.status, e.body);
      log(e.status);
    } else {
      console.error(e);
      send(res, 500, { error: 'INTERNAL_ERROR', message: 'Unexpected mock error.' });
      log(500);
    }
  }
});

server.listen(PORT, () => {
  console.log(`Monster Tamers mock gateway on http://localhost:${PORT}`);
  console.log(`  seed: ${SEED}`);
  console.log(`  demo account: demo / demo-tamer-1${NO_ENCOUNTERS ? '  (encounters service simulated as down)' : ''}`);
});
