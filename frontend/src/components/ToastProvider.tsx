import { useCallback, useMemo, useRef, useState, type ReactNode } from 'react';
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

  const dismiss = useCallback((id: number) => setItems((list) => list.filter((t) => t.id !== id)), []);

  const show = useCallback(
    (input: ToastInput) => {
      const id = nextId.current++;
      const item: ToastItem = { id, tone: 'info', duration: 4500, ...input };
      setItems((list) => [...list.slice(-3), item]);
      if (item.duration > 0) window.setTimeout(() => dismiss(id), item.duration);
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
