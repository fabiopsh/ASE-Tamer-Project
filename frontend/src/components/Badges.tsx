import type { Element, Rarity } from '../api/types';
import { capitalize } from '../lib/format';
import { ElementIcon } from './ElementIcon';
import './badges.css';

interface ElementBadgeProps {
  element: Element;
  size?: 'sm' | 'md';
  /** Icon only (the label stays available to screen readers). */
  iconOnly?: boolean;
}

/** Element pill with its glyph, tinted with the element colour. */
export function ElementBadge({ element, size = 'md', iconOnly = false }: ElementBadgeProps) {
  return (
    <span
      className={`element-badge element-badge--${size}${iconOnly ? ' element-badge--icon' : ''}`}
      data-element={element}
      title={iconOnly ? capitalize(element) : undefined}
    >
      <ElementIcon element={element} size={size === 'sm' ? 14 : 16} />
      {iconOnly ? <span className="visually-hidden">{capitalize(element)}</span> : capitalize(element)}
    </span>
  );
}

const RARITY_GEMS: Record<Rarity, number> = { common: 1, rare: 2, legendary: 3 };

/** Rarity label with 1–3 gems; legendary shimmers. */
export function RarityBadge({ rarity, size = 'md' }: { rarity: Rarity; size?: 'sm' | 'md' }) {
  return (
    <span className={`rarity-badge rarity-badge--${size}`} data-rarity={rarity}>
      <span className="rarity-badge__gems" aria-hidden="true">
        {Array.from({ length: RARITY_GEMS[rarity] ?? 1 }, (_, i) => (
          <span key={i} className="rarity-badge__gem" />
        ))}
      </span>
      {capitalize(rarity)}
    </span>
  );
}

/** "Lv 12" chip. */
export function LevelBadge({ level }: { level: number }) {
  return (
    <span className="level-badge num">
      <span className="level-badge__lv">Lv</span>
      {level}
    </span>
  );
}
