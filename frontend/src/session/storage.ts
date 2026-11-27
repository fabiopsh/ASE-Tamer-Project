/**
 * Token storage in localStorage (P-17), key `tamer.session`:
 * {access_token, expires_at, refresh_token?}. expires_at is epoch milliseconds.
 *
 * Keeps an in-memory mirror so private-browsing mode (where localStorage throws)
 * works seamlessly, while always reading from localStorage when available so
 * multi-tab token rotations and logouts are observed immediately.
 */
import type { TokenResponse } from '../api/types';

export const SESSION_STORAGE_KEY = 'tamer.session';
export const LAST_TEAM_STORAGE_KEY = 'tamer.lastTeam';

/** Skew buffer (15 s) before access_token expiry to proactively refresh. */
export const ACCESS_TOKEN_SKEW_MS = 15_000;

export interface StoredSession {
  access_token: string;
  expires_at: number;
  refresh_token?: string;
}

let memorySession: StoredSession | null = null;

function isStoredSession(value: unknown): value is StoredSession {
  if (!value || typeof value !== 'object') return false;
  const v = value as Partial<StoredSession>;
  return (
    typeof v.access_token === 'string' &&
    v.access_token.length > 0 &&
    typeof v.expires_at === 'number' &&
    Number.isFinite(v.expires_at) &&
    (v.refresh_token === undefined || typeof v.refresh_token === 'string')
  );
}

/** True when the session carries an access token with at least `skewMs` remaining. */
export function isAccessTokenValid(
  session: StoredSession | null | undefined,
  now = Date.now(),
  skewMs = ACCESS_TOKEN_SKEW_MS,
): boolean {
  if (!session) return false;
  return session.expires_at > now + skewMs;
}

/** True when the session holds a non-empty refresh token that can renew access. */
export function hasRefreshToken(session: StoredSession | null | undefined): boolean {
  return Boolean(session && typeof session.refresh_token === 'string' && session.refresh_token.length > 0);
}

/**
 * Reads the stored session without deleting expired access tokens when a refresh_token is present.
 * Falls back to the in-memory mirror if localStorage is unavailable (e.g. private browsing).
 */
export function readSession(): StoredSession | null {
  let raw: string | null;
  let storageAvailable = true;
  try {
    raw = localStorage.getItem(SESSION_STORAGE_KEY);
  } catch {
    storageAvailable = false;
    raw = null;
  }

  let session: StoredSession | null;
  if (storageAvailable) {
    if (!raw) {
      memorySession = null;
      return null;
    }
    try {
      const parsed: unknown = JSON.parse(raw);
      if (!isStoredSession(parsed)) {
        memorySession = null;
        return null;
      }
      session = parsed;
      memorySession = session;
    } catch {
      memorySession = null;
      return null;
    }
  } else {
    session = memorySession;
  }

  if (!session) return null;

  // Only drop an expired session when there is no refresh token to renew it.
  if (session.expires_at <= Date.now() && !hasRefreshToken(session)) {
    clearSession();
    return null;
  }

  return session;
}

/** Atomically stores access_token, expires_at and optional refresh_token in memory and localStorage. */
export function writeSession(token: TokenResponse): StoredSession {
  const session: StoredSession = {
    access_token: token.access_token,
    expires_at: Date.now() + token.expires_in * 1000,
    ...(token.refresh_token ? { refresh_token: token.refresh_token } : {}),
  };
  memorySession = session;
  try {
    localStorage.setItem(SESSION_STORAGE_KEY, JSON.stringify(session));
  } catch {
    // Storage unavailable (private mode): the session lives in memorySession.
  }
  return session;
}

export function clearSession(): void {
  memorySession = null;
  try {
    localStorage.removeItem(SESSION_STORAGE_KEY);
  } catch {
    // ignore
  }
}

export function clearLastTeam(): void {
  try {
    localStorage.removeItem(LAST_TEAM_STORAGE_KEY);
  } catch {
    // ignore
  }
}

/** Set when a 401 or invalid refresh ended the session, read by the login page to explain why. */
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
