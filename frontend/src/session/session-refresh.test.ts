import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ApiError, configureClient, request } from '../api/client';
import {
  configureRefreshCoordinator,
  performLogout,
  refreshSession,
  resetRefreshCoordinator,
} from './refresh';
import {
  clearSession,
  isAccessTokenValid,
  LAST_TEAM_STORAGE_KEY,
  readSession,
  writeSession,
} from './storage';

function createMemoryStorage(): Storage {
  const store = new Map<string, string>();
  return {
    get length() {
      return store.size;
    },
    clear() {
      store.clear();
    },
    getItem(key: string) {
      return store.has(key) ? store.get(key)! : null;
    },
    key(index: number) {
      return Array.from(store.keys())[index] ?? null;
    },
    removeItem(key: string) {
      store.delete(key);
    },
    setItem(key: string, value: string) {
      store.set(key, String(value));
    },
  };
}

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(body === undefined ? null : JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}

describe('FE-3 refresh token rotation & server logout', () => {
  let onUnauthorized: ReturnType<typeof vi.fn<() => void>>;
  let onRefreshed: ReturnType<typeof vi.fn<() => void>>;
  let onExpired: ReturnType<typeof vi.fn<() => void>>;

  beforeEach(() => {
    vi.stubGlobal('localStorage', createMemoryStorage());
    clearSession();
    resetRefreshCoordinator();

    onUnauthorized = vi.fn();
    onRefreshed = vi.fn();
    onExpired = vi.fn();

    configureRefreshCoordinator({ onRefreshed, onExpired });
    configureClient({
      getToken: () => {
        const s = readSession();
        return s && isAccessTokenValid(s) ? s.access_token : null;
      },
      refreshSession: (failedToken) => refreshSession(failedToken),
      onUnauthorized,
    });
  });

  afterEach(() => {
    clearSession();
    resetRefreshCoordinator();
    vi.unstubAllGlobals();
    vi.restoreAllMocks();
  });

  it('serializes concurrent 401s into a single refresh request and retries each caller once with rotated token', async () => {
    writeSession({
      access_token: 'at-old',
      token_type: 'Bearer',
      expires_in: 300,
      refresh_token: 'rt-1',
    });

    let refreshCalls = 0;
    const fetchMock = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const url = String(input);
      const headers = (init?.headers ?? {}) as Record<string, string>;

      if (url.endsWith('/api/auth/token')) {
        refreshCalls += 1;
        // Must NOT carry Authorization header on refresh endpoint.
        expect(headers.Authorization).toBeUndefined();
        expect(String(init?.body)).toContain('grant_type=refresh_token');
        expect(String(init?.body)).toContain('refresh_token=rt-1');
        // Small async delay so both 401s overlap.
        await new Promise((r) => setTimeout(r, 15));
        return jsonResponse({
          access_token: 'at-new',
          token_type: 'Bearer',
          expires_in: 300,
          refresh_token: 'rt-2',
        });
      }

      if (headers.Authorization === 'Bearer at-old') {
        return jsonResponse({ error: 'UNAUTHORIZED', message: 'Expired' }, 401);
      }
      if (headers.Authorization === 'Bearer at-new') {
        // Rotated refresh token must already be persisted before retrying!
        expect(readSession()?.refresh_token).toBe('rt-2');
        return jsonResponse({ ok: url });
      }
      return jsonResponse({ error: 'UNEXPECTED' }, 500);
    });
    vi.stubGlobal('fetch', fetchMock);

    const [a, b] = await Promise.all([
      request<{ ok: string }>('/api/players/me'),
      request<{ ok: string }>('/api/teams'),
    ]);

    expect(refreshCalls).toBe(1);
    expect(a.ok).toContain('/api/players/me');
    expect(b.ok).toContain('/api/teams');
    expect(readSession()?.access_token).toBe('at-new');
    expect(readSession()?.refresh_token).toBe('rt-2');
    expect(onUnauthorized).not.toHaveBeenCalled();
  });

  it('never refreshes on 403 Forbidden', async () => {
    writeSession({
      access_token: 'at-valid',
      token_type: 'Bearer',
      expires_in: 300,
      refresh_token: 'rt-1',
    });

    const fetchMock = vi.fn(async () => jsonResponse({ error: 'FORBIDDEN', message: 'No access' }, 403));
    vi.stubGlobal('fetch', fetchMock);

    await expect(request('/api/teams')).rejects.toMatchObject({ status: 403, code: 'FORBIDDEN' });
    expect(fetchMock).toHaveBeenCalledTimes(1);
    expect(onUnauthorized).not.toHaveBeenCalled();
  });

  it('does not loop when the single retried request still returns 401', async () => {
    writeSession({
      access_token: 'at-1',
      token_type: 'Bearer',
      expires_in: 300,
      refresh_token: 'rt-1',
    });

    let refreshCalls = 0;
    const fetchMock = vi.fn(async (input: RequestInfo | URL) => {
      const url = String(input);
      if (url.endsWith('/api/auth/token')) {
        refreshCalls += 1;
        return jsonResponse({
          access_token: 'at-2',
          token_type: 'Bearer',
          expires_in: 300,
          refresh_token: 'rt-2',
        });
      }
      return jsonResponse({ error: 'UNAUTHORIZED', message: 'Still rejected' }, 401);
    });
    vi.stubGlobal('fetch', fetchMock);

    await expect(request('/api/players/me')).rejects.toBeInstanceOf(ApiError);
    expect(refreshCalls).toBe(1);
    expect(onUnauthorized).toHaveBeenCalledTimes(1);
  });

  it('discards a stale in-flight refresh response if logout runs before it completes', async () => {
    writeSession({
      access_token: 'at-expired',
      token_type: 'Bearer',
      expires_in: -10,
      refresh_token: 'rt-1',
    });

    let resolveRefresh!: (res: Response) => void;
    const fetchMock = vi.fn((input: RequestInfo | URL) => {
      const url = String(input);
      if (url.endsWith('/api/auth/token')) {
        return new Promise<Response>((resolve) => {
          resolveRefresh = resolve;
        });
      }
      if (url.endsWith('/api/auth/logout')) {
        return Promise.resolve(new Response(null, { status: 204 }));
      }
      return Promise.resolve(jsonResponse({}));
    });
    vi.stubGlobal('fetch', fetchMock);

    const refreshPromise = refreshSession();
    // Wait a tick so the refresh fetch is in flight.
    await new Promise((r) => setTimeout(r, 0));

    // User logs out while refresh is still pending.
    const logoutResult = await performLogout();
    expect(logoutResult.serverRevoked).toBe(true);
    expect(readSession()).toBeNull();

    // Now the stale refresh resolves with tokens.
    resolveRefresh(
      jsonResponse({
        access_token: 'at-stale',
        token_type: 'Bearer',
        expires_in: 300,
        refresh_token: 'rt-stale',
      }),
    );

    const token = await refreshPromise;
    expect(token).toBeNull();
    expect(readSession()).toBeNull();
    expect(onRefreshed).not.toHaveBeenCalled();
  });

  it('reuses a token already rotated by another tab without submitting the consumed refresh token', async () => {
    writeSession({
      access_token: 'at-tab2-fresh',
      token_type: 'Bearer',
      expires_in: 300,
      refresh_token: 'rt-2',
    });

    const fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);

    const token = await refreshSession('at-tab1-old');
    expect(token).toBe('at-tab2-fresh');
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('always clears local session and tamer.lastTeam even when server logout fails, and reports revocation failure', async () => {
    writeSession({
      access_token: 'at-1',
      token_type: 'Bearer',
      expires_in: 300,
      refresh_token: 'rt-1',
    });
    localStorage.setItem(LAST_TEAM_STORAGE_KEY, 'team-abc');

    const fetchMock = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      expect(String(input)).toContain('/api/auth/logout');
      const headers = (init?.headers ?? {}) as Record<string, string>;
      expect(headers.Authorization).toBeUndefined();
      return jsonResponse({ error: 'INTERNAL', message: 'Auth down' }, 503);
    });
    vi.stubGlobal('fetch', fetchMock);

    const result = await performLogout();
    expect(result.serverRevoked).toBe(false);
    expect(result.error).toBeInstanceOf(ApiError);
    expect(readSession()).toBeNull();
    expect(localStorage.getItem(LAST_TEAM_STORAGE_KEY)).toBeNull();
  });
});
