import type { Ability } from '../api/types';
import { humanize, triggerLabel } from '../lib/format';
import { ElementBadge } from './Badges';
import './ability-card.css';

interface AbilityCardProps {
  /** Full ability, or the short {name, description} used in battle payloads. */
  ability: Pick<Ability, 'name' | 'description'> & Partial<Ability>;
  /** Show trigger, effect and scope tags when available (default true). */
  showMeta?: boolean;
}

/** Ability name, text and its trigger/effect tags, exactly as returned by the API. */
export function AbilityCard({ ability, showMeta = true }: AbilityCardProps) {
  return (
    <div className="ability-card" data-scope={ability.scope}>
      <p className="ability-card__label">
        {ability.scope === 'legendary' ? 'Legendary ability' : 'Ability'}
      </p>
      <p className="ability-card__name">{ability.name}</p>
      <p className="ability-card__text">{ability.description}</p>
      {showMeta && (ability.trigger || ability.effect_type) && (
        <div className="cluster">
          {ability.trigger && <span className="tag">{triggerLabel(ability.trigger)}</span>}
          {ability.effect_type && (
            <span className="tag tag--accent">
              {humanize(ability.effect_type)}
              {ability.effect_value ? ` ${ability.effect_value}%` : ''}
            </span>
          )}
          {ability.element && <ElementBadge element={ability.element} size="sm" />}
        </div>
      )}
    </div>
  );
}
