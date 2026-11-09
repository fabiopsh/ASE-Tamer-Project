import { useState } from 'react';
import { assetUrl, spriteUrlFor } from '../api/client';
import type { Element } from '../api/types';
import './sprite.css';

interface SpriteProps {
  /** image_url from the API (relative, e.g. /images/monsters/001.png). */
  src?: string;
  /** Used when src is missing (e.g. battle team members). */
  monsterId?: number;
  alt: string;
  /** Rendered size in px. Sprites are 64×64: use multiples (64, 96, 128, 192) for crisp pixels. */
  size?: number;
  /** Adds a soft element-coloured aura behind the sprite. */
  element?: Element;
  /** Gentle idle bobbing (disabled with prefers-reduced-motion). */
  float?: boolean;
  /** Greyed out (e.g. knocked out, archived). */
  dim?: boolean;
  className?: string;
}

/** Pixel-art monster sprite with image-rendering: pixelated and a graceful fallback. */
export function Sprite({ src, monsterId, alt, size = 96, element, float, dim, className }: SpriteProps) {
  const url = src ? assetUrl(src) : monsterId !== undefined ? spriteUrlFor(monsterId) : undefined;
  const [failedUrl, setFailedUrl] = useState<string | null>(null);
  const failed = !url || failedUrl === url;

  const classes = ['sprite', float && 'sprite--float', dim && 'sprite--dim', className].filter(Boolean).join(' ');
  return (
    <span className={classes} data-element={element} style={{ width: size, height: size }}>
      {element && <span className="sprite__aura" aria-hidden="true" />}
      {failed ? (
        <span className="sprite__fallback" role="img" aria-label={alt}>
          <svg viewBox="0 0 24 24" width="45%" height="45%" fill="none" stroke="currentColor" strokeWidth="1.6">
            <path d="M12 3c4.5 0 7.5 3.4 7.5 8 0 3.5-1.6 6.5-3.5 8.5l-1.5-1.8-1.3 2-1.2-2-1.2 2-1.3-2L8 19.5C6.1 17.5 4.5 14.5 4.5 11c0-4.6 3-8 7.5-8Z" />
            <circle cx="9.5" cy="11" r="1.2" fill="currentColor" />
            <circle cx="14.5" cy="11" r="1.2" fill="currentColor" />
          </svg>
        </span>
      ) : (
        <img
          className="sprite__img"
          src={url}
          alt={alt}
          width={size}
          height={size}
          loading="lazy"
          decoding="async"
          draggable={false}
          onError={() => setFailedUrl(url)}
        />
      )}
    </span>
  );
}
