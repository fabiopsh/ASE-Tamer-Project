/** encounters service: encounters, runs, battles (docs/plan/07-encounters.md, backend/encounters/.../api). */
import { request, requestOrNull } from './client';
import type { Battle, BattleAction, EncounterTemplate, Page, Run, StartBattleRequest, Uuid } from './types';

export function listEncounters(signal?: AbortSignal): Promise<EncounterTemplate[]> {
  return request<EncounterTemplate[]>('/api/encounters', { signal });
}

export function getEncounter(templateId: number, signal?: AbortSignal): Promise<EncounterTemplate> {
  return request<EncounterTemplate>(`/api/encounters/${templateId}`, { signal });
}

export function listRuns(q: { page?: number; size?: number } = {}, signal?: AbortSignal): Promise<Page<Run>> {
  return request<Page<Run>>('/api/runs', { query: { page: q.page, size: q.size }, signal });
}

/** Active run (or the latest won run awaiting a new one); null on 404 NO_ACTIVE_RUN. */
export function getCurrentRun(signal?: AbortSignal): Promise<Run | null> {
  return requestOrNull<Run>('/api/runs/current', { signal });
}

export function getRun(runId: Uuid, signal?: AbortSignal): Promise<Run> {
  return request<Run>(`/api/runs/${encodeURIComponent(runId)}`, { signal });
}

export function abandonCurrentRun(): Promise<Run> {
  return request<Run>('/api/runs/current/abandon', { method: 'POST' });
}

/** After a won run: reset and wait for new starters. Answers 202 {status: "choose_starters"}. */
export function startNewRun(): Promise<{ status: string }> {
  return request<{ status: string }>('/api/runs', { method: 'POST' });
}

/** 409 BATTLE_IN_PROGRESS: redirect to /battle. */
export function startBattle(body: StartBattleRequest): Promise<Battle> {
  return request<Battle>('/api/battles', { method: 'POST', body });
}

/** Current battle, or null (404). */
export function getCurrentBattle(signal?: AbortSignal): Promise<Battle | null> {
  return requestOrNull<Battle>('/api/battles/current', { signal });
}

/** Resolves one full round. 400 INVALID_ACTION for a bad switch. */
export function sendBattleAction(action: BattleAction): Promise<Battle> {
  return request<Battle>('/api/battles/current/actions', { method: 'POST', body: action });
}
