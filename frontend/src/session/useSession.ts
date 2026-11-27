import { createContext, useContext } from 'react';
import type { Player } from '../api/types';

export type PlayerStatus = 'anonymous' | 'loading' | 'ready' | 'error';

export interface SessionValue {
  /** True when a valid access token is active. */
  isAuthenticated: boolean;
  /** True while restoring an expired session via refresh_token on initial mount. */
  restoring: boolean;
  /** Profile from GET /api/players/me, null until loaded. */
  player: Player | null;
  playerStatus: PlayerStatus;
  playerError: unknown;
  /** Password grant; stores the tokens. The guard then loads the profile. */
  login: (username: string, password: string) => Promise<void>;
  /** Register then log in. Throws ApiError with code REGISTERED_LOGIN_FAILED if only login fails. */
  register: (username: string, password: string) => Promise<void>;
  /** Revokes the refresh token on the server and always clears local session state. */
  logout: () => Promise<void>;
  /** Reload GET /api/players/me (e.g. after choosing starters or a new run). */
  refreshPlayer: () => Promise<Player | null>;
  /** Update the in-memory player immediately after PATCH /api/players/me. */
  updatePlayer: (player: Player) => void;
}

export const SessionContext = createContext<SessionValue | null>(null);

export function useSession(): SessionValue {
  const value = useContext(SessionContext);
  if (!value) throw new Error('useSession must be used inside <SessionProvider>.');
  return value;
}
