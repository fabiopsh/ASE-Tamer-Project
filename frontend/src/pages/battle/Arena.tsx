import { useEffect, useRef, type ReactNode } from 'react';
import type { Battle, BattleEvent, BattleMember } from '../../api/types';
import { ElementBadge, LevelBadge, RarityBadge } from '../../components/Badges';
import { HpBar } from '../../components/HpBar';
import { Sprite } from '../../components/Sprite';
import type { ShownState } from './battleView';

interface ArenaProps {
  battle: Battle;
  shown: ShownState;
  /** Event being revealed now (drives the cosmetic effects), with a key that changes for every event. */
  fx: { event: BattleEvent; key: string } | null;
  /** Text in the dialogue box at the bottom of the arena. */
  caption: string;
  captionDetail?: string;
  /** Click on the arena while revealing: show everything at once. */
  onSkip?: () => void;
  revealing: boolean;
  /** VICTORY / DEFEAT / FLED banner once the round is fully shown. */
  banner: 'victory' | 'defeat' | 'fled' | null;
  compact: boolean;
  reducedMotion: boolean;
}

type Loose = Record<string, unknown>;

/** The battlefield: wild monster top right, the active team monster bottom left, HUD plates and effects. */
export function Arena({ battle, shown, fx, caption, captionDetail, onSkip, revealing, banner, compact, reducedMotion }: ArenaProps) {
  const wildRef = useRef<HTMLDivElement>(null);
  const teamRef = useRef<HTMLDivElement>(null);
  const arenaRef = useRef<HTMLDivElement>(null);
  const active: BattleMember | undefined = shown.team.find((m) => m.position === shown.activePosition);

  const ev = (fx?.event ?? null) as Loose | null;
  const attack = ev && ev.type === 'attack' ? ev : null;
  const attackerSide = attack ? (attack.side === 'wild' ? 'wild' : 'team') : null;
  const dodged = attack?.dodged === true;
  const strong = attack?.effectiveness === 'strong' && !dodged;
  const damage = typeof attack?.damage === 'number' ? attack.damage : 0;

  // Lunge of the attacker and shake of the target, with the Web Animations API so they restart on every event.
  useEffect(() => {
    if (!fx || reducedMotion) return;
    const e = fx.event as unknown as Loose;
    if (e.type !== 'attack') return;
    const fromWild = e.side === 'wild';
    const attacker = fromWild ? wildRef.current : teamRef.current;
    const target = fromWild ? teamRef.current : wildRef.current;
    const dx = fromWild ? -1 : 1;
    attacker?.animate(
      [
        { transform: 'translate(0, 0)' },
        { transform: `translate(${dx * 38}px, ${-dx * 22}px) scale(1.06)`, offset: 0.35 },
        { transform: 'translate(0, 0)' },
      ],
      { duration: 360, easing: 'cubic-bezier(0.2, 0.8, 0.2, 1)' },
    );
    if (e.dodged === true) {
      target?.animate(
        [{ transform: 'translateX(0)' }, { transform: `translateX(${dx * 34}px)`, offset: 0.4 }, { transform: 'translateX(0)' }],
        { duration: 380, delay: 120, easing: 'ease-out' },
      );
    } else {
      target?.animate(
        [
          { transform: 'translateX(0)', filter: 'brightness(1)' },
          { transform: 'translateX(-9px)', filter: 'brightness(2.6) saturate(0.2)', offset: 0.2 },
          { transform: 'translateX(8px)', filter: 'brightness(1.4)', offset: 0.45 },
          { transform: 'translateX(-5px)', offset: 0.7 },
          { transform: 'translateX(0)', filter: 'brightness(1)' },
        ],
        { duration: 420, delay: 140, easing: 'ease-out' },
      );
      if (e.effectiveness === 'strong') {
        arenaRef.current?.animate(
          [{ transform: 'translate(0,0)' }, { transform: 'translate(-4px, 3px)' }, { transform: 'translate(4px, -2px)' }, { transform: 'translate(0,0)' }],
          { duration: 260, delay: 140 },
        );
      }
    }
  }, [fx, reducedMotion]);

  const wildSize = compact ? 128 : 192;
  const teamSize = compact ? 128 : 192;

  return (
    <div
      ref={arenaRef}
      className={`arena${battle.template.is_boss ? ' arena--boss' : ''}`}
      data-element={battle.wild.element}
      onClick={revealing ? onSkip : undefined}
      role={revealing ? 'button' : undefined}
      aria-label={revealing ? 'Show the whole round now' : undefined}
      tabIndex={revealing ? 0 : undefined}
      onKeyDown={(e) => {
        if (revealing && (e.key === 'Enter' || e.key === ' ')) {
          e.preventDefault();
          onSkip?.();
        }
      }}
    >
      <div className="arena__sky" aria-hidden="true" />
      <div className="arena__motes" aria-hidden="true">
        {Array.from({ length: 10 }, (_, i) => (
          <span key={i} className="arena__mote" style={{ ['--m' as string]: i }} />
        ))}
      </div>
      <div className="arena__ground" aria-hidden="true" />

      {/* wild side */}
      <div className="arena__hud arena__hud--wild">
        <Plate
          name={battle.wild.name}
          level={battle.wild.level}
          element={battle.wild.element}
          extra={<RarityBadge rarity={battle.wild.rarity} size="sm" />}
          hp={shown.wildHp}
          max={battle.wild.max_hp}
          boss={battle.template.is_boss}
        />
      </div>
      <div className="arena__spot arena__spot--wild">
        <span className="arena__platform" aria-hidden="true" />
        <div ref={wildRef} className={`arena__mon${shown.wildKo ? ' is-ko' : ''}`}>
          <Sprite
            src={battle.wild.image_url}
            monsterId={battle.wild.monster_id}
            alt={`Wild ${battle.wild.name}`}
            size={wildSize}
            element={battle.wild.element}
            float={!shown.wildKo}
          />
        </div>
        {attack && attackerSide === 'team' && fx && <FloatText key={fx.key} damage={damage} dodged={dodged} strong={strong} weak={attack.effectiveness === 'weak'} />}
      </div>

      {/* team side */}
      <div className="arena__spot arena__spot--team">
        <span className="arena__platform" aria-hidden="true" />
        {active && (
          <div key={active.owned_id} className="arena__enter">
            <div ref={teamRef} className={`arena__mon arena__mon--team${active.knocked_out || active.hp <= 0 ? ' is-ko' : ''}`}>
              <Sprite
                monsterId={active.monster_id}
                src={active.image_url}
                alt={`Your ${active.name}`}
                size={teamSize}
                element={active.element}
              />
            </div>
          </div>
        )}
        {attack && attackerSide === 'wild' && fx && <FloatText key={fx.key} damage={damage} dodged={dodged} strong={strong} weak={attack.effectiveness === 'weak'} />}
      </div>
      <div className="arena__hud arena__hud--team">
        {active && (
          <Plate name={active.name} level={active.level} element={active.element} hp={active.hp} max={active.max_hp} mine />
        )}
      </div>

      {fx && strong && <span key={`flash-${fx.key}`} className="arena__flash" aria-hidden="true" />}
      {fx && (fx.event as unknown as Loose).type === 'knockout' && <span key={`ko-${fx.key}`} className="arena__flash arena__flash--ko" aria-hidden="true" />}

      <div className="arena__caption" aria-live="polite">
        <p className="arena__caption-text">{caption}</p>
        {captionDetail && <p className="arena__caption-detail">{captionDetail}</p>}
        {revealing && <span className="arena__skip">Click to skip ▸</span>}
      </div>

      {banner && (
        <div className={`arena__banner arena__banner--${banner}`} role="status">
          <span className="arena__banner-text">{banner === 'victory' ? 'Victory' : banner === 'defeat' ? 'Defeat' : 'Fled'}</span>
        </div>
      )}
    </div>
  );
}

function Plate({
  name,
  level,
  element,
  hp,
  max,
  extra,
  boss,
  mine,
}: {
  name: string;
  level: number;
  element: Battle['wild']['element'];
  hp: number;
  max: number;
  extra?: ReactNode;
  boss?: boolean;
  mine?: boolean;
}) {
  return (
    <div className={`plate${boss ? ' plate--boss' : ''}${mine ? ' plate--mine' : ''}`} data-element={element}>
      <div className="plate__row">
        <span className="plate__name">{name}</span>
        <LevelBadge level={level} />
      </div>
      <div className="plate__row plate__row--badges">
        <ElementBadge element={element} size="sm" />
        {extra}
        {boss && <span className="plate__boss">Boss</span>}
      </div>
      <HpBar hp={hp} max={max} label={`${name} HP`} size="lg" />
    </div>
  );
}

function FloatText({ damage, dodged, strong, weak }: { damage: number; dodged: boolean; strong: boolean; weak: boolean }) {
  if (dodged) return <span className="float-text float-text--miss" aria-hidden="true">Dodged!</span>;
  return (
    <span className={`float-text${strong ? ' float-text--strong' : ''}${weak ? ' float-text--weak' : ''}`} aria-hidden="true">
      <span className="float-text__num num">−{damage}</span>
      {strong && <span className="float-text__tag">Super effective!</span>}
      {weak && <span className="float-text__tag">Not very effective</span>}
    </span>
  );
}
