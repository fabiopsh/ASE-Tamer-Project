import './bars.css';

interface HpBarProps {
  hp: number;
  max: number;
  /** Accessible name, e.g. the monster name. */
  label?: string;
  size?: 'sm' | 'md' | 'lg';
  /** Show "hp / max" next to the bar (default true). */
  showValue?: boolean;
}

/**
 * Health bar: green above 50%, amber above 20%, red below. When hp drops, a pale "ghost"
 * segment lingers briefly to show the damage taken (cosmetic only).
 */
export function HpBar({ hp, max, label = 'HP', size = 'md', showValue = true }: HpBarProps) {
  const safeMax = Math.max(1, max);
  const value = Math.max(0, Math.min(hp, safeMax));
  const pct = (value / safeMax) * 100;
  const tone = pct > 50 ? 'high' : pct > 20 ? 'mid' : 'low';
  return (
    <div className={`hpbar hpbar--${size}`} data-tone={tone}>
      <span className="hpbar__tag" aria-hidden="true">
        HP
      </span>
      <span
        className="hpbar__track"
        role="progressbar"
        aria-label={label}
        aria-valuemin={0}
        aria-valuemax={safeMax}
        aria-valuenow={value}
        aria-valuetext={`${value} of ${safeMax} HP`}
      >
        <span className="hpbar__ghost" style={{ width: `${pct}%` }} />
        <span className="hpbar__fill" style={{ width: `${pct}%` }} />
      </span>
      {showValue && (
        <span className="hpbar__value num" aria-hidden="true">
          {value}
          <span className="hpbar__max">/{safeMax}</span>
        </span>
      )}
    </div>
  );
}

interface XpBarProps {
  xp: number;
  /** xp_to_next_level from the API (0 at max level). */
  toNext: number;
  level?: number;
}

/** Experience towards the next level, as returned by the API. */
export function XpBar({ xp, toNext, level }: XpBarProps) {
  const maxed = toNext <= 0;
  const pct = maxed ? 100 : Math.max(0, Math.min(100, (xp / toNext) * 100));
  return (
    <div className="xpbar">
      <div className="xpbar__row">
        <span className="xpbar__label">{level !== undefined ? `Lv ${level}` : 'XP'}</span>
        <span className="xpbar__value num">{maxed ? 'MAX' : `${xp} / ${toNext} XP`}</span>
      </div>
      <span
        className="xpbar__track"
        role="progressbar"
        aria-label="Experience to next level"
        aria-valuemin={0}
        aria-valuemax={maxed ? 1 : toNext}
        aria-valuenow={maxed ? 1 : xp}
      >
        <span className="xpbar__fill" style={{ width: `${pct}%` }} />
      </span>
    </div>
  );
}
