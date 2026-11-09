import { createContext, useContext } from 'react';
import type { Battle, Run } from '../api/types';

/**
 * - loading: first request in flight
 * - ready: GET /api/runs/current returned a run
 * - none: 404 NO_ACTIVE_RUN (e.g. starters not chosen yet)
 * - unavailable: the encounters service did not answer (not deployed yet, 5xx, network)
 */
export type RunState =
  | { status: 'loading' }
  | { status: 'ready'; run: Run }
  | { status: 'none' }
  | { status: 'unavailable'; error: unknown };

export interface GameStatusValue {
  run: RunState;
  /** Battle in progress (GET /api/battles/current), null if none or unavailable. */
  battle: Battle | null;
  /** Re-read run and battle, e.g. after a battle round or a run change. */
  refresh: () => void;
}

export const GameStatusContext = createContext<GameStatusValue | null>(null);

export function useGameStatus(): GameStatusValue {
  const value = useContext(GameStatusContext);
  if (!value) throw new Error('useGameStatus must be used inside <GameStatusProvider>.');
  return value;
}
