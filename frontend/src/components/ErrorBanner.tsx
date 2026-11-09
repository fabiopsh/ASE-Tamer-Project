import type { ReactNode } from 'react';
import { errorMessage, isApiError } from '../api/client';
import './feedback.css';

type Tone = 'error' | 'warning' | 'info' | 'success';

interface ErrorBannerProps {
  /** Anything thrown (ApiError, Error) or a plain message. */
  error?: unknown;
  title?: string;
  /** Overrides the message derived from `error`. */
  message?: ReactNode;
  tone?: Tone;
  onRetry?: () => void;
  onDismiss?: () => void;
  /** Extra button(s) on the right. */
  action?: ReactNode;
  compact?: boolean;
}

/** Inline banner for API errors and notices. Shows the server message and the error code. */
export function ErrorBanner({
  error,
  title,
  message,
  tone = 'error',
  onRetry,
  onDismiss,
  action,
  compact,
}: ErrorBannerProps) {
  const text = message ?? (typeof error === 'string' ? error : error !== undefined ? errorMessage(error) : null);
  const code = isApiError(error) && !error.isNetwork ? error.code : null;
  return (
    <div
      className={`banner banner--${tone}${compact ? ' banner--compact' : ''}`}
      role={tone === 'error' ? 'alert' : 'status'}
    >
      <span className="banner__icon" aria-hidden="true">
        <ToneIcon tone={tone} />
      </span>
      <div className="banner__body">
        {title && <p className="banner__title">{title}</p>}
        {text && <p className="banner__text">{text}</p>}
        {code && <p className="banner__code">Code: {code}</p>}
      </div>
      {(onRetry || action || onDismiss) && (
        <div className="banner__actions">
          {action}
          {onRetry && (
            <button type="button" className="btn btn--sm" onClick={onRetry}>
              Try again
            </button>
          )}
          {onDismiss && (
            <button type="button" className="btn btn--ghost btn--sm btn--icon" onClick={onDismiss} aria-label="Dismiss">
              ✕
            </button>
          )}
        </div>
      )}
    </div>
  );
}

export function ToneIcon({ tone }: { tone: Tone }) {
  const d =
    tone === 'success'
      ? 'm6 12.5 4 4 8-9'
      : tone === 'info'
        ? 'M12 11v6M12 7.5v.5'
        : tone === 'warning'
          ? 'M12 8v5M12 16.5v.5'
          : 'M8.5 8.5l7 7M15.5 8.5l-7 7';
  return (
    <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" strokeWidth="2.4" strokeLinecap="round">
      <circle cx="12" cy="12" r="9.5" strokeWidth="1.8" />
      <path d={d} />
    </svg>
  );
}
