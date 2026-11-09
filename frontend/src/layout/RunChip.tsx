import { Link } from 'react-router-dom';
import { useGameStatus } from '../session/useGameStatus';
import './layout.css';

/** HUD chip with the run stage and wins (from GET /api/runs/current). Hidden when unavailable. */
export function RunChip() {
  const { run } = useGameStatus();
  if (run.status === 'loading') return <span className="run-chip run-chip--loading skeleton" aria-hidden="true" />;
  if (run.status === 'unavailable') return null;
  if (run.status === 'none') {
    return (
      <span className="run-chip run-chip--idle" title="No active run">
        No active run
      </span>
    );
  }
  const r = run.run;
  return (
    <Link to="/encounters" className="run-chip" data-boss={r.boss_available || undefined} title="Current run">
      <span className="run-chip__stage">
        <span className="run-chip__label">Stage</span>
        <span className="run-chip__value num">{r.stage}</span>
        <span className="run-chip__of num">/8</span>
      </span>
      <span className="run-chip__sep" aria-hidden="true" />
      <span className="run-chip__wins num">
        {r.wins_in_stage} {r.wins_in_stage === 1 ? 'win' : 'wins'}
      </span>
      {r.boss_available && <span className="run-chip__boss">Boss ready</span>}
      {r.status !== 'active' && <span className="run-chip__boss">{r.status}</span>}
    </Link>
  );
}
