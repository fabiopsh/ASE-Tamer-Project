/**
 * Single-flight, cross-tab serialized refresh-token coordinator and server logout (FE-3).
 *
 * Guarantees:
 * - Concurrent 401s or pre-flight expired tokens in the same tab share ONE in-flight promise.
 * - Cross-tab serialization via navigator.locks (with safe fallback when unavailable) plus
 *   re-reading localStorage inside the lock so a tab never submits an already-rotated token.
 * - Monotonic sessionEpoch prevents a stale in-flight refresh from restoring a logged-out session.
 * - Rotated access_token + refresh_token are saved atomically BEFORE callers retry.
 * - Never auto-retries a refresh after an ambiguous network / 5xx failure.
 */
import * as authApi from '../api/auth';
import { isApiError } from '../api/client';
import {
  clearLastTeam,
  clearSession,
  hasRefreshToken,
  isAccessTokenValid,
  markSessionExpired,
  readSession,
  writeSession,
  type StoredSession,
} from './storage';

const LOCK_NAME = 'tamer.session.refresh';

let sessionEpoch = 0;
let inFlightRefresh: Promise<string | null> | null = null;

export interface RefreshCallbacks {
  onRefreshed?: (session: StoredSession) => void;
  onExpired?: () => void;
}

let callbacks: RefreshCallbacks = {};

export function configureRefreshCoordinator(next: RefreshCallbacks): void {
  callbacks = next;
}

export function getSessionEpoch(): number {
  return sessionEpoch;
}

export function incrementSessionEpoch(): number {
  sessionEpoch += 1;
  return sessionEpoch;
}

/** Reset coordinator state (used in unit tests). */
export function resetRefreshCoordinator(): void {
  sessionEpoch += 1;
  inFlightRefresh = null;
  callbacks = {};
}

async function withCrossTabLock<T>(fn: () => Promise<T>): Promise<T> {
  const locks = typeof navigator !== 'undefined' ? navigator.locks : undefined;
  if (locks && typeof locks.request === 'function') {
    return locks.request(LOCK_NAME, fn);
  }
  return fn();
}

async function executeRefresh(failedAccessToken?: string): Promise<string | null> {
  return withCrossTabLock(async () => {
    const epochAtStart = sessionEpoch;
    const latest = readSession();
    if (!latest) return null;

    // Another tab or earlier request already rotated the session to a fresh token.
    if (
      isAccessTokenValid(latest) &&
      (failedAccessToken === undefined || latest.access_token !== failedAccessToken)
    ) {
      callbacks.onRefreshed?.(latest);
      return latest.access_token;
    }

    if (!hasRefreshToken(latest)) {
      return null;
    }

    try {
      const res = await authApi.refreshToken(latest.refresh_token!);
      // A logout (or fresh login) ran while this refresh was in flight: discard stale tokens.
      if (sessionEpoch !== epochAtStart) {
        return null;
      }
      const saved = writeSession(res);
      callbacks.onRefreshed?.(saved);
      return saved.access_token;
    } catch (err) {
      if (sessionEpoch !== epochAtStart) {
        return null;
      }
      // Confirmed token rejection (invalid_grant / revoked / reused): end local session.
      if (isApiError(err) && (err.status === 400 || err.status === 401)) {
        clearSession();
        clearLastTeam();
        markSessionExpired();
        callbacks.onExpired?.();
        return null;
      }
      // Ambiguous network or 5xx error: do not auto-retry, let the caller surface it.
      throw err;
    }
  });
}

/**
 * Ensures a valid access token by refreshing if needed, sharing one in-flight promise
 * across concurrent callers in the same tab.
 */
export function refreshSession(failedAccessToken?: string): Promise<string | null> {
  if (inFlightRefresh) {
    return inFlightRefresh;
  }
  const promise = executeRefresh(failedAccessToken).finally(() => {
    if (inFlightRefresh === promise) {
      inFlightRefresh = null;
    }
  });
  inFlightRefresh = promise;
  return promise;
}

export interface LogoutResult {
  serverRevoked: boolean;
  error: unknown;
}

/**
 * Increments sessionEpoch, revokes the current refresh token on the server if present,
 * and ALWAYS clears local session and cached team selection.
 */
export async function performLogout(): Promise<LogoutResult> {
  incrementSessionEpoch();
  const refreshToken = readSession()?.refresh_token;
  clearSession();
  clearLastTeam();

  if (!refreshToken) {
    return { serverRevoked: true, error: null };
  }

  try {
    await authApi.logoutServer(refreshToken);
    return { serverRevoked: true, error: null };
  } catch (error) {
    return { serverRevoked: false, error };
  }
}
