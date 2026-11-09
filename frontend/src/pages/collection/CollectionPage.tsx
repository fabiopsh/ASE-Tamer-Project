import type { CSSProperties } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { listRuns } from '../../api/encounters';
import { listCollection } from '../../api/monsters';
import type { Run } from '../../api/types';
import { EmptyState } from '../../components/EmptyState';
import { ErrorBanner } from '../../components/ErrorBanner';
import { XpBar } from '../../components/HpBar';
import { MonsterCard, MonsterCardSkeleton } from '../../components/MonsterCard';
import { PageHeader } from '../../components/PageHeader';
import { Pagination } from '../../components/Pagination';
import { useAsync } from '../../hooks/useAsync';
import { capitalize, formatDate } from '../../lib/format';
import { useSession } from '../../session/useSession';

const PAGE_SIZE = 20;

function runLabel(r: Run): string {
  const when = r.started_at ? formatDate(r.started_at) : `run ${r.run_id.slice(0, 6)}`;
  return `${when} · ${capitalize(r.status)} at stage ${r.stage}`;
}

/** /collection — owned monsters of the current run, or of a past run via the run selector (US-09). */
export function CollectionPage() {
  const { player } = useSession();
  const [params, setParams] = useSearchParams();
  const runId = params.get('run') ?? undefined;
  const page = Math.max(0, Number.parseInt(params.get('page') ?? '0', 10) || 0);

  const { data, error, loading, reload } = useAsync(
    (signal) => listCollection({ run_id: runId, page, size: PAGE_SIZE }, signal),
    `collection:${runId ?? 'current'}:${page}`,
  );
  // Past runs come from the encounters service; the selector stays hidden while it is unavailable.
  const runs = useAsync((signal) => listRuns({ size: 50 }, signal), 'runs');
  const pastRuns = (runs.data?.items ?? []).filter((r) => r.run_id !== player?.current_run_id);

  function update(next: { run?: string | null; page?: number }) {
    const p = new URLSearchParams(params);
    if (next.run !== undefined) {
      if (next.run) p.set('run', next.run);
      else p.delete('run');
    }
    const np = next.page ?? 0;
    if (np > 0) p.set('page', String(np));
    else p.delete('page');
    setParams(p);
    if (next.page !== undefined) window.scrollTo({ top: 0, behavior: 'smooth' });
  }

  const viewingPast = runId !== undefined;

  return (
    <div className="page">
      <PageHeader
        eyebrow={viewingPast ? 'Archive' : 'Current run'}
        title="My collection"
        subtitle={
          viewingPast
            ? 'Monsters from a past run. They are archived: they keep their levels but cannot join new teams.'
            : 'The monsters travelling with you in this run. Win encounters to catch more and level them up.'
        }
        actions={
          pastRuns.length > 0 ? (
            <div className="field" style={{ minWidth: 260 }}>
              <label className="field__label" htmlFor="run-select">
                Run
              </label>
              <select
                id="run-select"
                className="select"
                value={runId ?? ''}
                onChange={(e) => update({ run: e.target.value || null })}
              >
                <option value="">Current run</option>
                {pastRuns.map((r) => (
                  <option key={r.run_id} value={r.run_id}>
                    {runLabel(r)}
                  </option>
                ))}
              </select>
            </div>
          ) : undefined
        }
      />

      {error !== null && <ErrorBanner title="Your collection could not be loaded" error={error} onRetry={reload} />}

      {data === undefined && error === null ? (
        <div className="grid-cards">
          {Array.from({ length: 6 }, (_, i) => (
            <MonsterCardSkeleton key={i} />
          ))}
        </div>
      ) : data && data.items.length === 0 ? (
        <EmptyState
          title={viewingPast ? 'No monsters in this run' : 'Your collection is empty'}
          message={
            viewingPast
              ? 'This run ended before any monster joined you.'
              : 'Choose your starters or win an encounter to welcome your first monster.'
          }
          action={
            <Link to={viewingPast ? '/collection' : '/encounters'} className="btn btn--primary btn--sm">
              {viewingPast ? 'Back to the current run' : 'Find an encounter'}
            </Link>
          }
        />
      ) : data ? (
        <>
          <p className="muted num" aria-live="polite">
            {data.total_items} {data.total_items === 1 ? 'monster' : 'monsters'}
          </p>
          <div className={`grid-cards stagger${loading ? ' is-loading' : ''}`} key={`${runId}:${data.page}`}>
            {data.items.map((m, i) => (
              <MonsterCard
                key={m.owned_id}
                monster={m}
                to={`/collection/${m.owned_id}`}
                level={m.level}
                corner={m.status === 'archived' ? <span className="tag">Archived</span> : undefined}
                footer={
                  <XpBar
                    xp={m.xp}
                    toNext={m.xp_to_next_level}
                    level={m.status === 'archived' ? m.level : undefined}
                  />
                }
                style={{ '--i': i } as CSSProperties}
              />
            ))}
          </div>
          <Pagination
            page={data.page}
            totalPages={data.total_pages}
            totalItems={data.total_items}
            pageSize={data.size}
            onPageChange={(p) => update({ page: p })}
            disabled={loading}
          />
        </>
      ) : null}
    </div>
  );
}
