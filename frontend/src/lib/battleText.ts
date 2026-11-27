/**
 * Turns battle events (engine/BattleEvent.java, `last_round.events` and history `turns[].events`) into readable
 * sentences. Shared by the battle log and the history detail. Text only: no game rule is computed here.
 *
 * Events are flat records: fields that do not belong to the type are absent (live battle, NON_NULL) or null
 * (possible in stored history), so every field is read defensively.
 */
import type { BattleEvent, BattleOutcome, Round } from '../api/types';

export type LineTone =
  | 'team'
  | 'wild'
  | 'strong'
  | 'weak'
  | 'miss'
  | 'ko'
  | 'switch'
  | 'flee'
  | 'end'
  | 'ability'
  | 'heal'
  | 'info';

export interface LogLine {
  text: string;
  tone: LineTone;
  /** Smaller second line (effectiveness, abilities). */
  detail?: string;
  /** Damage shown as a negative number chip. */
  damage?: number;
  /** Healing shown as a positive number chip. */
  heal?: number;
}

/** Names needed to describe events that only carry positions. */
export interface BattleNames {
  wild: string;
  /** Team member name by position (1-3). */
  team: Record<number, string>;
}

type Loose = Record<string, unknown>;

const str = (v: unknown): string | undefined => (typeof v === 'string' && v.length > 0 ? v : undefined);
const num = (v: unknown): number | undefined => (typeof v === 'number' && Number.isFinite(v) ? v : undefined);

function member(names: BattleNames, position: unknown): string {
  const p = num(position);
  return (p !== undefined && names.team[p]) || (p !== undefined ? `Monster #${p}` : 'Your monster');
}

export const OUTCOME_LABELS: Record<BattleOutcome, string> = {
  victory: 'Victory',
  defeat: 'Defeat',
  fled: 'Fled',
  expired: 'Expired',
  interrupted: 'Interrupted',
  abandoned: 'Abandoned',
};

export function outcomeLabel(outcome: string): string {
  return OUTCOME_LABELS[outcome as BattleOutcome] ?? outcome;
}

/** Tag class for an outcome (ui.css .tag--*). */
export function outcomeTone(outcome: string): 'success' | 'danger' | 'warning' | 'info' | '' {
  switch (outcome) {
    case 'victory':
      return 'success';
    case 'defeat':
      return 'danger';
    case 'fled':
      return 'info';
    case 'abandoned':
    case 'expired':
    case 'interrupted':
      return 'warning';
    default:
      return '';
  }
}

function describeAbilityEvent(e: Loose, names: BattleNames): LogLine {
  const side = str(e.side) === 'wild' ? 'wild' : str(e.side) === 'team' ? 'team' : undefined;
  const ability = str(e.ability) ?? 'An ability';
  const effect = (str(e.effect) ?? str(e.effect_type))?.toLowerCase();
  const actor =
    str(e.monster) ??
    str(e.actor) ??
    str(e.attacker) ??
    (side === 'wild' ? names.wild : side === 'team' ? member(names, e.position) : undefined);
  const target =
    str(e.target) ??
    (side === 'wild' ? 'your monster' : side === 'team' ? names.wild : undefined);
  const amount = num(e.amount) ?? num(e.value) ?? num(e.damage);
  const description = str(e.description);

  switch (effect) {
    case 'heal': {
      const subject = actor ?? 'A monster';
      const text =
        amount !== undefined && amount > 0
          ? `${subject} recovered ${amount} HP with ${ability}.`
          : `${subject} healed with ${ability}.`;
      return { text, tone: 'heal', detail: description, heal: amount && amount > 0 ? amount : undefined };
    }
    case 'reflect': {
      const text =
        amount !== undefined && amount > 0
          ? `${ability} reflected ${amount} damage back to ${target ?? 'the attacker'}!`
          : `${ability} reflected damage back to ${target ?? 'the attacker'}!`;
      return { text, tone: 'ability', detail: description, damage: amount && amount > 0 ? amount : undefined };
    }
    case 'stun': {
      if (description) {
        return { text: description, tone: 'ability' };
      }
      const stunned = str(e.target) ?? actor ?? target ?? 'The opponent';
      return {
        text: `${stunned} was stunned by ${ability} and cannot act!`,
        tone: 'ability',
      };
    }
    case 'revive': {
      const subject = actor ?? 'A monster';
      const text =
        amount !== undefined && amount > 0
          ? `${subject} revived with ${amount} HP thanks to ${ability}!`
          : `${subject} revived thanks to ${ability}!`;
      return { text, tone: 'heal', detail: description, heal: amount && amount > 0 ? amount : undefined };
    }
    case 'random_boost': {
      const stat = str(e.stat);
      const subject = actor ? `${actor}'s ` : '';
      const boost = amount !== undefined && amount > 0 ? ` (+${amount}%)` : '';
      const text = stat
        ? `${ability} boosted ${subject}${stat}${boost}!`
        : `${ability} boosted a random stat${boost}!`;
      return { text, tone: 'ability', detail: description };
    }
    case 'copy_ability': {
      const copied = str(e.copied) ?? str(e.copied_ability);
      const subject = actor ?? 'A monster';
      const text = copied
        ? `${subject} copied ${copied} with ${ability}!`
        : `${subject} copied the opponent's ability with ${ability}!`;
      return { text, tone: 'ability', detail: description };
    }
    case 'invert_advantage': {
      return {
        text: `${ability} inverted elemental advantages!`,
        tone: 'ability',
        detail: description,
      };
    }
    default: {
      const text = actor ? `${actor}'s ${ability} activates.` : `${ability} activates.`;
      return { text, tone: 'ability', detail: description };
    }
  }
}

/** One event → one log line (or none for unknown types). */
export function describeEvent(event: BattleEvent, names: BattleNames): LogLine | null {
  const e = event as unknown as Loose;
  const type = str(e.type);
  switch (type) {
    case 'attack': {
      const side = str(e.side) === 'wild' ? 'wild' : 'team';
      const attacker = str(e.attacker) ?? (side === 'wild' ? names.wild : 'Your monster');
      const target = str(e.target) ?? (side === 'wild' ? 'your monster' : names.wild);
      const abilities = Array.isArray(e.abilities) ? (e.abilities as unknown[]).map(str).filter(Boolean) : [];
      const abilityText = abilities.length > 0 ? `${abilities.join(', ')} took effect.` : undefined;
      if (e.dodged === true) {
        return {
          text: `${target} dodged ${attacker}'s attack!`,
          tone: 'miss',
          detail: abilityText,
        };
      }
      const damage = num(e.damage) ?? 0;
      const eff = str(e.effectiveness);
      const effText =
        eff === 'strong' ? "It's super effective!" : eff === 'weak' ? "It's not very effective…" : undefined;
      const detail = [effText, abilityText].filter(Boolean).join(' ') || undefined;
      return {
        text: `${attacker} attacks ${target} for ${damage} damage.`,
        tone: eff === 'strong' ? 'strong' : eff === 'weak' ? 'weak' : side,
        detail,
        damage,
      };
    }
    case 'knockout': {
      if (str(e.side) === 'wild') return { text: `${names.wild} was knocked out!`, tone: 'ko' };
      return { text: `${member(names, e.position)} was knocked out!`, tone: 'ko' };
    }
    case 'auto_switch':
      return { text: `${member(names, e.to)} steps into the fight!`, tone: 'switch' };
    case 'switch':
      return {
        text: `You call back ${member(names, e.from)} and send out ${member(names, e.to)}.`,
        tone: 'switch',
      };
    case 'flee':
      return e.success === true
        ? { text: 'You got away safely!', tone: 'flee' }
        : { text: "You tried to flee, but couldn't escape!", tone: 'flee' };
    case 'ability':
      return describeAbilityEvent(e, names);
    case 'end': {
      const outcome = str(e.outcome) ?? '';
      if (outcome === 'victory') return { text: `Victory! ${names.wild} has been defeated.`, tone: 'end' };
      if (outcome === 'defeat') return { text: 'Defeat… your whole team has fallen.', tone: 'ko' };
      if (outcome === 'fled') return { text: 'You left the battle.', tone: 'flee' };
      return { text: `The battle ended: ${outcomeLabel(outcome).toLowerCase()}.`, tone: 'end' };
    }
    default:
      return type ? { text: `Event: ${type.replace(/_/g, ' ')}.`, tone: 'info' } : null;
  }
}

/** All lines of a round, in event order. */
export function describeRound(round: Round | null | undefined, names: BattleNames): LogLine[] {
  if (!round || !Array.isArray(round.events)) return [];
  return round.events.map((ev) => describeEvent(ev, names)).filter((l): l is LogLine => l !== null);
}

/** Builds the names map from any list of {position, name}. */
export function namesFrom(wild: string, team: { position: number; name: string }[]): BattleNames {
  const map: Record<number, string> = {};
  for (const m of team) map[m.position] = m.name;
  return { wild, team: map };
}
