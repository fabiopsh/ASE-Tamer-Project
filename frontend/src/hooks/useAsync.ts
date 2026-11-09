import { useCallback, useEffect, useLayoutEffect, useRef, useState } from 'react';

export interface AsyncState<T> {
  /** Last successful result (kept while a new key loads, so lists do not flash). */
  data: T | undefined;
  error: unknown;
  /** True while the request for the current key is in flight. */
  loading: boolean;
  /** Run the request again with the same key. */
  reload: () => void;
}

/**
 * Loads data for a key. The request re-runs when `key` changes (encode every input in it,
 * e.g. `catalog:${element}:${page}`) and is aborted when the key changes or on unmount.
 * Pass key = null to skip loading.
 *
 *   const { data, error, loading, reload } = useAsync((signal) => listMonsters(q, signal), `cat:${page}`);
 */
export function useAsync<T>(fn: (signal: AbortSignal) => Promise<T>, key: string | null): AsyncState<T> {
  const fnRef = useRef(fn);
  useLayoutEffect(() => {
    fnRef.current = fn;
  });

  const [nonce, setNonce] = useState(0);
  const [state, setState] = useState<{ token: string | null; data: T | undefined; error: unknown }>({
    token: null,
    data: undefined,
    error: null,
  });

  const token = key === null ? null : `${key}#${nonce}`;

  useEffect(() => {
    if (token === null) return;
    const ctrl = new AbortController();
    fnRef.current(ctrl.signal).then(
      (data) => setState({ token, data, error: null }),
      (error: unknown) => {
        if (error instanceof DOMException && error.name === 'AbortError') return;
        setState((s) => ({ token, data: s.data, error }));
      },
    );
    return () => ctrl.abort();
  }, [token]);

  const reload = useCallback(() => setNonce((n) => n + 1), []);

  const settled = state.token === token;
  return {
    data: state.data,
    error: settled ? state.error : null,
    loading: token !== null && !settled,
    reload,
  };
}
