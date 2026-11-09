import { Link, useParams } from 'react-router-dom';
import { isApiError } from '../../api/client';
import { getRun } from '../../api/encounters';
import { listHistory } from '../../api/history';
import { BackLink } from '../../components/BackLink';
import { ErrorBanner } from '../../components/ErrorBanner';
import { PageHeader } from '../../components/PageHeader';
import { useAsync } from '../../hooks/useAsync';
import { formatDateTime } from '../../lib/format';
import { HistoryRow } from '../history/HistoryRow';
import { RunStatusTag, StageDots } from './RunBits';
import './runs.css';

/** /runs/:id — one run: status, stage reached, bosses, dates, its latest battles. */
export function RunDetailPage() {
  const { id = '' } = useParams();
  const run = useAsync((signal) => getRun(id, signal), `run:${id}`);
  const battles = useAsync((signal) => listHistory({ run_id: id, size: 5 }, signal), `run:${id}:history`);
  const r = run.data;

  return (
    <div className="page page--narrow">
      <PageHeader
        before={<BackLink to="/runs" label="Runs" />}
        eyebrow="Run"
        title={r ? `Run of ${formatDateTime(r.started_at)}` : 'Run details'}
        actions={r ? <RunStatusTag status={r.status} /> : undefined}
      />

      {run.error !== null && (
        <ErrorBanner
          title={isApiError(run.error) && run.error.isNotFound ? 'This run does not exist' : 'The run could not be loaded'}
          error={run.error}
          onRetry={run.reload}
        />
      )}
      {!r && run.error === null && <div className="skeleton" style={{ height: 240, borderRadius: 16 }} />}

      {r && (
        <>
          <section className="panel stack" aria-label="Progress">
            <StageDots run={r} />
            <dl className="kv run-kv">
              <dt>Stage reached</dt>
              <dd className="num">{r.stage} / 8</dd>
              <dt>Wins in that stage</dt>
              <dd className="num">{r.wins_in_stage}</dd>
              <dt>Bosses defeated</dt>
              <dd>{(r.bosses_defeated ?? []).length > 0 ? (r.bosses_defeated ?? []).map((s) => `stage ${s}`).join(', ') : 'None'}</dd>
              <dt>Boss of the stage</dt>
              <dd>{r.boss_available ? 'Unlocked' : 'Locked'}</dd>
              <dt>Started</dt>
              <dd>{formatDateTime(r.started_at)}</dd>
              <dt>Ended</dt>
              <dd>{r.ended_at ? formatDateTime(r.ended_at) : 'Still in progress'}</dd>
              {r.battle_in_progress && (
                <>
                  <dt>Battle</dt>
                  <dd>
                    <Link to="/battle">A battle is in progress</Link>
                  </dd>
                </>
              )}
              {r.reset_pending && (
                <>
                  <dt>Closing</dt>
                  <dd>The game is still archiving this run and will finish by itself.</dd>
                </>
              )}
            </dl>
            <div className="cluster">
              <Link to={`/collection?run=${encodeURIComponent(r.run_id)}`} className="btn btn--sm">
                Monsters of this run
              </Link>
              <Link to={`/history?run=${encodeURIComponent(r.run_id)}`} className="btn btn--sm">
                All battles of this run
              </Link>
              {r.status === 'active' && (
                <Link to="/runs" className="btn btn--ghost btn--sm">
                  Abandon from the Runs page
                </Link>
              )}
            </div>
          </section>

          <section className="stack" aria-labelledby="run-battles">
            <h2 id="run-battles">Latest battles</h2>
            {battles.error !== null ? (
              <ErrorBanner compact title="Battle history is not available" error={battles.error} onRetry={battles.reload} />
            ) : battles.data === undefined ? (
              <div className="skeleton" style={{ height: 90, borderRadius: 12 }} />
            ) : battles.data.items.length === 0 ? (
              <p className="muted">No finished battle in this run.</p>
            ) : (
              <ul className="history-list">
                {battles.data.items.map((h) => (
                  <li key={h.encounter_id}>
                    <HistoryRow item={h} />
                  </li>
                ))}
              </ul>
            )}
          </section>
        </>
      )}
    </div>
  );
}
