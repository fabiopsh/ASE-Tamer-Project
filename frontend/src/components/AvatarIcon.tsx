import type { Avatar } from '../api/types';
import { isAvatar } from './avatars';

const AVATAR_PATHS: Record<Avatar, string> = {
  scout: 'M12 3 4 20l8-4 8 4L12 3Z',
  warden: 'M12 3 5 6v6c0 4.5 3 7.5 7 9 4-1.5 7-4.5 7-9V6l-7-3Z',
  keeper: 'M12 4a4 4 0 1 0 0 8 4 4 0 0 0 0-8Zm0 8v8m-3-3h6',
  sage: 'M12 3v18M5 8l7-5 7 5M5 16l7 5 7-5',
  nomad: 'M12 3a9 9 0 1 0 0 18 9 9 0 0 0 0-18Zm3 6-2 6-6 2 2-6 6-2Z',
  scholar: 'M4 5h7a2 2 0 0 1 2 2v12a2 2 0 0 0-2-2H4V5Zm16 0h-7a2 2 0 0 0-2 2v12a2 2 0 0 1 2-2h7V5Z',
  archivist: 'M4 6h16v4H4V6Zm2 4v10h12V10M9 14h6',
  ranger: 'M5 19 19 5M12 5h7v7M5 12v7h7',
};

/** Crisp emblem for a chosen player avatar, or the display name's initial when unset. */
export function AvatarBadge({
  avatar,
  displayName,
  size = 18,
}: {
  avatar: string | null | undefined;
  displayName: string;
  size?: number;
}) {
  if (isAvatar(avatar)) {
    return (
      <svg
        width={size}
        height={size}
        viewBox="0 0 24 24"
        fill="none"
        stroke="currentColor"
        strokeWidth="2.1"
        strokeLinecap="square"
        strokeLinejoin="miter"
        aria-hidden="true"
      >
        <path d={AVATAR_PATHS[avatar]} />
      </svg>
    );
  }
  return <>{displayName.replace(/^Tamer-/, '').charAt(0).toUpperCase() || 'T'}</>;
}
