import type { ReactNode } from 'react';
import './feedback.css';

interface EmptyStateProps {
  title: string;
  message?: ReactNode;
  /** Decorative element above the title (icon or sprite). Defaults to a moon glyph. */
  art?: ReactNode;
  /** Buttons or links. */
  action?: ReactNode;
}

/** Friendly "nothing here yet" block. */
export function EmptyState({ title, message, art, action }: EmptyStateProps) {
  return (
    <div className="empty-state">
      <div className="empty-state__art" aria-hidden="true">
        {art ?? (
          <svg viewBox="0 0 64 64" width="64" height="64" fill="none" stroke="currentColor" strokeWidth="2">
            <path d="M40 10a22 22 0 1 0 14 36A18 18 0 0 1 40 10Z" fill="currentColor" fillOpacity="0.12" />
            <path d="M48 8v6M45 11h6M14 16v4M12 18h4M52 30v3M50.5 31.5h3" strokeLinecap="round" />
          </svg>
        )}
      </div>
      <h3 className="empty-state__title">{title}</h3>
      {message && <p className="empty-state__text">{message}</p>}
      {action && <div className="empty-state__action">{action}</div>}
    </div>
  );
}
