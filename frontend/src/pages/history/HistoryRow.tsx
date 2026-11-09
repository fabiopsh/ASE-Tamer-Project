import { Link } from 'react-router-dom';
import type { HistorySummary } from '../../api/types';
import { ElementBadge, LevelBadge, RarityBadge } from '../../components/Badges';
import { Sprite } from '../../components/Sprite';
import { outcomeLabel, outcomeTone } from '../../lib/battleText';
import { formatDateTime } from '../../lib/format';
import './history.css';

/** One finished battle in a list: wild monster, place, outcome, reward, XP, date. */
export function HistoryRow({ item }: { item: HistorySummary }) {
  const tone = outcomeTone(item.outcome);
  return (
    <Link to={`/history/${item.encounter_id}`} className="hrow" data-outcome={item.outcome} data-element={item.wild?.element}>
      <span className="hrow__sprite">
        <Sprite monsterId={item.wild?.monster_id} alt="" size={64} element={item.wild?.element} dim={item.outcome !== 'victory'} />
      </span>
      <span className="hrow__main">
        <span className="hrow__title">
          {item.wild?.name ?? 'Unknown monster'}
          {item.wild && <LevelBadge level={item.wild.level} />}
        </span>
        <span className="hrow__where">
          {item.template?.is_boss && <span className="hrow__boss">Boss</span>}
          {item.template?.name ?? 'Unknown place'} · Stage {item.template?.stage ?? '?'}
        </span>
        <span className="cluster hrow__badges">
          {item.wild && <ElementBadge element={item.wild.element} size="sm" />}
          {item.wild && <RarityBadge rarity={item.wild.rarity} size="sm" />}
        </span>
      </span>
      <span className="hrow__side">
        <span className={`tag${tone ? ` tag--${tone}` : ''}`}>{outcomeLabel(item.outcome)}</span>
        {item.xp_gained > 0 && <span className="hrow__xp num">+{item.xp_gained} XP</span>}
        {item.reward && <span className="hrow__reward">Caught at Lv {item.reward.level}</span>}
        <span className="hrow__date">{formatDateTime(item.ended_at ?? item.started_at)}</span>
      </span>
    </Link>
  );
}
