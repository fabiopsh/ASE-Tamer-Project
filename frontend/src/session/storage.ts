/**
 * Token storage in localStorage (P-17), key `tamer.session`:
 * {access_token, expires_at, refresh_token?}. expires_at is epoch milliseconds.
 */
import type { TokenResponse } from '../api/types';

const KEY = 'tamer.session';

export interface StoredSession {
  access_token: string;
  expires_at: number;
  refresh_token?: string;
}

export function readSession(): StoredSession | null {
  try {
    const raw = localStorage.getItem(KEY);
    if (!raw) return null;
    const parsed = JSON.parse(raw) as Partial<StoredSession>;
    if (typeof parsed.access_token !== 'string' || typeof parsed.expires_at !== 'number') return null;
    if (parsed.expires_at <= Date.now()) {
      clearSession();
      return null;
    }
    return parsed as StoredSession;
  } catch {
    return null;
  }
}

export function writeSession(token: TokenResponse): StoredSession {
  const session: StoredSession = {
    access_token: token.access_token,
    expires_at: Date.now() + token.expires_in * 1000,
    ...(token.refresh_token ? { refresh_token: token.refresh_token } : {}),
  };
  try {
    localStorage.setItem(KEY, JSON.stringify(session));
  } catch {
    // Storage unavailable (private mode): the session lives only in memory.
  }
  return session;
}

export function clearSession(): void {
  try {
    localStorage.removeItem(KEY);
  } catch {
    // ignore
  }
}

/** Set when a 401 ended the session, read by the login page to explain why. */
let expiredFlag = false;
export function markSessionExpired(): void {
  expiredFlag = true;
}
export function wasSessionExpired(): boolean {
  return expiredFlag;
}
export function clearSessionExpired(): void {
  expiredFlag = false;
}
