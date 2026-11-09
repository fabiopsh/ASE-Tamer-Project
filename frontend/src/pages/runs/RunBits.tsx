import type { Run, RunStatus } from '../../api/types';

const STATUS: Record<RunStatus, { label: string; tone: string }> = {
  active: { label: 'Active', tone: 'tag--info' },
  won: { label: 'Won', tone: 'tag--success' },
  lost: { label: 'Lost', tone: 'tag--danger' },
  abandoned: { label: 'Abandoned', tone: 'tag--warning' },
};

export function RunStatusTag({ status }: { status: RunStatus | string }) {
  const s = STATUS[status as RunStatus] ?? { label: status, tone: '' };
  return <span className={`tag ${s.tone}`}>{s.label}</span>;
}

/** Eight stage dots: beaten bosses, the stage reached, the rest. */
export function StageDots({ run }: { run: Run }) {
  const beaten = new Set(run.bosses_defeated ?? []);
  return (
    <ol className="stage-dots" aria-label={`Stage ${run.stage} of 8, ${beaten.size} bosses defeated`}>
      {Array.from({ length: 8 }, (_, i) => {
        const s = i + 1;
        const state = beaten.has(s) ? 'done' : s === run.stage ? 'current' : 'todo';
        return (
          <li key={s} className="stage-dots__dot num" data-state={state} title={`Stage ${s}`}>
            {state === 'done' ? '✓' : s}
          </li>
        );
      })}
    </ol>
  );
}
