import { useState, type CSSProperties } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { errorMessage, isApiError } from '../../api/client';
import { abandonCurrentRun, getCurrentRun, listRuns, startNewRun } from '../../api/encounters';
import type { Run } from '../../api/types';
import { Button } from '../../components/Button';
import { ConfirmDialog } from '../../components/ConfirmDialog';
import { EmptyState } from '../../components/EmptyState';
import { ErrorBanner } from '../../components/ErrorBanner';
import { PageHeader } from '../../components/PageHeader';
import { Pagination } from '../../components/Pagination';
import { useToast } from '../../components/toast-context';
import { useAsync } from '../../hooks/useAsync';
import { useGoToStarters } from '../../hooks/useGoToStarters';
import { formatDate } from '../../lib/format';
import { useGameStatus } from '../../session/useGameStatus';
import { RunStatusTag, StageDots } from './RunBits';
import './runs.css';

const PAGE_SIZE = 10;

/** /runs — current run (abandon, start a new one after a win) and the list of past runs. */
export function RunsPage() {
  const toast = useToast();
  const goToStarters = useGoToStarters();
  const { refresh } = useGameStatus();
  const [params, setParams] = useSearchParams();
  const page = Math.max(0, Number.parseInt(params.get('page') ?? '0', 10) || 0);

  const current = useAsync((signal) => getCurrentRun(signal), 'runs:current');
  const list = useAsync((signal) => listRuns({ page, size: PAGE_SIZE }, signal), `runs:list:${page}`);

  const [confirmAbandon, setConfirmAbandon] = useState(false);
  const [busy, setBusy] = useState<'abandon' | 'new' | null>(null);
  const [runEnded, setRunEnded] = useState<Run | null>(null);
  const [actionError, setActionError] = useState<unknown>(null);

  async function abandon() {
    setBusy('abandon');
    setActionError(null);
    try {
      const r = await abandonCurrentRun();
      setConfirmAbandon(false);
      setRunEnded(r);
      toast.show({ title: 'Run abandoned', message: 'Choose new starters to begin again.', tone: 'info' });
      refresh();
      current.reload();
      list.reload();
    } catch (e) {
      setConfirmAbandon(false);
      setActionError(e);
    } finally {
      setBusy(null);
    }
  }

  async function newRun() {
    setBusy('new');
    setActionError(null);
    try {
      await startNewRun();
      refresh();
      goToStarters();
    } catch (e) {
      setActionError(e);
    } finally {
      setBusy(null);
    }
  }

  const run = current.data ?? null;

  return (
    <div className="page">
      <PageHeader
        eyebrow="Your journey"
        title="Runs"
        subtitle="A run goes from your three starters to the boss of stage 8. Losing every monster in a battle, or abandoning, ends it."
      />

      {actionError !== null && (
        <ErrorBanner
          title={isApiError(actionError) && actionError.code === 'RESET_PENDING' ? 'Your previous run is still closing' : 'That did not work'}
          error={actionError}
          onDismiss={() => setActionError(null)}
        />
      )}

      {runEnded && (
        <ErrorBanner
          tone="warning"
          title="Your run is over"
          message={
            runEnded.reset_pending
              ? 'The run is abandoned. The game is still archiving your collection and will finish by itself; you can choose new starters in a moment.'
              : 'The run is abandoned. Your monsters are archived and your teams are cleared. Choose three new starters to begin a new run.'
          }
          action={
            <Button variant="primary" size="sm" onClick={goToStarters}>
              Choose starters
            </Button>
          }
        />
      )}

      <section className="panel current-run" aria-labelledby="current-run-title">
        <h2 id="current-run-title" className="panel__title">
          Current run
        </h2>
        {current.error !== null ? (
          <ErrorBanner compact error={current.error} onRetry={current.reload} />
        ) : current.data === undefined ? (
          <div className="skeleton" style={{ height: 120 }} />
        ) : !run ? (
          <div className="stack">
            <p className="muted">There is no run open. Choose your starters to begin one.</p>
            <div>
              <Button variant="primary" onClick={goToStarters}>
                Choose starters
              </Button>
            </div>
          </div>
        ) : (
          <div className="current-run__body">
            <div className="current-run__info">
              <div className="cluster">
                <RunStatusTag status={run.status} />
                {run.battle_in_progress && <span className="tag tag--info">Battle in progress</span>}
                {run.reset_pending && <span className="tag tag--warning">Closing…</span>}
              </div>
              <StageDots run={run} />
              <p className="muted text-sm">
                Started {formatDate(run.started_at)} · Stage {run.stage} · {run.wins_in_stage}{' '}
                {run.wins_in_stage === 1 ? 'win' : 'wins'} in this stage ·{' '}
                {run.boss_available ? 'boss unlocked' : 'boss locked'}
              </p>
            </div>
            <div className="current-run__actions">
              <Link to={`/runs/${run.run_id}`} className="btn">
                Details
              </Link>
              {run.status === 'active' && (
                <Button variant="danger" onClick={() => setConfirmAbandon(true)} disabled={run.reset_pending}>
                  Abandon run
                </Button>
              )}
              {run.status === 'won' && (
                <Button variant="gold" loading={busy === 'new'} onClick={() => void newRun()}>
                  Start new run
                </Button>
              )}
            </div>
          </div>
        )}
      </section>

      <section className="stack" aria-labelledby="all-runs-title">
        <h2 id="all-runs-title">All runs</h2>
        {list.error !== null && <ErrorBanner error={list.error} onRetry={list.reload} />}
        {list.data === undefined && list.error === null ? (
          <div className="stack stack--sm">
            {Array.from({ length: 3 }, (_, i) => (
              <div key={i} className="skeleton" style={{ height: 84, borderRadius: 12 }} />
            ))}
          </div>
        ) : list.data && list.data.items.length === 0 ? (
          <EmptyState title="No runs yet" message="Your runs will appear here once you choose your starters." />
        ) : list.data ? (
          <>
            <ul className={`run-list stagger${list.loading ? ' is-loading' : ''}`}>
              {list.data.items.map((r, i) => (
                <li key={r.run_id} style={{ '--i': i } as CSSProperties}>
                  <Link to={`/runs/${r.run_id}`} className="run-row" data-status={r.status}>
                    <span className="run-row__stage">
                      <span className="run-row__stage-label">Stage</span>
                      <span className="run-row__stage-value num">{r.stage}</span>
                    </span>
                    <span className="run-row__main">
                      <span className="cluster">
                        <RunStatusTag status={r.status} />
                        {r.run_id === run?.run_id && <span className="tag tag--accent">Current</span>}
                      </span>
                      <span className="run-row__dates">
                        {formatDate(r.started_at)} – {r.ended_at ? formatDate(r.ended_at) : 'now'}
                      </span>
                    </span>
                    <span className="run-row__bosses">
                      <span className="num">{(r.bosses_defeated ?? []).length}</span> bosses defeated
                    </span>
                    <span className="run-row__chev" aria-hidden="true">
                      ›
                    </span>
                  </Link>
                </li>
              ))}
            </ul>
            <Pagination
              page={list.data.page}
              totalPages={list.data.total_pages}
              totalItems={list.data.total_items}
              pageSize={list.data.size}
              disabled={list.loading}
              onPageChange={(p) => setParams(p > 0 ? { page: String(p) } : {})}
            />
          </>
        ) : null}
      </section>

      <ConfirmDialog
        open={confirmAbandon}
        title="Abandon this run?"
        confirmLabel="Abandon run"
        danger
        busy={busy === 'abandon'}
        onConfirm={() => void abandon()}
        onCancel={() => setConfirmAbandon(false)}
      >
        <p>This cannot be undone. If a battle is open it ends as abandoned, without reward.</p>
        <ul className="dialog-list">
          <li>Every monster of this run is archived: you keep seeing them in past runs, but they cannot fight again.</li>
          <li>Your teams are removed.</li>
          <li>You start over at stage 1 with three new starters.</li>
        </ul>
        {actionError !== null && <p className="field__error">{errorMessage(actionError)}</p>}
      </ConfirmDialog>
    </div>
  );
}
