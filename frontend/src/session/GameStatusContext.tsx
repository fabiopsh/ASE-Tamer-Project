import { useCallback, useEffect, useMemo, useState, type ReactNode } from 'react';
import { useLocation } from 'react-router-dom';
import { isApiError, request } from '../api/client';
import { getCurrentBattle } from '../api/encounters';
import type { Battle, Run } from '../api/types';
import { GameStatusContext, type GameStatusValue, type RunState } from './useGameStatus';

/**
 * Current run and battle for the HUD, the dashboard and the "Resume battle" banner.
 * Re-read on every route change and on refresh(). Failures never break the page:
 * the encounters service may not exist yet.
 */
export function GameStatusProvider({ children }: { children: ReactNode }) {
  const { pathname } = useLocation();
  const [nonce, setNonce] = useState(0);
  const [run, setRun] = useState<RunState>({ status: 'loading' });
  const [battle, setBattle] = useState<Battle | null>(null);

  useEffect(() => {
    const ctrl = new AbortController();
    request<Run>('/api/runs/current', { signal: ctrl.signal })
      .then((r) => setRun({ status: 'ready', run: r }))
      .catch((e: unknown) => {
        if (e instanceof DOMException && e.name === 'AbortError') return;
        // 404 NO_ACTIVE_RUN is a real answer; a 404 without that code means "no such route yet".
        if (isApiError(e) && e.status === 404 && e.code === 'NO_ACTIVE_RUN') setRun({ status: 'none' });
        else setRun({ status: 'unavailable', error: e });
      });
    getCurrentBattle(ctrl.signal)
      .then(setBattle)
      .catch((e: unknown) => {
        if (e instanceof DOMException && e.name === 'AbortError') return;
        setBattle(null);
      });
    return () => ctrl.abort();
  }, [pathname, nonce]);

  const refresh = useCallback(() => setNonce((n) => n + 1), []);

  const value = useMemo<GameStatusValue>(() => ({ run, battle, refresh }), [run, battle, refresh]);
  return <GameStatusContext.Provider value={value}>{children}</GameStatusContext.Provider>;
}
