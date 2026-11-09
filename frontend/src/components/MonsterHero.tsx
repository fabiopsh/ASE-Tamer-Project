import type { ReactNode } from 'react';
import type { Rarity } from '../api/types';
import { dexNumber } from '../lib/format';
import { ElementBadge, LevelBadge, RarityBadge } from './Badges';
import type { MonsterLike } from './MonsterCard';
import { Sprite } from './Sprite';
import './monster-hero.css';

interface MonsterHeroProps {
  monster: MonsterLike & { rarity: Rarity };
  level?: number;
  /** Tags next to the badges (e.g. "Archived"). */
  tags?: ReactNode;
  /** Content of the info column below the name. */
  children?: ReactNode;
}

/** Large detail header: sprite on an element-coloured stage, number, name, badges, then children. */
export function MonsterHero({ monster, level, tags, children }: MonsterHeroProps) {
  return (
    <section className="mhero panel panel--flush fade-in" data-element={monster.element} data-rarity={monster.rarity}>
      <div className="mhero__stage">
        <span className="mhero__ring" aria-hidden="true" />
        <span className="mhero__ring mhero__ring--outer" aria-hidden="true" />
        <span className="mhero__pedestal" aria-hidden="true" />
        <Sprite src={monster.image_url} monsterId={monster.monster_id} alt={monster.name} size={192} element={monster.element} float />
      </div>
      <div className="mhero__info">
        <div className="stack stack--sm">
          <p className="mhero__dex num">{dexNumber(monster.monster_id)}</p>
          <h1 className="mhero__name">{monster.name}</h1>
          <div className="cluster">
            {level !== undefined && <LevelBadge level={level} />}
            <ElementBadge element={monster.element} />
            <RarityBadge rarity={monster.rarity} />
            {tags}
          </div>
        </div>
        {children}
      </div>
    </section>
  );
}
