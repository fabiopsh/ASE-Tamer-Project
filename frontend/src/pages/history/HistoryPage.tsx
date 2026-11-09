import type { CSSProperties } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { listRuns } from '../../api/encounters';
import { listHistory } from '../../api/history';
import type { BattleOutcome } from '../../api/types';
import { EmptyState } from '../../components/EmptyState';
import { ErrorBanner } from '../../components/ErrorBanner';
import { FilterChips, type ChipOption } from '../../components/FilterChips';
import { PageHeader } from '../../components/PageHeader';
import { Pagination } from '../../components/Pagination';
import { useAsync } from '../../hooks/useAsync';
import { OUTCOME_LABELS } from '../../lib/battleText';
import { capitalize, formatDate } from '../../lib/format';
import { HistoryRow } from './HistoryRow';
import './history.css';

const PAGE_SIZE = 10;
const OUTCOMES: BattleOutcome[] = ['victory', 'defeat', 'fled', 'abandoned', 'expired', 'interrupted'];
const OUTCOME_OPTIONS: ChipOption<BattleOutcome>[] = OUTCOMES.map((o) => ({ value: o, label: OUTCOME_LABELS[o] }));

/** /history — finished battles, newest first, filtered by outcome and run (US-23). */
export function HistoryPage() {
  const [params, setParams] = useSearchParams();
  const outcome = (params.get('outcome') as BattleOutcome | null) ?? null;
  const runId = params.get('run') ?? undefined;
  const page = Math.max(0, Number.parseInt(params.get('page') ?? '0', 10) || 0);

  const { data, error, loading, reload } = useAsync(
    (signal) => listHistory({ outcome: outcome ?? undefined, run_id: runId, page, size: PAGE_SIZE }, signal),
    `history:${outcome}:${runId}:${page}`,
  );
  const runs = useAsync((signal) => listRuns({ size: 50 }, signal), 'history:runs');

  function update(next: { outcome?: BattleOutcome | null; run?: string | null; page?: number }) {
    const p = new URLSearchParams(params);
    if (next.outcome !== undefined) {
      if (next.outcome) p.set('outcome', next.outcome);
      else p.delete('outcome');
    }
    if (next.run !== undefined) {
      if (next.run) p.set('run', next.run);
      else p.delete('run');
    }
    const np = next.page ?? 0;
    if (np > 0) p.set('page', String(np));
    else p.delete('page');
    setParams(p);
  }

  const filtered = outcome !== null || runId !== undefined;

  return (
    <div className="page">
      <PageHeader
        eyebrow="Chronicle"
        title="Battle history"
        subtitle="Every finished battle, with the team you brought and what happened round by round."
      />

      <section className="panel panel--tight history-filters" aria-label="Filters">
        <FilterChips label="Outcome" options={OUTCOME_OPTIONS} value={outcome} onChange={(v) => update({ outcome: v })} />
        <div className="field history-filters__run">
          <label className="field__label" htmlFor="history-run">
            Run
          </label>
          <select id="history-run" className="select" value={runId ?? ''} onChange={(e) => update({ run: e.target.value || null })}>
            <option value="">All runs</option>
            {(runs.data?.items ?? []).map((r) => (
              <option key={r.run_id} value={r.run_id}>
                {formatDate(r.started_at)} · {capitalize(r.status)} · stage {r.stage}
              </option>
            ))}
            {runId && !(runs.data?.items ?? []).some((r) => r.run_id === runId) && <option value={runId}>Selected run</option>}
          </select>
        </div>
      </section>

      {error !== null && <ErrorBanner title="The history could not be loaded" error={error} onRetry={reload} />}

      {data === undefined && error === null ? (
        <div className="stack stack--sm">
          {Array.from({ length: 4 }, (_, i) => (
            <div key={i} className="skeleton" style={{ height: 96, borderRadius: 12 }} />
          ))}
        </div>
      ) : data && data.items.length === 0 ? (
        <EmptyState
          title={filtered ? 'No battle matches these filters' : 'No battle yet'}
          message={filtered ? 'Try another outcome or run.' : 'Finished battles are recorded here automatically.'}
          action={
            filtered ? (
              <button type="button" className="btn btn--sm" onClick={() => setParams({})}>
                Clear filters
              </button>
            ) : (
              <Link to="/encounters" className="btn btn--primary btn--sm">
                Find an encounter
              </Link>
            )
          }
        />
      ) : data ? (
        <>
          <p className="muted num">
            {data.total_items} {data.total_items === 1 ? 'battle' : 'battles'}
          </p>
          <ul className={`history-list stagger${loading ? ' is-loading' : ''}`}>
            {data.items.map((h, i) => (
              <li key={h.encounter_id} style={{ '--i': i } as CSSProperties}>
                <HistoryRow item={h} />
              </li>
            ))}
          </ul>
          <Pagination
            page={data.page}
            totalPages={data.total_pages}
            totalItems={data.total_items}
            pageSize={data.size}
            disabled={loading}
            onPageChange={(p) => update({ page: p })}
          />
        </>
      ) : null}
    </div>
  );
}
