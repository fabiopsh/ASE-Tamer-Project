/**
 * What the arena shows while the events of a round are revealed one by one.
 *
 * Nothing is computed: every number comes from the battle state before the action (`prev`) or after it (`next`).
 * Before an event is revealed the arena keeps the old values; once the event that changes a combatant is revealed,
 * that combatant switches to the new values. When the reveal ends, the arena shows `next` exactly.
 */
import type { Battle, BattleEvent, BattleMember } from '../../api/types';

export interface ShownState {
  wildHp: number;
  wildKo: boolean;
  team: BattleMember[];
  activePosition: number | null;
}

type Loose = Record<string, unknown>;

export function eventsOf(b: Battle | null | undefined): BattleEvent[] {
  return b?.last_round?.events ?? [];
}

/**
 * State after the round. A finished battle may have no `active` member any more: the arena then keeps the
 * monster that was on the field (from the previous state, or the last switch of the round).
 */
export function finalState(b: Battle, prev: Battle | null = null): ShownState {
  let position = b.team.find((m) => m.active)?.position ?? null;
  if (position === null) {
    position = prev?.team.find((m) => m.active)?.position ?? null;
    for (const e of eventsOf(b) as unknown as Loose[]) {
      if ((e.type === 'switch' || e.type === 'auto_switch') && typeof e.to === 'number') position = e.to;
    }
  }
  return {
    wildHp: b.wild.hp,
    wildKo: b.wild.hp <= 0,
    team: b.team,
    activePosition: position,
  };
}

export function shownState(prev: Battle | null, next: Battle, revealed: number): ShownState {
  const events = eventsOf(next);
  if (!prev || revealed >= events.length) return finalState(next, prev);

  const shown = events.slice(0, revealed) as unknown as Loose[];
  const teamHit = shown.some((e) => e.type === 'attack' && e.side === 'wild');
  const wildHit = shown.some((e) => e.type === 'attack' && e.side === 'team');
  const wildKo = shown.some((e) => e.type === 'knockout' && e.side === 'wild');

  let activePosition = prev.team.find((m) => m.active)?.position ?? null;
  for (const e of shown) {
    if ((e.type === 'switch' || e.type === 'auto_switch') && typeof e.to === 'number') activePosition = e.to;
  }

  const source = teamHit ? next.team : prev.team;
  const team = source.map((m) => ({ ...m, active: m.position === activePosition }));
  return {
    wildHp: wildHit ? next.wild.hp : prev.wild.hp,
    wildKo,
    team,
    activePosition,
  };
}

/** Milliseconds between two revealed events: the whole round fits in about 1.8 s. */
export function revealStep(count: number): number {
  if (count <= 0) return 0;
  return Math.max(260, Math.min(560, Math.round(1800 / count)));
}
