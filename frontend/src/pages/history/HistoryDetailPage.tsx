import { Link, useParams } from 'react-router-dom';
import { isApiError } from '../../api/client';
import { getHistoryRecord } from '../../api/history';
import type { HistoryRecord } from '../../api/types';
import { BackLink } from '../../components/BackLink';
import { ElementBadge, LevelBadge, RarityBadge } from '../../components/Badges';
import { BattleRound } from '../../components/BattleLog';
import { ErrorBanner } from '../../components/ErrorBanner';
import { PageHeader } from '../../components/PageHeader';
import { Sprite } from '../../components/Sprite';
import { useAsync } from '../../hooks/useAsync';
import { describeRound, namesFrom, outcomeLabel, outcomeTone } from '../../lib/battleText';
import { formatDateTime } from '../../lib/format';
import './history.css';

/** /history/:id — one finished battle with its team snapshot and full round log (US-24). */
export function HistoryDetailPage() {
  const { id = '' } = useParams();
  const { data, error, reload } = useAsync((signal) => getHistoryRecord(id, signal), `history:${id}`);

  return (
    <div className="page">
      <PageHeader
        before={<BackLink to="/history" label="History" />}
        eyebrow={data ? `Stage ${data.template?.stage ?? '?'} · ${formatDateTime(data.ended_at ?? data.started_at)}` : 'Battle'}
        title={data ? `${data.wild?.name ?? 'Unknown monster'} at ${data.template?.name ?? 'an unknown place'}` : 'Battle record'}
        actions={
          data ? (
            <span className={`tag hd-outcome${outcomeTone(data.outcome) ? ` tag--${outcomeTone(data.outcome)}` : ''}`}>
              {outcomeLabel(data.outcome)}
            </span>
          ) : undefined
        }
      />
      {error !== null && (
        <ErrorBanner
          title={isApiError(error) && error.isNotFound ? 'This battle record does not exist' : 'The battle could not be loaded'}
          error={error}
          onRetry={reload}
        />
      )}
      {!data && error === null && <div className="skeleton" style={{ height: 320, borderRadius: 16 }} />}
      {data && <Record r={data} />}
    </div>
  );
}

function Record({ r }: { r: HistoryRecord }) {
  const team = Array.isArray(r.team_snapshot) ? [...r.team_snapshot].sort((a, b) => a.position - b.position) : [];
  const turns = Array.isArray(r.turns) ? r.turns : [];
  const names = namesFrom(r.wild?.name ?? 'The wild monster', team);

  return (
    <div className="hd">
      <div className="hd__side">
        <section className="panel hd-face" data-element={r.wild?.element} aria-label="Wild monster">
          <span className="hd-face__glow" aria-hidden="true" />
          <p className="eyebrow">{r.template?.is_boss ? 'Stage boss' : 'Wild monster'}</p>
          {r.wild && (
            <>
              <Sprite monsterId={r.wild.monster_id} alt={r.wild.name} size={128} element={r.wild.element} />
              <h2 className="hd-face__name">{r.wild.name}</h2>
              <div className="cluster" style={{ justifyContent: 'center' }}>
                <LevelBadge level={r.wild.level} />
                <ElementBadge element={r.wild.element} size="sm" />
                <RarityBadge rarity={r.wild.rarity} size="sm" />
              </div>
            </>
          )}
        </section>

        <section className="panel stack" aria-labelledby="hd-result">
          <h2 id="hd-result" className="panel__title" style={{ marginBottom: 0 }}>
            Result
          </h2>
          <dl className="kv">
            <dt>Outcome</dt>
            <dd>{outcomeLabel(r.outcome)}</dd>
            <dt>XP gained</dt>
            <dd className="num">{r.xp_gained > 0 ? `+${r.xp_gained} to every member` : 'None'}</dd>
            <dt>Reward</dt>
            <dd>
              {r.reward ? (
                r.reward.owned_id ? (
                  <Link to={`/collection/${r.reward.owned_id}`}>
                    {r.wild?.name ?? 'Monster'} at Lv {r.reward.level}
                  </Link>
                ) : (
                  `${r.wild?.name ?? 'Monster'} at Lv ${r.reward.level}`
                )
              ) : (
                'None'
              )}
            </dd>
            <dt>Rounds</dt>
            <dd className="num">{turns.length}</dd>
            <dt>Started</dt>
            <dd>{formatDateTime(r.started_at)}</dd>
            <dt>Ended</dt>
            <dd>{formatDateTime(r.ended_at)}</dd>
          </dl>
          <details className="hd-tech">
            <summary>Replay data</summary>
            <dl className="kv">
              <dt>Engine version</dt>
              <dd className="num">{r.engine_version}</dd>
              <dt>Random seed</dt>
              <dd className="num hd-tech__seed">{r.rng_seed ?? 'Not recorded'}</dd>
              <dt>Run</dt>
              <dd>
                <Link to={`/runs/${r.run_id}`}>Open the run</Link>
              </dd>
            </dl>
          </details>
        </section>

        <section className="panel stack" aria-labelledby="hd-team">
          <h2 id="hd-team" className="panel__title" style={{ marginBottom: 0 }}>
            Your team
          </h2>
          {team.length === 0 ? (
            <p className="muted text-sm">No team snapshot was recorded for this battle.</p>
          ) : (
            <ol className="hd-team">
              {team.map((m) => (
                <li key={m.owned_id ?? m.position} className="hd-team__member" data-element={m.element}>
                  <span className="team-slot num">{m.position}</span>
                  <Sprite monsterId={m.monster_id} alt="" size={64} element={m.element} />
                  <span className="hd-team__info">
                    <span className="hd-team__name">
                      {m.name} <LevelBadge level={m.level} />
                    </span>
                    {m.stats && (
                      <span className="hd-team__stats num">
                        HP {m.stats.hp} · ATK {m.stats.attack} · DEF {m.stats.defense} · SPD {m.stats.speed}
                      </span>
                    )}
                    {m.ability?.name && <span className="hd-team__ability">{m.ability.name}</span>}
                  </span>
                </li>
              ))}
            </ol>
          )}
        </section>
      </div>

      <section className="panel hd-log" aria-labelledby="hd-log-title">
        <h2 id="hd-log-title" className="panel__title">
          Round by round
        </h2>
        {turns.length === 0 ? (
          <p className="muted">No round was played or recorded for this battle.</p>
        ) : (
          <div className="stack">
            {turns.map((t, i) => (
              <BattleRound key={t.turn ?? i} turn={t.turn ?? i + 1} lines={describeRound(t, names)} />
            ))}
          </div>
        )}
      </section>
    </div>
  );
}
