import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import {
  clearLastTeam,
  clearSession,
  hasRefreshToken,
  isAccessTokenValid,
  LAST_TEAM_STORAGE_KEY,
  readSession,
  SESSION_STORAGE_KEY,
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

describe('session/storage', () => {
  beforeEach(() => {
    vi.stubGlobal('localStorage', createMemoryStorage());
    clearSession();
  });

  afterEach(() => {
    clearSession();
    vi.unstubAllGlobals();
    vi.restoreAllMocks();
  });

  it('writes and reads access_token, expires_at and refresh_token atomically', () => {
    const written = writeSession({
      access_token: 'at-1',
      token_type: 'Bearer',
      expires_in: 300,
      refresh_token: 'rt-1',
    });

    const raw = localStorage.getItem(SESSION_STORAGE_KEY);
    expect(raw).not.toBeNull();
    expect(JSON.parse(raw!)).toEqual(written);

    const read = readSession();
    expect(read).toEqual(written);
    expect(isAccessTokenValid(read)).toBe(true);
    expect(hasRefreshToken(read)).toBe(true);
  });

  it('preserves expired access_token session when a refresh_token is present', () => {
    const now = Date.now();
    localStorage.setItem(
      SESSION_STORAGE_KEY,
      JSON.stringify({
        access_token: 'expired-at',
        expires_at: now - 5_000,
        refresh_token: 'rt-still-valid',
      }),
    );

    const session = readSession();
    expect(session).not.toBeNull();
    expect(session?.refresh_token).toBe('rt-still-valid');
    expect(isAccessTokenValid(session, now)).toBe(false);
    expect(hasRefreshToken(session)).toBe(true);
    expect(localStorage.getItem(SESSION_STORAGE_KEY)).not.toBeNull();
  });

  it('treats access tokens inside the 15 s skew buffer as invalid for proactive refresh', () => {
    const now = Date.now();
    const session = {
      access_token: 'soon-expiring',
      expires_at: now + 10_000,
      refresh_token: 'rt-1',
    };
    expect(isAccessTokenValid(session, now)).toBe(false);
    expect(isAccessTokenValid({ ...session, expires_at: now + 30_000 }, now)).toBe(true);
  });

  it('clears expired session when no refresh_token is present', () => {
    localStorage.setItem(
      SESSION_STORAGE_KEY,
      JSON.stringify({
        access_token: 'expired-no-rt',
        expires_at: Date.now() - 1_000,
      }),
    );

    expect(readSession()).toBeNull();
    expect(localStorage.getItem(SESSION_STORAGE_KEY)).toBeNull();
  });

  it('falls back to in-memory session seamlessly when localStorage throws (private mode)', () => {
    const throwingStorage: Storage = {
      length: 0,
      clear: () => {
        throw new Error('QuotaExceededError');
      },
      getItem: () => {
        throw new Error('SecurityError');
      },
      key: () => null,
      removeItem: () => {
        throw new Error('SecurityError');
      },
      setItem: () => {
        throw new Error('QuotaExceededError');
      },
    };
    vi.stubGlobal('localStorage', throwingStorage);

    const written = writeSession({
      access_token: 'mem-at',
      token_type: 'Bearer',
      expires_in: 300,
      refresh_token: 'mem-rt',
    });

    const read = readSession();
    expect(read).toEqual(written);
    expect(isAccessTokenValid(read)).toBe(true);

    clearSession();
    expect(readSession()).toBeNull();
  });

  it('clears tamer.lastTeam via clearLastTeam()', () => {
    localStorage.setItem(LAST_TEAM_STORAGE_KEY, 'team-123');
    clearLastTeam();
    expect(localStorage.getItem(LAST_TEAM_STORAGE_KEY)).toBeNull();
  });
});
