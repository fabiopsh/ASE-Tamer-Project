import type { ReactNode } from 'react';
import type { Element } from '../api/types';

const GLYPHS: Record<Element, ReactNode> = {
  // crescent moon with a small star
  dream: (
    <>
      <path d="M15.5 3.5a8.5 8.5 0 1 0 5 13.9A7 7 0 0 1 15.5 3.5Z" fill="currentColor" fillOpacity="0.28" />
      <path d="M15.5 3.5a8.5 8.5 0 1 0 5 13.9A7 7 0 0 1 15.5 3.5Z" />
      <path d="M18.5 4.5v3M17 6h3" />
    </>
  ),
  // sound waves from a point
  echo: (
    <>
      <circle cx="5" cy="12" r="2" fill="currentColor" />
      <path d="M9.5 8.5a5 5 0 0 1 0 7" />
      <path d="M13 5.5a9.5 9.5 0 0 1 0 13" />
      <path d="M16.5 2.8a13.5 13.5 0 0 1 0 18.4" />
    </>
  ),
  // snowflake
  silence: (
    <>
      <path d="M12 2.5v19M3.8 7.2l16.4 9.6M3.8 16.8l16.4-9.6" />
      <path d="M9.6 4.3 12 6.2l2.4-1.9M9.6 19.7 12 17.8l2.4 1.9" />
    </>
  ),
  // open book
  memory: (
    <>
      <path d="M12 6.5C10 5 7 4.5 3 5v13c4-.5 7 0 9 1.5 2-1.5 5-2 9-1.5V5c-4-.5-7 0-9 1.5Z" fill="currentColor" fillOpacity="0.2" />
      <path d="M12 6.5C10 5 7 4.5 3 5v13c4-.5 7 0 9 1.5 2-1.5 5-2 9-1.5V5c-4-.5-7 0-9 1.5Z" />
      <path d="M12 6.5v13" />
    </>
  ),
  // hourglass
  time: (
    <>
      <path d="M6 3h12M6 21h12" />
      <path d="M7 3c0 5 10 5 10 9s-10 4-10 9M17 3c0 5-10 5-10 9s10 4 10 9" />
      <path d="M9.5 18.5h5l-2.5-2.5Z" fill="currentColor" />
    </>
  ),
  // ringed planet
  gravity: (
    <>
      <circle cx="12" cy="12" r="5.5" fill="currentColor" fillOpacity="0.28" />
      <circle cx="12" cy="12" r="5.5" />
      <ellipse cx="12" cy="12" rx="10.5" ry="3.6" transform="rotate(-20 12 12)" />
    </>
  ),
  // shattered star
  chaos: (
    <>
      <path d="m12 2 2 6.5L21 9l-5.5 4.2L17.5 21 12 16.8 6.5 21l2-7.8L3 9l7-.5Z" fill="currentColor" fillOpacity="0.22" />
      <path d="m12 2 2 6.5L21 9l-5.5 4.2L17.5 21 12 16.8 6.5 21l2-7.8L3 9l7-.5Z" />
      <path d="m10 8.5 2.6 3.2-1.8 1.4 2 3.7" />
    </>
  ),
};

interface ElementIconProps {
  element: Element;
  size?: number;
  className?: string;
  /** Accessible name; omit when a visible label is next to the icon. */
  title?: string;
}

/** Inline SVG glyph of an element, coloured with currentColor. */
export function ElementIcon({ element, size = 18, className, title }: ElementIconProps) {
  return (
    <svg
      className={className}
      width={size}
      height={size}
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.8"
      strokeLinecap="round"
      strokeLinejoin="round"
      role={title ? 'img' : undefined}
      aria-hidden={title ? undefined : true}
      aria-label={title}
    >
      {GLYPHS[element] ?? <circle cx="12" cy="12" r="6" />}
    </svg>
  );
}
