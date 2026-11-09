import { createContext, useContext } from 'react';
import type { Player } from '../api/types';

export type PlayerStatus = 'anonymous' | 'loading' | 'ready' | 'error';

export interface SessionValue {
  /** True when an access token is stored. */
  isAuthenticated: boolean;
  /** Profile from GET /api/players/me, null until loaded. */
  player: Player | null;
  playerStatus: PlayerStatus;
  playerError: unknown;
  /** Password grant; stores the token. The guard then loads the profile. */
  login: (username: string, password: string) => Promise<void>;
  /** Register then log in. */
  register: (username: string, password: string) => Promise<void>;
  /** P1: clears local storage only. */
  logout: () => void;
  /** Reload GET /api/players/me (e.g. after choosing starters or a new run). */
  refreshPlayer: () => Promise<Player | null>;
}

export const SessionContext = createContext<SessionValue | null>(null);

export function useSession(): SessionValue {
  const value = useContext(SessionContext);
  if (!value) throw new Error('useSession must be used inside <SessionProvider>.');
  return value;
}
