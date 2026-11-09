/** Display helpers: formatting only, no game rules. */

export function capitalize(s: string): string {
  return s ? s.charAt(0).toUpperCase() + s.slice(1) : s;
}

/** "#007" style species number. */
export function dexNumber(monsterId: number): string {
  return `#${String(monsterId).padStart(3, '0')}`;
}

const dateFmt = new Intl.DateTimeFormat('en-GB', { day: 'numeric', month: 'short', year: 'numeric' });
const dateTimeFmt = new Intl.DateTimeFormat('en-GB', {
  day: 'numeric',
  month: 'short',
  year: 'numeric',
  hour: '2-digit',
  minute: '2-digit',
});

export function formatDate(iso: string | null | undefined): string {
  if (!iso) return '—';
  const d = new Date(iso);
  return Number.isNaN(d.getTime()) ? iso : dateFmt.format(d);
}

export function formatDateTime(iso: string | null | undefined): string {
  if (!iso) return '—';
  const d = new Date(iso);
  return Number.isNaN(d.getTime()) ? iso : dateTimeFmt.format(d);
}

/** "effect_type" → "Effect type". */
export function humanize(code: string): string {
  return capitalize(code.replace(/_/g, ' '));
}

const TRIGGER_LABELS: Record<string, string> = {
  always: 'Always active',
  first_turn: 'First turn',
  hp_below_50: 'HP below 50%',
  hp_above_50: 'HP above 50%',
  vs_faster: 'Against faster foes',
  on_hit: 'When hit',
  on_attack: 'When attacking',
  once: 'Once per encounter',
};

export function triggerLabel(trigger: string): string {
  return TRIGGER_LABELS[trigger] ?? humanize(trigger);
}

export const SOURCE_LABELS: Record<string, string> = {
  starter: 'Starter',
  encounter: 'Caught in an encounter',
  boss: 'Boss reward',
};

export function shortId(uuid: string): string {
  return uuid.slice(0, 8);
}
