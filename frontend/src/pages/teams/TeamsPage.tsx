import { useState, type CSSProperties } from 'react';
import { Link } from 'react-router-dom';
import { errorMessage } from '../../api/client';
import { deleteTeam, listTeams } from '../../api/teams';
import type { Team } from '../../api/types';
import { ElementBadge, LevelBadge } from '../../components/Badges';
import { Button } from '../../components/Button';
import { ConfirmDialog } from '../../components/ConfirmDialog';
import { EmptyState } from '../../components/EmptyState';
import { ErrorBanner } from '../../components/ErrorBanner';
import { PageHeader } from '../../components/PageHeader';
import { Sprite } from '../../components/Sprite';
import { useToast } from '../../components/toast-context';
import { useAsync } from '../../hooks/useAsync';
import { formatDate } from '../../lib/format';
import './teams.css';

/** /teams — saved teams of the current run, in battle order (US-14, US-16). */
export function TeamsPage() {
  const toast = useToast();
  const { data, error, reload } = useAsync((signal) => listTeams(signal), 'teams');
  const [toDelete, setToDelete] = useState<Team | null>(null);
  const [deleting, setDeleting] = useState(false);

  async function confirmDelete() {
    if (!toDelete) return;
    setDeleting(true);
    try {
      await deleteTeam(toDelete.team_id);
      toast.show({ title: `Team "${toDelete.name}" deleted`, tone: 'success' });
      setToDelete(null);
      reload();
    } catch (e) {
      toast.show({ title: 'The team could not be deleted', message: errorMessage(e), tone: 'error' });
    } finally {
      setDeleting(false);
    }
  }

  return (
    <div className="page">
      <PageHeader
        eyebrow="Prepare"
        title="Your teams"
        subtitle="A team is one to three monsters of this run. The first slot enters the battle first; the others step in when it falls."
        actions={
          <Link to="/teams/new" className="btn btn--primary">
            <PlusIcon /> New team
          </Link>
        }
      />

      {error !== null && <ErrorBanner title="Your teams could not be loaded" error={error} onRetry={reload} />}

      {data === undefined && error === null ? (
        <div className="team-grid">
          {Array.from({ length: 3 }, (_, i) => (
            <div key={i} className="panel skeleton" style={{ height: 250 }} />
          ))}
        </div>
      ) : data && data.length === 0 ? (
        <EmptyState
          title="No team yet"
          message="Build your first team to start an encounter. Pick up to three monsters from your collection and choose their order."
          action={
            <Link to="/teams/new" className="btn btn--primary">
              Build a team
            </Link>
          }
        />
      ) : data ? (
        <>
          <p className="muted num">
            {data.length} {data.length === 1 ? 'team' : 'teams'}
          </p>
          <div className="team-grid stagger">
            {data.map((t, i) => (
              <TeamCard key={t.team_id} team={t} index={i} onDelete={() => setToDelete(t)} />
            ))}
          </div>
        </>
      ) : null}

      <ConfirmDialog
        open={toDelete !== null}
        title={`Delete team "${toDelete?.name ?? ''}"?`}
        confirmLabel="Delete team"
        danger
        busy={deleting}
        onConfirm={() => void confirmDelete()}
        onCancel={() => setToDelete(null)}
      >
        <p>The monsters stay in your collection; only this team arrangement is removed.</p>
      </ConfirmDialog>
    </div>
  );
}

function TeamCard({ team, index, onDelete }: { team: Team; index: number; onDelete: () => void }) {
  const lead = team.members[0];
  return (
    <article className="team-card panel" data-element={lead?.element} style={{ '--i': index } as CSSProperties}>
      <span className="team-card__glow" aria-hidden="true" />
      <header className="team-card__head">
        <h2 className="team-card__name">{team.name}</h2>
        <span className="subtle text-sm">Updated {formatDate(team.updated_at)}</span>
      </header>
      <ol className="team-card__members">
        {team.members.map((m) => (
          <li key={m.owned_id} className="team-card__member" data-element={m.element}>
            <span className="team-card__slot num" aria-label={`Slot ${m.position}`}>
              {m.position}
            </span>
            <Sprite src={m.image_url} monsterId={m.monster_id} alt="" size={64} element={m.element} />
            <span className="team-card__info">
              <span className="team-card__mname">{m.name}</span>
              <span className="cluster">
                <LevelBadge level={m.level} />
                <ElementBadge element={m.element} size="sm" iconOnly />
              </span>
            </span>
          </li>
        ))}
        {Array.from({ length: Math.max(0, 3 - team.members.length) }, (_, i) => (
          <li key={`empty-${i}`} className="team-card__member team-card__member--empty">
            <span className="team-card__slot num">{team.members.length + i + 1}</span>
            <span className="subtle text-sm">Empty slot</span>
          </li>
        ))}
      </ol>
      <footer className="team-card__actions">
        <Link to={`/teams/${team.team_id}`} className="btn btn--sm">
          Edit
        </Link>
        <Button variant="ghost" size="sm" onClick={onDelete}>
          Delete
        </Button>
      </footer>
    </article>
  );
}

function PlusIcon() {
  return (
    <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.6" strokeLinecap="round" aria-hidden="true">
      <path d="M12 5v14M5 12h14" />
    </svg>
  );
}
