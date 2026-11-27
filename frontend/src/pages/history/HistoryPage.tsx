import { useState, type CSSProperties } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { fetchAllPages } from '../../api/client';
import { listRuns } from '../../api/encounters';
import { listHistory, verifyHistory, type HistoryVerification } from '../../api/history';
import type { BattleOutcome } from '../../api/types';
import { Button } from '../../components/Button';
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
const OUTCOMES: readonly BattleOutcome[] = ['victory', 'defeat', 'fled', 'abandoned', 'expired', 'interrupted'];
const OUTCOME_OPTIONS: ChipOption<BattleOutcome>[] = OUTCOMES.map((o) => ({ value: o, label: OUTCOME_LABELS[o] }));

function parseOutcome(raw: string | null): BattleOutcome | null {
  if (!raw) return null;
  return (OUTCOMES as readonly string[]).includes(raw) ? (raw as BattleOutcome) : null;
}

/** /history — finished battles, newest first, filtered by outcome and run (US-23), with HI-2 integrity verification. */
export function HistoryPage() {
  const [params, setParams] = useSearchParams();
  const outcome = parseOutcome(params.get('outcome'));
  const runId = params.get('run') ?? undefined;
  const page = Math.max(0, Number.parseInt(params.get('page') ?? '0', 10) || 0);

  const [verifying, setVerifying] = useState(false);
  const [verification, setVerification] = useState<HistoryVerification | null>(null);
  const [verifyError, setVerifyError] = useState<unknown>(null);

  const { data, error, loading, reload } = useAsync(
    (signal) => listHistory({ outcome: outcome ?? undefined, run_id: runId, page, size: PAGE_SIZE }, signal),
    `history:${outcome}:${runId}:${page}`,
  );
  const runs = useAsync(
    (signal) => fetchAllPages((p, size) => listRuns({ page: p, size }, signal)),
    'history:runs',
  );

  async function runVerify() {
    setVerifying(true);
    setVerifyError(null);
    try {
      const res = await verifyHistory();
      setVerification(res);
    } catch (err) {
      setVerifyError(err);
    } finally {
      setVerifying(false);
    }
  }

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
  const runItems = runs.data ?? [];

  return (
    <div className="page">
      <PageHeader
        eyebrow="Chronicle"
        title="Battle history"
        subtitle="Every finished battle, with the team you brought and what happened round by round."
        actions={
          <div className="cluster">
            {verification && (
              <span
                className={`tag ${verification.valid ? 'tag--success' : 'tag--danger'}`}
                title={verification.head_hash ? `Head hash: ${verification.head_hash}` : undefined}
              >
                {verification.valid ? 'Chain verified' : 'Chain tampered'}
              </span>
            )}
            <Button variant="ghost" size="sm" loading={verifying} onClick={() => void runVerify()}>
              Verify integrity
            </Button>
          </div>
        }
      />

      {verifyError !== null && (
        <ErrorBanner
          compact
          title="History integrity check failed"
          error={verifyError}
          onRetry={() => void runVerify()}
          onDismiss={() => setVerifyError(null)}
        />
      )}

      {verification && (
        <ErrorBanner
          compact
          tone={verification.valid ? 'success' : 'error'}
          title={
            verification.valid
              ? `Integrity verified (${verification.records} ${verification.records === 1 ? 'record' : 'records'})`
              : 'History integrity check detected tampering'
          }
          message={
            verification.valid
              ? verification.head_hash
                ? `Hash chain intact. Head hash: ${verification.head_hash.slice(0, 12)}…`
                : 'Hash chain intact.'
              : [
                  `Checked ${verification.records} ${verification.records === 1 ? 'record' : 'records'}.`,
                  verification.first_broken_seq != null ? `First broken sequence: #${verification.first_broken_seq}.` : null,
                  verification.first_invalid ? `First invalid record: ${verification.first_invalid}.` : null,
                ]
                  .filter(Boolean)
                  .join(' ')
          }
          onDismiss={() => setVerification(null)}
        />
      )}

      <section className="panel panel--tight history-filters" aria-label="Filters">
        <FilterChips label="Outcome" options={OUTCOME_OPTIONS} value={outcome} onChange={(v) => update({ outcome: v })} />
        <div className="field history-filters__run">
          <label className="field__label" htmlFor="history-run">
            Run
          </label>
          <select id="history-run" className="select" value={runId ?? ''} onChange={(e) => update({ run: e.target.value || null })}>
            <option value="">All runs</option>
            {runItems.map((r) => (
              <option key={r.run_id} value={r.run_id}>
                {formatDate(r.started_at)} · {capitalize(r.status)} · stage {r.stage}
              </option>
            ))}
            {runId && !runItems.some((r) => r.run_id === runId) && <option value={runId}>Selected run</option>}
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
