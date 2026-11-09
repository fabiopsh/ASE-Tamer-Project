import type { CSSProperties, ReactNode } from 'react';
import { Link } from 'react-router-dom';
import type { Element, Rarity } from '../api/types';
import { dexNumber } from '../lib/format';
import { ElementBadge, LevelBadge, RarityBadge } from './Badges';
import { Sprite } from './Sprite';
import './monster-card.css';

/** The fields every monster payload shares (MonsterSummary, OwnedMonster, TeamMember…). */
export interface MonsterLike {
  monster_id: number;
  name: string;
  element: Element;
  rarity?: Rarity;
  image_url?: string;
}

export interface MonsterCardProps {
  monster: MonsterLike;
  /** Render the card as a link. */
  to?: string;
  /** Render the card as a toggle button (aria-pressed = selected). */
  onSelect?: () => void;
  selected?: boolean;
  disabled?: boolean;
  /** Shows a level chip (owned instances). */
  level?: number;
  /** 'md' = 96 px sprite (grids), 'lg' = 128 px sprite (starter pick, highlights). */
  size?: 'md' | 'lg';
  /** Extra content under the badges, e.g. an XpBar. */
  footer?: ReactNode;
  /** Top-right slot, e.g. a status tag. Replaced by a check mark when selected. */
  corner?: ReactNode;
  /** Greyed sprite (archived, knocked out). */
  dim?: boolean;
  className?: string;
  style?: CSSProperties;
}

/**
 * Monster card: rarity frame (shimmering for legendary), element-tinted glow,
 * sprite on a pedestal, name, element and rarity badges.
 */
export function MonsterCard({
  monster,
  to,
  onSelect,
  selected = false,
  disabled = false,
  level,
  size = 'md',
  footer,
  corner,
  dim,
  className,
  style,
}: MonsterCardProps) {
  const rarity = monster.rarity ?? 'common';
  const interactive = Boolean(to || onSelect);
  const classes = [
    'mcard',
    `mcard--${size}`,
    interactive && 'mcard--interactive',
    selected && 'mcard--selected',
    disabled && 'mcard--disabled',
    className,
  ]
    .filter(Boolean)
    .join(' ');

  const body = (
    <>
      <span className="mcard__glow" aria-hidden="true" />
      <span className="mcard__top">
        <span className="mcard__dex num">{dexNumber(monster.monster_id)}</span>
        {selected ? (
          <span className="mcard__check" aria-hidden="true">
            <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" strokeWidth="3">
              <path d="m5 12.5 4.5 4.5L19 7.5" strokeLinecap="round" strokeLinejoin="round" />
            </svg>
          </span>
        ) : (
          (corner ?? (level !== undefined ? <LevelBadge level={level} /> : null))
        )}
      </span>
      <span className="mcard__stage">
        <span className="mcard__pedestal" aria-hidden="true" />
        <Sprite
          src={monster.image_url}
          monsterId={monster.monster_id}
          alt=""
          size={size === 'lg' ? 128 : 96}
          element={monster.element}
          dim={dim}
        />
      </span>
      <span className="mcard__name">{monster.name}</span>
      <span className="mcard__badges">
        <ElementBadge element={monster.element} size="sm" />
        {monster.rarity && <RarityBadge rarity={monster.rarity} size="sm" />}
      </span>
      {selected && level !== undefined && (
        <span className="mcard__level-inline">
          <LevelBadge level={level} />
        </span>
      )}
      {footer && <span className="mcard__footer">{footer}</span>}
    </>
  );

  const common = {
    className: classes,
    'data-element': monster.element,
    'data-rarity': rarity,
    style,
  };

  if (to) {
    return (
      <Link {...common} to={to} aria-label={`${monster.name}, ${monster.element}, ${rarity}`}>
        {body}
      </Link>
    );
  }
  if (onSelect) {
    return (
      <button
        {...common}
        type="button"
        aria-pressed={selected}
        aria-disabled={disabled || undefined}
        aria-label={`${monster.name}, ${monster.element}, ${rarity}`}
        onClick={() => {
          if (!disabled) onSelect();
        }}
      >
        {body}
      </button>
    );
  }
  return <article {...common}>{body}</article>;
}

/** Placeholder card while data loads. */
export function MonsterCardSkeleton({ size = 'md' }: { size?: 'md' | 'lg' }) {
  return (
    <div className={`mcard mcard--${size} mcard--skeleton`} aria-hidden="true">
      <span className="mcard__top">
        <span className="skeleton" style={{ width: 38, height: 14 }} />
      </span>
      <span className="mcard__stage">
        <span className="skeleton" style={{ width: size === 'lg' ? 112 : 84, height: size === 'lg' ? 112 : 84, borderRadius: '50%' }} />
      </span>
      <span className="skeleton" style={{ width: '62%', height: 18, margin: '0 auto' }} />
      <span className="mcard__badges">
        <span className="skeleton" style={{ width: 64, height: 22, borderRadius: 999 }} />
        <span className="skeleton" style={{ width: 72, height: 22, borderRadius: 999 }} />
      </span>
    </div>
  );
}
