import type { CSSProperties } from 'react';
import type { GrowthBlock, StatBlock } from '../api/types';
import './stat-table.css';

const STATS: { key: keyof StatBlock; label: string; short: string; color: string }[] = [
  { key: 'hp', label: 'HP', short: 'HP', color: '#5fe3a1' },
  { key: 'attack', label: 'Attack', short: 'ATK', color: '#ff7a8f' },
  { key: 'defense', label: 'Defense', short: 'DEF', color: '#79b8ff' },
  { key: 'speed', label: 'Speed', short: 'SPD', color: '#ffd27a' },
];

interface StatTableProps {
  stats: StatBlock;
  /** Per-level growth (species detail): shown as "+1.5 / lv". */
  growth?: GrowthBlock;
  /** Value of a full bar. Default: max(100, highest stat), since base stats are 1–100. */
  scaleMax?: number;
  /** Caption read by screen readers and shown above the table. */
  caption?: string;
  compact?: boolean;
}

/** The four stats with bars. Values are shown exactly as returned by the API. */
export function StatTable({ stats, growth, scaleMax, caption, compact = false }: StatTableProps) {
  const max = scaleMax ?? Math.max(100, stats.hp, stats.attack, stats.defense, stats.speed);
  return (
    <table className={`stat-table${compact ? ' stat-table--compact' : ''}`}>
      {caption && <caption className="stat-table__caption">{caption}</caption>}
      <thead className="visually-hidden">
        <tr>
          <th scope="col">Stat</th>
          <th scope="col">Value</th>
          <th scope="col">Bar</th>
          {growth && <th scope="col">Growth per level</th>}
        </tr>
      </thead>
      <tbody>
        {STATS.map((s) => {
          const value = stats[s.key];
          const pct = Math.max(3, Math.min(100, (value / max) * 100));
          return (
            <tr key={s.key} style={{ '--stat-color': s.color } as CSSProperties}>
              <th scope="row" className="stat-table__label">
                <span className="stat-table__dot" aria-hidden="true" />
                {compact ? s.short : s.label}
              </th>
              <td className="stat-table__value num">{value}</td>
              <td className="stat-table__bar-cell">
                <span className="stat-table__track" aria-hidden="true">
                  <span className="stat-table__fill" style={{ width: `${pct}%` }} />
                </span>
              </td>
              {growth && <td className="stat-table__growth num">+{formatGrowth(growth[s.key])} / lv</td>}
            </tr>
          );
        })}
      </tbody>
    </table>
  );
}

function formatGrowth(v: number): string {
  return Number.isInteger(v) ? v.toFixed(1) : String(v);
}
