import { AVATARS, type Avatar } from '../api/types';

export const AVATAR_LABELS: Record<Avatar, string> = {
  scout: 'Scout',
  warden: 'Warden',
  keeper: 'Keeper',
  sage: 'Sage',
  nomad: 'Nomad',
  scholar: 'Scholar',
  archivist: 'Archivist',
  ranger: 'Ranger',
};

export function isAvatar(value: string | null | undefined): value is Avatar {
  return typeof value === 'string' && (AVATARS as readonly string[]).includes(value);
}
