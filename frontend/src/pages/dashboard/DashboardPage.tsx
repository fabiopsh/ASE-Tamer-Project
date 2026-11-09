import type { CSSProperties } from 'react';
import { Link } from 'react-router-dom';
import { listCollection } from '../../api/monsters';
import type { Run } from '../../api/types';
import { ErrorBanner } from '../../components/ErrorBanner';
import { MonsterCard, MonsterCardSkeleton } from '../../components/MonsterCard';
import { Sprite } from '../../components/Sprite';
import { useAsync } from '../../hooks/useAsync';
import { NavIcon, type NavIconName } from '../../layout/NavIcon';
import { useGameStatus } from '../../session/useGameStatus';
import { useSession } from '../../session/useSession';
import './dashboard.css';

const STAGES = 8;

const SHORTCUTS: { to: string; icon: NavIconName; title: string; text: string }[] = [
  { to: '/encounters', icon: 'encounters', title: 'Encounters', text: 'Face wild monsters of your stage and earn new companions.' },
  { to: '/teams', icon: 'teams', title: 'Teams', text: 'Choose up to three monsters and their battle order.' },
  { to: '/collection', icon: 'collection', title: 'Collection', text: 'Levels, XP and stats of every monster you own.' },
  { to: '/catalog', icon: 'catalog', title: 'Catalogue', text: 'All one hundred species, their stats and abilities.' },
  { to: '/runs', icon: 'runs', title: 'Runs', text: 'Your past runs and how far each one went.' },
  { to: '/history', icon: 'history', title: 'History', text: 'Every finished battle, round by round.' },
];

/** / — run stage, wins, boss availability, companions and shortcuts. */
export function DashboardPage() {
  const { player } = useSession();
  const { run, battle } = useGameStatus();
  const companions = useAsync((signal) => listCollection({ size: 6 }, signal), 'dashboard:companions');

  return (
    <div className="page dashboard">
      <section className="dash-hero panel panel--glow fade-in">
        <div className="dash-hero__text">
          <p className="eyebrow">Tamer's camp</p>
          <h1 className="dash-hero__title">
            Welcome back, <span className="gradient-text dash-hero__name">{player?.display_name ?? 'Tamer'}</span>
          </h1>
          <p className="muted">
            Rest, plan your team, then head out. Each victory brings the wild monster to your side.
          </p>
        </div>
        {companions.data && companions.data.items.length > 0 && (
          <div className="dash-hero__party" aria-hidden="true">
            {companions.data.items.slice(0, 3).map((m, i) => (
              <span key={m.owned_id} className="dash-hero__member" style={{ '--i': i } as CSSProperties}>
                <Sprite src={m.image_url} alt="" size={i === 1 ? 128 : 96} element={m.element} float />
              </span>
            ))}
          </div>
        )}
        <div className="dash-hero__actions">
          {battle && battle.status === 'in_progress' ? (
            <Link to="/battle" className="btn btn--primary btn--lg">
              Resume battle
            </Link>
          ) : (
            <Link to="/encounters" className="btn btn--primary btn--lg">
              Find an encounter
            </Link>
          )}
          <Link to="/teams" className="btn btn--lg">
            Manage teams
          </Link>
        </div>
      </section>

      <section className="panel" aria-labelledby="run-title">
        <h2 id="run-title" className="panel__title">
          <NavIcon name="runs" /> Current run
        </h2>
        {run.status === 'loading' && <div className="skeleton" style={{ height: 120 }} />}
        {run.status === 'ready' && <RunProgress run={run.run} />}
        {run.status === 'none' && (
          <ErrorBanner
            tone="info"
            title="No active run"
            message="The game has no run open for you right now. Check your past runs."
            action={
              <Link to="/runs" className="btn btn--sm">
                My runs
              </Link>
            }
          />
        )}
        {run.status === 'unavailable' && (
          <ErrorBanner
            tone="info"
            title="Run progress is not available yet"
            message="Stage, wins and boss status will show here as soon as the encounter service is online."
          />
        )}
      </section>

      <section className="stack" aria-labelledby="companions-title">
        <div className="row row--between">
          <h2 id="companions-title">Your companions</h2>
          <Link to="/collection" className="btn btn--ghost btn--sm">
            See all
          </Link>
        </div>
        {companions.error !== null ? (
          <ErrorBanner error={companions.error} onRetry={companions.reload} compact />
        ) : (
          <div className="grid-cards stagger">
            {companions.data === undefined
              ? Array.from({ length: 3 }, (_, i) => <MonsterCardSkeleton key={i} />)
              : companions.data.items.map((m, i) => (
                  <MonsterCard
                    key={m.owned_id}
                    monster={m}
                    level={m.level}
                    to={`/collection/${m.owned_id}`}
                    style={{ '--i': i } as CSSProperties}
                  />
                ))}
          </div>
        )}
      </section>

      <section className="stack" aria-labelledby="shortcuts-title">
        <h2 id="shortcuts-title">Where to next?</h2>
        <div className="dash-shortcuts stagger">
          {SHORTCUTS.map((s, i) => (
            <Link key={s.to} to={s.to} className="dash-shortcut" style={{ '--i': i } as CSSProperties}>
              <span className="dash-shortcut__icon" aria-hidden="true">
                <NavIcon name={s.icon} size={26} />
              </span>
              <span className="dash-shortcut__body">
                <span className="dash-shortcut__title">{s.title}</span>
                <span className="dash-shortcut__text">{s.text}</span>
              </span>
            </Link>
          ))}
        </div>
      </section>
    </div>
  );
}

/** Stage track and run figures, exactly as returned by GET /api/runs/current. */
function RunProgress({ run }: { run: Run }) {
  const defeated = new Set(run.bosses_defeated ?? []);
  return (
    <div className="run-progress">
      <ol className="stage-track" aria-label={`Stage ${run.stage} of ${STAGES}`}>
        {Array.from({ length: STAGES }, (_, i) => {
          const s = i + 1;
          const state = defeated.has(s) || s < run.stage ? 'done' : s === run.stage ? 'current' : 'locked';
          return (
            <li key={s} className="stage-track__node" data-state={state} aria-current={state === 'current' ? 'step' : undefined}>
              <span className="stage-track__dot num">{state === 'done' ? '✓' : s}</span>
              <span className="stage-track__label">Stage {s}</span>
            </li>
          );
        })}
      </ol>

      <div className="run-stats">
        <div className="run-stat">
          <span className="run-stat__label">Stage</span>
          <span className="run-stat__value num">
            {run.stage}
            <span className="run-stat__of">/{STAGES}</span>
          </span>
        </div>
        <div className="run-stat">
          <span className="run-stat__label">Wins in this stage</span>
          <span className="run-stat__value num">{run.wins_in_stage}</span>
        </div>
        <div className="run-stat" data-highlight={run.boss_available || undefined}>
          <span className="run-stat__label">Stage boss</span>
          <span className="run-stat__value run-stat__value--text">
            {run.boss_available ? 'Ready to challenge' : 'Locked'}
          </span>
        </div>
        <div className="run-stat">
          <span className="run-stat__label">Status</span>
          <span className="run-stat__value run-stat__value--text">{run.status}</span>
        </div>
      </div>

      {run.boss_available && (
        <Link to="/encounters" className="btn btn--gold">
          Challenge the stage boss
        </Link>
      )}
      {run.status === 'won' && (
        <ErrorBanner
          tone="success"
          title="You won this run!"
          message="Start a new run whenever you are ready: you will pick new starters."
          action={
            <Link to="/runs" className="btn btn--primary btn--sm">
              Start a new run
            </Link>
          }
        />
      )}
    </div>
  );
}
