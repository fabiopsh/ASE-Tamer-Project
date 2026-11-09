/**
 * API DTO types. Field names are snake_case exactly as on the wire.
 *
 * auth, players, monsters and teams mirror the Java records in
 * backend/<service>/src/main/java/it/unipi/ase/tamer/<service>/api/dto/.
 * encounters mirrors backend/encounters/.../api/dto (RunResponse, EncounterResponse, BattleStateResponse,
 * BattleResultResponse) and engine/BattleEvent.java. history mirrors backend/history/.../api/dto
 * (RecordSummary, EncounterRecordResponse).
 */

export type Uuid = string;
/** ISO-8601 UTC instant, e.g. "2026-11-06T10:15:30Z". */
export type Instant = string;

export const ELEMENTS = ['dream', 'echo', 'silence', 'memory', 'time', 'gravity', 'chaos'] as const;
export type Element = (typeof ELEMENTS)[number];

export const RARITIES = ['common', 'rare', 'legendary'] as const;
export type Rarity = (typeof RARITIES)[number];

// ---------------------------------------------------------------- common

/** PageResponse<T> (monsters/api/dto/PageResponse.java). `page` is 0-based. */
export interface Page<T> {
  items: T[];
  page: number;
  size: number;
  total_items: number;
  total_pages: number;
}

/** Error body of every non-OAuth endpoint: {error, message, details?}. */
export interface ErrorDetail {
  field?: string;
  code?: string;
}
export interface ErrorBody {
  error: string;
  message: string;
  details?: ErrorDetail[];
}

/** RFC 6749 §5.2 error of the token endpoint. */
export interface OAuthErrorBody {
  error: 'invalid_request' | 'invalid_grant' | 'unsupported_grant_type' | 'invalid_client' | string;
  error_description?: string;
}

// ---------------------------------------------------------------- auth

/** RegisterRequest.java */
export interface RegisterRequest {
  username: string;
  password: string;
}
/** RegisterResponse.java (username is the normalised lowercase form). */
export interface RegisterResponse {
  player_id: Uuid;
  username: string;
}
/** TokenResponse.java (refresh_token only from P2). */
export interface TokenResponse {
  access_token: string;
  token_type: 'Bearer' | string;
  expires_in: number;
  refresh_token?: string;
  scope?: string;
}

// ---------------------------------------------------------------- players

/** PlayerResponse.java */
export interface Player {
  player_id: Uuid;
  display_name: string;
  avatar: string | null;
  current_run_id: Uuid;
  starters_chosen: boolean;
  created_at: Instant;
}
/** StarterPoolResponse.java — 10 monster ids. */
export interface StarterPool {
  run_id: Uuid;
  pool: number[];
  chosen: boolean;
}
/** ChooseStartersRequest.java */
export interface ChooseStartersRequest {
  monster_ids: number[];
}
/** StarterEntry.java */
export interface StarterEntry {
  owned_id: Uuid;
  monster_id: number;
  level: number;
}
/** ChosenStartersResponse.java */
export interface ChosenStarters {
  run_id: Uuid;
  starters: StarterEntry[];
}

// ---------------------------------------------------------------- monsters

/** StatBlock.java */
export interface StatBlock {
  hp: number;
  attack: number;
  defense: number;
  speed: number;
}
/** GrowthBlock.java (BigDecimal → JSON number). */
export type GrowthBlock = StatBlock;

/** AbilityResponse.java */
export interface Ability {
  ability_id: number;
  name: string;
  description: string;
  scope: 'generic' | 'element' | 'legendary' | string;
  element: Element | null;
  effect_type: string;
  effect_value: number;
  trigger: string;
}

/** MonsterSummary.java — catalogue item. image_url is relative (/images/monsters/001.png). */
export interface MonsterSummary {
  monster_id: number;
  name: string;
  element: Element;
  rarity: Rarity;
  image_url: string;
}
/** MonsterDetail.java — species detail. */
export interface MonsterDetail extends MonsterSummary {
  description: string;
  base_stats: StatBlock;
  growth: GrowthBlock;
  ability: Ability;
}

export type OwnedSource = 'starter' | 'encounter' | 'boss';
export type OwnedStatus = 'active' | 'archived';

/** OwnedMonsterResponse.java — a personal instance. */
export interface OwnedMonster {
  owned_id: Uuid;
  monster_id: number;
  name: string;
  element: Element;
  rarity: Rarity;
  image_url: string;
  level: number;
  xp: number;
  xp_to_next_level: number;
  stats: StatBlock;
  ability: Ability;
  source: OwnedSource;
  encounter_id: Uuid | null;
  run_id: Uuid;
  status: OwnedStatus;
  obtained_at: Instant;
}

/** ElementsResponse.java */
export interface ElementChart {
  cycle: Element[];
  strong_against: Record<Element, Element>;
  weak_against: Record<Element, Element>;
}

export interface CatalogueQuery {
  element?: Element;
  rarity?: Rarity;
  ids?: number[];
  page?: number;
  size?: number;
}

// ---------------------------------------------------------------- teams

/** TeamMemberResponse.java */
export interface TeamMember {
  position: number;
  owned_id: Uuid;
  monster_id: number;
  name: string;
  level: number;
  element: Element;
  image_url: string;
}
/** TeamResponse.java */
export interface Team {
  team_id: Uuid;
  name: string;
  run_id: Uuid;
  members: TeamMember[];
  created_at: Instant;
  updated_at: Instant;
}
/** TeamRequest.java — members in slot order. */
export interface TeamRequest {
  name: string;
  members: Uuid[];
}
/** ValidateTeamRequest.java */
export interface ValidateTeamRequest {
  members: Uuid[];
}
export type TeamErrorCode =
  | 'TEAM_SIZE'
  | 'DUPLICATE_MEMBER'
  | 'NOT_OWNED'
  | 'NOT_ACTIVE'
  | 'MIXED_RUNS'
  | 'TEAM_LIMIT'
  | 'NAME_INVALID';
/** TeamError.java (owned_id omitted when null). */
export interface TeamError {
  code: TeamErrorCode | string;
  message: string;
  owned_id?: Uuid;
}
/** TeamValidationResponse.java */
export interface TeamValidation {
  valid: boolean;
  errors: TeamError[];
}

// ---------------------------------------------------------------- encounters (api/dto/*.java)

export type RunStatus = 'active' | 'won' | 'lost' | 'abandoned';

/** RunResponse.java — GET /api/runs/current, /api/runs/{id}, items of /api/runs. */
export interface Run {
  run_id: Uuid;
  status: RunStatus;
  stage: number;
  wins_in_stage: number;
  boss_available: boolean;
  /** Stages whose boss was beaten. */
  bosses_defeated: number[];
  /** A battle is open (GET /api/battles/current). */
  battle_in_progress: boolean;
  /** The end-of-run reset is not finished yet; the backend resumes it automatically. */
  reset_pending: boolean;
  started_at: Instant;
  ended_at: Instant | null;
}

export interface RarityWeights {
  common: number;
  rare: number;
  legendary: number;
}

/** EncounterResponse.java (GET /api/encounters). For a boss, level_min = level_max = boss level. */
export interface EncounterTemplate {
  template_id: number;
  name: string;
  stage: number;
  element: Element;
  is_boss: boolean;
  boss_monster_id?: number | null;
  level_min: number;
  level_max: number;
  rarity_weights: RarityWeights;
}

export interface TemplateRef {
  template_id: number;
  name: string;
  stage: number;
  element: Element;
  is_boss: boolean;
}

export interface WildMonster {
  monster_id: number;
  name: string;
  element: Element;
  rarity: Rarity;
  level: number;
  hp: number;
  max_hp: number;
  image_url: string;
  /** BattleView.AbilityView; null when the species has no ability data. */
  ability: { name: string; description: string } | null;
}

export interface BattleMember {
  position: number;
  owned_id: Uuid;
  monster_id: number;
  name: string;
  level: number;
  element: Element;
  hp: number;
  max_hp: number;
  active: boolean;
  knocked_out: boolean;
  image_url: string;
}

export type BattleSide = 'team' | 'wild';
export type Effectiveness = 'strong' | 'weak' | 'neutral' | string;

export type BattleEvent =
  | {
      type: 'attack';
      side: BattleSide;
      attacker: string;
      target: string;
      damage: number;
      effectiveness: Effectiveness;
      dodged: boolean;
      /** Names of the abilities that changed this attack. */
      abilities?: string[];
    }
  | { type: 'knockout'; side: BattleSide; position?: number }
  | { type: 'auto_switch'; to: number }
  | { type: 'switch'; side?: BattleSide; from: number; to: number }
  | { type: 'flee'; success: boolean }
  | { type: 'ability'; side?: BattleSide; ability?: string; description?: string; [key: string]: unknown }
  | { type: 'end'; outcome: BattleOutcome };

export interface Round {
  turn: number;
  events: BattleEvent[];
}

export type BattleOutcome = 'victory' | 'defeat' | 'fled' | 'expired' | 'interrupted' | 'abandoned';

export interface LevelUp {
  owned_id: Uuid;
  level_before: number;
  level_after: number;
}

/** BattleResultResponse.java. outcome of a live battle: victory | defeat | fled. */
export interface BattleResult {
  outcome: BattleOutcome;
  reward: { owned_id: Uuid | null; monster_id: number; level: number; pending: boolean } | null;
  xp_gained: number;
  level_ups: LevelUp[];
  run: { stage: number; wins_in_stage: number; boss_available: boolean; status: RunStatus };
}

/** BattleStateResponse.java (POST /api/battles, GET /api/battles/current, POST .../actions). */
export interface Battle {
  encounter_id: Uuid;
  status: 'in_progress' | 'finished';
  turn: number;
  template: TemplateRef;
  wild: WildMonster;
  team: BattleMember[];
  last_round: Round | null;
  result: BattleResult | null;
}

export interface StartBattleRequest {
  template_id: number;
  team_id: Uuid;
}

export type BattleAction = { type: 'attack' } | { type: 'flee' } | { type: 'switch'; slot: number };

// ---------------------------------------------------------------- history (backend/history/.../api/dto)

/**
 * RecordSummary.java. `wild` and `reward` are stored as sent by encounters
 * (HistoryRecordBuilder.java): wild {monster_id, name, level, rarity, element}, reward {owned_id, monster_id, level}.
 */
export interface HistorySummary {
  encounter_id: Uuid;
  run_id: Uuid;
  template: TemplateRef;
  wild: { monster_id: number; name: string; level: number; rarity: Rarity; element: Element };
  outcome: BattleOutcome;
  reward: { owned_id: Uuid | null; monster_id: number; level: number } | null;
  xp_gained: number;
  started_at: Instant;
  ended_at: Instant;
}

/** One entry of team_snapshot (HistoryRecordBuilder.team): stats and ability may be missing. */
export interface HistoryTeamEntry {
  position: number;
  owned_id: Uuid;
  monster_id: number;
  name: string;
  level: number;
  element?: Element;
  rarity?: Rarity;
  stats?: StatBlock;
  ability?: { ability_id: number; name: string };
}

/** EncounterRecordResponse.java. team_snapshot and turns are empty for an interrupted battle. */
export interface HistoryRecord extends HistorySummary {
  team_snapshot: HistoryTeamEntry[];
  turns: Round[];
  /** String: 64-bit values exceed JavaScript's safe integer range. Missing when unknown. */
  rng_seed?: string | null;
  engine_version: number;
}

export interface HistoryQuery {
  run_id?: Uuid;
  outcome?: BattleOutcome;
  page?: number;
  size?: number;
}
