/** players service: /api/players/me* (docs/plan/04-players.md). */
import { request } from './client';
import type { ChosenStarters, Player, StarterPool } from './types';

export function getMe(signal?: AbortSignal): Promise<Player> {
  return request<Player>('/api/players/me', { signal });
}

export function getStarterPool(signal?: AbortSignal): Promise<StarterPool> {
  return request<StarterPool>('/api/players/me/starters', { signal });
}

/** Exactly 3 ids from the pool. Errors: 400 INVALID_STARTERS, 409 STARTERS_ALREADY_CHOSEN, 503. */
export function chooseStarters(monsterIds: number[]): Promise<ChosenStarters> {
  return request<ChosenStarters>('/api/players/me/starters', {
    method: 'POST',
    body: { monster_ids: monsterIds },
  });
}
