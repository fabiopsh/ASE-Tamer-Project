import { useCallback, useEffect, useMemo, useRef, useState, type ReactNode } from 'react';
import { ToneIcon } from './ErrorBanner';
import { ToastContext, type ToastApi, type ToastInput } from './toast-context';
import './feedback.css';

interface ToastItem extends Required<Omit<ToastInput, 'message'>> {
  id: number;
  message?: string;
}

/** Stack of transient notifications, bottom-right on desktop, bottom on phones. */
export function ToastProvider({ children }: { children: ReactNode }) {
  const [items, setItems] = useState<ToastItem[]>([]);
  const nextId = useRef(1);
  const timers = useRef<Map<number, number>>(new Map());

  const dismiss = useCallback((id: number) => {
    const timer = timers.current.get(id);
    if (timer !== undefined) {
      window.clearTimeout(timer);
      timers.current.delete(id);
    }
    setItems((list) => list.filter((t) => t.id !== id));
  }, []);

  useEffect(() => {
    const activeTimers = timers.current;
    return () => {
      for (const timer of activeTimers.values()) {
        window.clearTimeout(timer);
      }
      activeTimers.clear();
    };
  }, []);

  const show = useCallback(
    (input: ToastInput) => {
      const id = nextId.current++;
      const item: ToastItem = { id, tone: 'info', duration: 4500, ...input };
      setItems((list) => {
        const next = [...list.slice(-3), item];
        const keptIds = new Set(next.map((t) => t.id));
        for (const [tid, timer] of timers.current.entries()) {
          if (!keptIds.has(tid)) {
            window.clearTimeout(timer);
            timers.current.delete(tid);
          }
        }
        return next;
      });
      if (item.duration > 0) {
        const timer = window.setTimeout(() => dismiss(id), item.duration);
        timers.current.set(id, timer);
      }
    },
    [dismiss],
  );

  const api = useMemo<ToastApi>(() => ({ show }), [show]);

  return (
    <ToastContext.Provider value={api}>
      {children}
      <div className="toasts" aria-live="polite" aria-atomic="false">
        {items.map((t) => (
          <div key={t.id} className={`toast toast--${t.tone}`} role={t.tone === 'error' ? 'alert' : 'status'}>
            <span className="toast__icon" aria-hidden="true">
              <ToneIcon tone={t.tone} />
            </span>
            <div className="toast__body">
              <p className="toast__title">{t.title}</p>
              {t.message && <p className="toast__text">{t.message}</p>}
            </div>
            <button type="button" className="toast__close" onClick={() => dismiss(t.id)} aria-label="Dismiss">
              ✕
            </button>
          </div>
        ))}
      </div>
    </ToastContext.Provider>
  );
}
