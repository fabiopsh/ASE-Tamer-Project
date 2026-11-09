import { useCallback, useEffect, useMemo, useState, type ReactNode } from 'react';
import { useNavigate } from 'react-router-dom';
import { configureClient } from '../api/client';
import * as authApi from '../api/auth';
import * as playersApi from '../api/players';
import type { Player } from '../api/types';
import { clearSession, markSessionExpired, readSession, writeSession } from './storage';
import { SessionContext, type PlayerStatus, type SessionValue } from './useSession';

/**
 * Session state: token (localStorage `tamer.session`) and the player profile.
 * Must be rendered inside the router (it navigates to /login on 401).
 */
export function SessionProvider({ children }: { children: ReactNode }) {
  const navigate = useNavigate();
  const [token, setToken] = useState<string | null>(() => readSession()?.access_token ?? null);
  const [player, setPlayer] = useState<Player | null>(null);
  const [playerError, setPlayerError] = useState<unknown>(null);

  // The client reads the token on every request and calls back on 401.
  useEffect(() => {
    configureClient({
      getToken: () => readSession()?.access_token ?? null,
      onUnauthorized: () => {
        clearSession();
        markSessionExpired();
        setToken(null);
        setPlayer(null);
        setPlayerError(null);
        navigate('/login', { replace: true });
      },
    });
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
    const res = await authApi.login(username, password);
    writeSession(res);
    setPlayer(null);
    setPlayerError(null);
    setToken(res.access_token);
  }, []);

  const register = useCallback(
    async (username: string, password: string) => {
      await authApi.register({ username, password });
      await login(username, password);
    },
    [login],
  );

  const logout = useCallback(() => {
    clearSession();
    setToken(null);
    setPlayer(null);
    setPlayerError(null);
    navigate('/login', { replace: true });
  }, [navigate]);

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

  const playerStatus: PlayerStatus = !token
    ? 'anonymous'
    : player
      ? 'ready'
      : playerError
        ? 'error'
        : 'loading';

  const value = useMemo<SessionValue>(
    () => ({
      isAuthenticated: token !== null,
      player,
      playerStatus,
      playerError,
      login,
      register,
      logout,
      refreshPlayer,
    }),
    [token, player, playerStatus, playerError, login, register, logout, refreshPlayer],
  );

  return <SessionContext.Provider value={value}>{children}</SessionContext.Provider>;
}
