import { useCallback, useEffect, useMemo, useState, type ReactNode } from 'react';
import { useNavigate } from 'react-router-dom';
import * as authApi from '../api/auth';
import { ApiError, configureClient, isApiError } from '../api/client';
import * as playersApi from '../api/players';
import type { Player } from '../api/types';
import { useToast } from '../components/toast-context';
import {
  configureRefreshCoordinator,
  incrementSessionEpoch,
  performLogout,
  refreshSession,
} from './refresh';
import {
  clearLastTeam,
  clearSession,
  hasRefreshToken,
  isAccessTokenValid,
  markSessionExpired,
  readSession,
  SESSION_STORAGE_KEY,
  writeSession,
} from './storage';
import { SessionContext, type PlayerStatus, type SessionValue } from './useSession';

function initialSessionState(): { token: string | null; needsRestore: boolean } {
  const stored = readSession();
  if (!stored) return { token: null, needsRestore: false };
  if (isAccessTokenValid(stored)) return { token: stored.access_token, needsRestore: false };
  if (hasRefreshToken(stored)) return { token: null, needsRestore: true };
  return { token: null, needsRestore: false };
}

/**
 * Session state: access + refresh tokens (`tamer.session`) and the player profile.
 * Must be rendered inside the router and ToastProvider.
 */
export function SessionProvider({ children }: { children: ReactNode }) {
  const navigate = useNavigate();
  const toast = useToast();

  const [initial] = useState(initialSessionState);
  const [token, setToken] = useState<string | null>(initial.token);
  const [restoring, setRestoring] = useState<boolean>(initial.needsRestore);
  const [player, setPlayer] = useState<Player | null>(null);
  const [playerError, setPlayerError] = useState<unknown>(null);

  const expireSession = useCallback(() => {
    incrementSessionEpoch();
    clearSession();
    clearLastTeam();
    markSessionExpired();
    setToken(null);
    setRestoring(false);
    setPlayer(null);
    setPlayerError(null);
    navigate('/login', { replace: true });
  }, [navigate]);

  // Wire client token provider, single-flight refresh coordinator and 401 handler.
  useEffect(() => {
    configureRefreshCoordinator({
      onRefreshed: (session) => {
        setToken(session.access_token);
      },
      onExpired: () => {
        setToken(null);
        setRestoring(false);
        setPlayer(null);
        setPlayerError(null);
        navigate('/login', { replace: true });
      },
    });
    configureClient({
      getToken: () => {
        const s = readSession();
        return s && isAccessTokenValid(s) ? s.access_token : null;
      },
      refreshSession: (failedToken) => refreshSession(failedToken),
      onUnauthorized: expireSession,
    });
  }, [expireSession, navigate]);

  // Attempt session restoration on mount when access_token is expired but refresh_token exists.
  useEffect(() => {
    if (!initial.needsRestore) return;
    let active = true;
    refreshSession()
      .then((newToken) => {
        if (!active) return;
        if (newToken) {
          setToken(newToken);
        } else {
          setToken(null);
        }
        setRestoring(false);
      })
      .catch(() => {
        if (!active) return;
        setToken(null);
        setRestoring(false);
      });
    return () => {
      active = false;
    };
  }, [initial.needsRestore]);

  // Multi-tab sync: react immediately when another tab rotates tokens or logs out.
  useEffect(() => {
    function onStorage(e: StorageEvent) {
      if (e.key !== null && e.key !== SESSION_STORAGE_KEY) return;
      const latest = readSession();
      if (!latest) {
        incrementSessionEpoch();
        setToken(null);
        setRestoring(false);
        setPlayer(null);
        setPlayerError(null);
        navigate('/login', { replace: true });
        return;
      }
      if (isAccessTokenValid(latest)) {
        setToken((prev) => (prev === latest.access_token ? prev : latest.access_token));
        setRestoring(false);
      }
    }
    window.addEventListener('storage', onStorage);
    return () => window.removeEventListener('storage', onStorage);
  }, [navigate]);

  // Load the profile whenever a token exists and the profile is not loaded yet.
  useEffect(() => {
    if (!token || player || playerError) return;
    const ctrl = new AbortController();
    playersApi
      .getMe(ctrl.signal)
      .then(setPlayer)
      .catch((e: unknown) => {
        if (e instanceof DOMException && e.name === 'AbortError') return;
        setPlayerError(e);
      });
    return () => ctrl.abort();
  }, [token, player, playerError]);

  const login = useCallback(async (username: string, password: string) => {
    incrementSessionEpoch();
    const res = await authApi.login(username, password);
    writeSession(res);
    setRestoring(false);
    setPlayer(null);
    setPlayerError(null);
    setToken(res.access_token);
  }, []);

  const register = useCallback(
    async (username: string, password: string) => {
      await authApi.register({ username, password });
      try {
        await login(username, password);
      } catch (err) {
        throw new ApiError(
          isApiError(err) ? err.status : 0,
          'REGISTERED_LOGIN_FAILED',
          'Your account was created, but automatic sign-in failed. Please log in directly with your new credentials.',
        );
      }
    },
    [login],
  );

  const logout = useCallback(async () => {
    setToken(null);
    setRestoring(false);
    setPlayer(null);
    setPlayerError(null);
    const result = await performLogout();
    if (!result.serverRevoked) {
      toast.show({
        tone: 'warning',
        title: 'Signed out locally, but server logout failed',
        message:
          'Your local credentials were cleared, but we could not confirm token revocation with the server.',
        duration: 8000,
      });
    }
    navigate('/login', { replace: true });
  }, [navigate, toast]);

  const refreshPlayer = useCallback(async () => {
    try {
      const me = await playersApi.getMe();
      setPlayerError(null);
      setPlayer(me);
      return me;
    } catch (e) {
      setPlayerError(e);
      return null;
    }
  }, []);

  const updatePlayer = useCallback((next: Player) => {
    setPlayerError(null);
    setPlayer(next);
  }, []);

  const playerStatus: PlayerStatus = restoring
    ? 'loading'
    : !token
      ? 'anonymous'
      : player
        ? 'ready'
        : playerError
          ? 'error'
          : 'loading';

  const value = useMemo<SessionValue>(
    () => ({
      isAuthenticated: token !== null,
      restoring,
      player,
      playerStatus,
      playerError,
      login,
      register,
      logout,
      refreshPlayer,
      updatePlayer,
    }),
    [token, restoring, player, playerStatus, playerError, login, register, logout, refreshPlayer, updatePlayer],
  );

  return <SessionContext.Provider value={value}>{children}</SessionContext.Provider>;
}
