import { useId } from 'react';
import './layout.css';

/** Game emblem + wordmark. */
export function Logo({ compact = false, size = 34 }: { compact?: boolean; size?: number }) {
  // Unique gradient ids: a duplicate id inside a hidden (display:none) logo would blank this one.
  const uid = useId().replace(/:/g, '');
  const g = `logo-g-${uid}`;
  const core = `logo-core-${uid}`;
  return (
    <span className="logo">
      <svg className="logo__mark" width={size} height={size} viewBox="0 0 64 64" aria-hidden="true">
        <defs>
          <linearGradient id={g} x1="0" y1="0" x2="1" y2="1">
            <stop offset="0" stopColor="var(--accent)" />
            <stop offset="0.5" stopColor="var(--accent)" />
            <stop offset="1" stopColor="var(--accent)" />
          </linearGradient>
          <radialGradient id={core} cx="0.4" cy="0.35" r="0.7">
            <stop offset="0" stopColor="var(--accent-2)" />
            <stop offset="0.35" stopColor="var(--accent-2)" />
            <stop offset="1" stopColor="var(--accent-2)" />
          </radialGradient>
        </defs>
        <path d="M20 4h24v4h12v12h4v24h-4v12H44v4H20v-4H8V44H4V20h4V8h12Z" fill="var(--bg-0)" stroke={`url(#${g})`} strokeWidth="2" strokeLinejoin="miter" />
        <path d="M20 16h24v4h4v24h-4v4H20v-4h-4V20h4Z" fill="none" stroke={`url(#${g})`} strokeOpacity="1" strokeWidth="2" />
        <circle cx="32" cy="32" r="8.5" fill={`url(#${core})`} />
        <path d="M32 16v8M32 40v8M16 32h8M40 32h8" stroke="var(--text-1)" strokeWidth="2" strokeLinecap="square" />
      </svg>
      {!compact && (
        <span className="logo__text">
          <span className="logo__word">Monster</span>
          <span className="logo__word logo__word--accent">Tamers</span>
        </span>
      )}
    </span>
  );
}
