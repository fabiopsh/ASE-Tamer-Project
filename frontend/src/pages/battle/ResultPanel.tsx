import { Link } from 'react-router-dom';
import { getMonster } from '../../api/monsters';
import type { Battle, BattleResult } from '../../api/types';
import { ElementBadge, LevelBadge, RarityBadge } from '../../components/Badges';
import { Button } from '../../components/Button';
import { ErrorBanner } from '../../components/ErrorBanner';
import { Sprite } from '../../components/Sprite';
import { useAsync } from '../../hooks/useAsync';
import { useGoToStarters } from '../../hooks/useGoToStarters';

/** End of battle (US-20): outcome, reward, XP, level-ups and run progress, as returned in `result`. */
export function ResultPanel({ battle, result }: { battle: Battle; result: BattleResult }) {
  const goToStarters = useGoToStarters();
  const outcome = result.outcome;
  const runOver = result.run.status === 'lost' || result.run.status === 'abandoned' || outcome === 'defeat';
  const runWon = result.run.status === 'won';
  const nameOf = (ownedId: string) => battle.team.find((m) => m.owned_id === ownedId)?.name ?? 'A team member';

  return (
    <section className={`result panel panel--glow result--${outcome}`} aria-labelledby="result-title">
      <header className="result__head">
        <p className="eyebrow">Battle over</p>
        <h2 id="result-title" className="result__title">
          {outcome === 'victory'
            ? `You defeated ${battle.wild.name}!`
            : outcome === 'defeat'
              ? 'Your team has fallen'
              : outcome === 'fled'
                ? 'You escaped safely'
                : 'The battle has ended'}
        </h2>
      </header>

      <div className="result__grid">
        {outcome === 'victory' && result.reward && <RewardCard reward={result.reward} />}

        <div className="result__block">
          <h3 className="result__label">Experience</h3>
          {result.xp_gained > 0 ? (
            <p className="result__xp">
              <span className="num">+{result.xp_gained} XP</span>
              <span className="muted text-sm">to every team member</span>
            </p>
          ) : (
            <p className="muted">No experience this time.</p>
          )}
          {result.level_ups.length > 0 && (
            <ul className="levelups">
              {result.level_ups.map((l) => (
                <li key={l.owned_id} className="levelup">
                  <span className="levelup__name">{nameOf(l.owned_id)}</span>
                  <span className="levelup__levels num">
                    Lv {l.level_before} <span aria-hidden="true">→</span>
                    <span className="visually-hidden">to</span> <strong>Lv {l.level_after}</strong>
                  </span>
                  <span className="tag tag--success">Level up!</span>
                </li>
              ))}
            </ul>
          )}
        </div>

        <div className="result__block">
          <h3 className="result__label">Run progress</h3>
          <dl className="kv">
            <dt>Stage</dt>
            <dd className="num">{result.run.stage} / 8</dd>
            <dt>Wins in this stage</dt>
            <dd className="num">{result.run.wins_in_stage}</dd>
            <dt>Stage boss</dt>
            <dd>{result.run.boss_available ? <span className="tag tag--warning">Unlocked</span> : 'Locked'}</dd>
            <dt>Run</dt>
            <dd>{result.run.status === 'active' ? 'In progress' : result.run.status === 'won' ? 'Won' : result.run.status === 'lost' ? 'Lost' : result.run.status}</dd>
          </dl>
        </div>
      </div>

      {runWon && (
        <ErrorBanner
          tone="success"
          title="You conquered all eight stages!"
          message="This run is won. Start a new run from the Runs page whenever you want: you will pick new starters."
        />
      )}
      {runOver && (
        <ErrorBanner
          tone="warning"
          title="This run is over"
          message="Your whole team was knocked out, so the run is lost. Your monsters are archived (you can still see them in past runs) and your teams are cleared. Choose three new starters to begin a new run."
        />
      )}
      {result.run.boss_available && !runOver && (
        <ErrorBanner tone="info" title="The stage boss is waiting" message="You have unlocked the boss of this stage. Beat it to reach the next stage." />
      )}

      <div className="result__actions">
        {runOver ? (
          <>
            <Button variant="primary" size="lg" onClick={goToStarters}>
              Choose new starters
            </Button>
            <Link to="/history" className="btn btn--lg">
              Battle history
            </Link>
          </>
        ) : runWon ? (
          <>
            <Link to="/runs" className="btn btn--gold btn--lg">
              Start a new run
            </Link>
            <Link to="/collection" className="btn btn--lg">
              View collection
            </Link>
          </>
        ) : (
          <>
            <Link to="/encounters" className="btn btn--primary btn--lg">
              Back to encounters
            </Link>
            <Link to="/collection" className="btn btn--lg">
              View collection
            </Link>
          </>
        )}
      </div>
    </section>
  );
}

function RewardCard({ reward }: { reward: NonNullable<BattleResult['reward']> }) {
  const species = useAsync((signal) => getMonster(reward.monster_id, signal), `reward:${reward.monster_id}`);
  const m = species.data;
  return (
    <div className="result__block reward" data-element={m?.element} data-rarity={m?.rarity}>
      <h3 className="result__label">New companion</h3>
      <div className="reward__body">
        <span className="reward__art">
          <span className="reward__rays" aria-hidden="true" />
          <Sprite monsterId={reward.monster_id} src={m?.image_url} alt={m?.name ?? 'Reward monster'} size={128} element={m?.element} float />
        </span>
        <div className="stack stack--sm">
          <span className="reward__name">{m?.name ?? `Monster #${reward.monster_id}`}</span>
          <span className="cluster">
            <LevelBadge level={reward.level} />
            {m && <ElementBadge element={m.element} size="sm" />}
            {m && <RarityBadge rarity={m.rarity} size="sm" />}
          </span>
          {reward.pending ? (
            <p className="reward__note">
              It will join your collection in a moment: the game is still delivering it and retries automatically.
            </p>
          ) : (
            <p className="reward__note reward__note--ok">Joined your collection.</p>
          )}
          {!reward.pending && reward.owned_id && (
            <Link to={`/collection/${reward.owned_id}`} className="btn btn--sm">
              See it
            </Link>
          )}
        </div>
      </div>
    </div>
  );
}
