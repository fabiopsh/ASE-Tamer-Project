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
            <stop offset="0" stopColor="#c9b6ff" />
            <stop offset="0.5" stopColor="#8f8cff" />
            <stop offset="1" stopColor="#5fd6ec" />
          </linearGradient>
          <radialGradient id={core} cx="0.4" cy="0.35" r="0.7">
            <stop offset="0" stopColor="#ffffff" />
            <stop offset="0.35" stopColor="#d7ccff" />
            <stop offset="1" stopColor="#6b5cff" />
          </radialGradient>
        </defs>
        <path d="M32 4 56 18v28L32 60 8 46V18Z" fill="rgba(20,22,60,0.9)" stroke={`url(#${g})`} strokeWidth="3.5" strokeLinejoin="round" />
        <path d="M32 14 47 23v18L32 50 17 41V23Z" fill="none" stroke={`url(#${g})`} strokeOpacity="0.45" strokeWidth="1.5" />
        <circle cx="32" cy="32" r="8.5" fill={`url(#${core})`} />
        <path d="M32 18v5M32 41v5M20 25l4 2.5M40 36.5l4 2.5M44 25l-4 2.5M24 36.5l-4 2.5" stroke="#bfefff" strokeWidth="2" strokeLinecap="round" />
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
