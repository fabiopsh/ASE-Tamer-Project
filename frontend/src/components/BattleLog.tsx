import type { CSSProperties } from 'react';
import type { LogLine } from '../lib/battleText';
import './battle-log.css';

const ICONS: Record<LogLine['tone'], string> = {
  team: 'M5 19 19 5M13 5h6v6',
  wild: 'M19 19 5 5M11 5H5v6',
  strong: 'M13 2 4 14h7l-1 8 9-12h-7l1-8Z',
  weak: 'M6 12h12',
  miss: 'M4 12c3-5 13-5 16 0M4 12c3 5 13 5 16 0',
  ko: 'M7 7l10 10M17 7 7 17',
  switch: 'M7 7h11l-3-3M17 17H6l3 3',
  flee: 'M5 12h12m-4-5 5 5-5 5',
  end: 'M12 3l2.6 5.6 6.1.7-4.5 4.2 1.2 6L12 16.8 6.6 19.5l1.2-6L3.3 9.3l6.1-.7L12 3Z',
  info: 'M12 11v6M12 7.5v.5',
};

/** One readable line of a round log (battle screen and history detail). */
export function BattleLogLine({ line, index = 0, fresh = false }: { line: LogLine; index?: number; fresh?: boolean }) {
  return (
    <li
      className={`blog-line blog-line--${line.tone}${fresh ? ' blog-line--fresh' : ''}`}
      style={{ '--i': index } as CSSProperties}
    >
      <span className="blog-line__icon" aria-hidden="true">
        <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round">
          <path d={ICONS[line.tone]} />
        </svg>
      </span>
      <span className="blog-line__body">
        <span className="blog-line__text">{line.text}</span>
        {line.detail && <span className="blog-line__detail">{line.detail}</span>}
      </span>
      {line.damage !== undefined && line.damage > 0 && <span className="blog-line__dmg num">−{line.damage}</span>}
    </li>
  );
}

/** A whole round with its turn header. */
export function BattleRound({ turn, lines }: { turn: number; lines: LogLine[] }) {
  return (
    <section className="blog-round" aria-label={`Turn ${turn}`}>
      <h3 className="blog-round__turn">
        <span>Turn</span> <span className="num">{turn}</span>
      </h3>
      {lines.length === 0 ? (
        <p className="subtle text-sm">Nothing happened this turn.</p>
      ) : (
        <ol className="blog-lines">
          {lines.map((l, i) => (
            <BattleLogLine key={i} line={l} index={i} />
          ))}
        </ol>
      )}
    </section>
  );
}
