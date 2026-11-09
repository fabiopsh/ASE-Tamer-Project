import { useState, type CSSProperties } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { isApiError } from '../../api/client';
import { getCurrentRun, listEncounters, startBattle } from '../../api/encounters';
import { listTeams, validateTeam } from '../../api/teams';
import type { EncounterTemplate, Run, Team, TeamValidation } from '../../api/types';
import { ElementBadge } from '../../components/Badges';
import { Button } from '../../components/Button';
import { ElementIcon } from '../../components/ElementIcon';
import { EmptyState } from '../../components/EmptyState';
import { ErrorBanner } from '../../components/ErrorBanner';
import { PageHeader } from '../../components/PageHeader';
import { Spinner } from '../../components/Spinner';
import { Sprite } from '../../components/Sprite';
import { useToast } from '../../components/toast-context';
import { useAsync } from '../../hooks/useAsync';
import { useGoToStarters } from '../../hooks/useGoToStarters';
import { capitalize } from '../../lib/format';
import { useGameStatus } from '../../session/useGameStatus';
import './encounters.css';

const TEAM_KEY = 'tamer.lastTeam';

function readLastTeam(): string | null {
  try {
    return localStorage.getItem(TEAM_KEY);
  } catch {
    return null;
  }
}
function writeLastTeam(id: string) {
  try {
    localStorage.setItem(TEAM_KEY, id);
  } catch {
    /* storage unavailable: the choice is simply not remembered */
  }
}

/** /encounters — run progress, available templates (US-17), team picker and battle start (US-18). */
export function EncountersPage() {
  const navigate = useNavigate();
  const toast = useToast();
  const goToStarters = useGoToStarters();
  const { refresh, battle } = useGameStatus();

  const run = useAsync((signal) => getCurrentRun(signal), 'enc:run');
  const encounters = useAsync((signal) => listEncounters(signal), 'enc:list');
  const teams = useAsync((signal) => listTeams(signal), 'enc:teams');

  const [templateId, setTemplateId] = useState<number | null>(null);
  const [teamId, setTeamId] = useState<string | null>(() => readLastTeam());
  const [starting, setStarting] = useState(false);
  const [startError, setStartError] = useState<unknown>(null);

  const teamList = teams.data ?? [];
  // Last used team if it still exists, otherwise the first one: the player always has a team picked.
  const team = teamList.find((t) => t.team_id === teamId) ?? teamList[0] ?? null;
  const template = (encounters.data ?? []).find((t) => t.template_id === templateId) ?? null;

  // Backend check of the chosen team, shown next to the picker.
  const validation = useAsync(
    (signal) => validateTeam(team!.members.map((m) => m.owned_id), signal),
    team ? `enc:validate:${team.team_id}:${team.updated_at}` : null,
  );

  async function start() {
    if (!template || !team) return;
    setStarting(true);
    setStartError(null);
    try {
      const b = await startBattle({ template_id: template.template_id, team_id: team.team_id });
      writeLastTeam(team.team_id);
      refresh();
      navigate('/battle', { state: { battle: b } });
    } catch (e) {
      if (isApiError(e) && e.code === 'BATTLE_IN_PROGRESS') {
        toast.show({ title: 'A battle is already in progress', message: 'Taking you back to it.', tone: 'info' });
        refresh();
        navigate('/battle');
        return;
      }
      setStartError(e);
      if (isApiError(e) && e.status === 404) encounters.reload();
    } finally {
      setStarting(false);
    }
  }

  const noRun =
    (run.data === null && run.error === null) ||
    (isApiError(encounters.error) && encounters.error.code === 'NO_ACTIVE_RUN');
  const r = run.data ?? null;

  return (
    <div className="page encounters">
      <PageHeader
        eyebrow="Venture out"
        title="Encounters"
        subtitle="Pick a place to explore and a team to bring. Win to catch the wild monster at its level and give XP to the whole team."
      />

      {battle && battle.status === 'in_progress' && (
        <ErrorBanner
          tone="info"
          title="You are in the middle of a battle"
          message={`Your fight against ${battle.wild.name} is waiting for you.`}
          action={
            <Link to="/battle" className="btn btn--primary btn--sm">
              Resume battle
            </Link>
          }
        />
      )}

      {run.error !== null && <ErrorBanner title="Run progress could not be loaded" error={run.error} onRetry={run.reload} />}
      {r && <RunHeader run={r} />}

      {noRun ? (
        <EmptyState
          title="No active run"
          message="You need an active run to explore. Choose your starters to begin a new one."
          action={
            <Button variant="primary" onClick={goToStarters}>
              Choose starters
            </Button>
          }
        />
      ) : r && r.status === 'won' ? (
        <EmptyState
          title="You conquered all eight stages!"
          message="This run is won. Start a new run from the Runs page whenever you are ready."
          action={
            <Link to="/runs" className="btn btn--gold">
              Go to my runs
            </Link>
          }
        />
      ) : (
        <div className="enc-layout">
          <section className="enc-list" aria-labelledby="enc-title">
            <h2 id="enc-title" className="visually-hidden">
              Available encounters
            </h2>
            {r?.reset_pending && (
              <ErrorBanner
                tone="warning"
                title="Your previous run is still closing"
                message="The game finishes it automatically in a moment. Battles can start once it is done."
                onRetry={run.reload}
              />
            )}
            {encounters.error !== null && !noRun && (
              <ErrorBanner title="Encounters could not be loaded" error={encounters.error} onRetry={encounters.reload} />
            )}
            {encounters.data === undefined && encounters.error === null ? (
              <div className="enc-grid">
                {Array.from({ length: 4 }, (_, i) => (
                  <div key={i} className="skeleton" style={{ height: 230, borderRadius: 16 }} />
                ))}
              </div>
            ) : encounters.data && encounters.data.length === 0 ? (
              <EmptyState title="No encounter available" message="The game did not offer any encounter for this run." />
            ) : encounters.data ? (
              <StageGroups
                encounters={encounters.data}
                run={r}
                selected={templateId}
                onSelect={(id) => {
                  setTemplateId(id === templateId ? null : id);
                  setStartError(null);
                }}
              />
            ) : null}
          </section>

          <aside className="enc-side panel" aria-labelledby="pick-title">
            <h2 id="pick-title" className="panel__title">
              Your team
            </h2>
            <TeamPicker
              teams={teamList}
              loading={teams.data === undefined && teams.error === null}
              error={teams.error}
              reload={teams.reload}
              selected={team?.team_id ?? null}
              onSelect={(id) => {
                setTeamId(id);
                setStartError(null);
              }}
            />
            {team && (
              <ValidationNote
                result={validation.data}
                loading={validation.loading}
                error={validation.error}
                teamId={team.team_id}
              />
            )}

            <div className="enc-summary">
              <span className="enc-summary__label">Destination</span>
              <span className="enc-summary__value">
                {template ? (
                  <>
                    {template.name} <span className="subtle">· Stage {template.stage}</span>
                  </>
                ) : (
                  <span className="subtle">Pick an encounter</span>
                )}
              </span>
            </div>

            {startError !== null && <StartError error={startError} team={team} onStarters={goToStarters} onRetry={() => void start()} />}

            <Button
              variant={template?.is_boss ? 'gold' : 'primary'}
              size="lg"
              block
              loading={starting}
              disabled={!template || !team}
              onClick={() => void start()}
            >
              <SwordsIcon /> {template?.is_boss ? 'Challenge the boss' : 'Start battle'}
            </Button>
          </aside>
        </div>
      )}

      {/* Phone: keep the start button in reach while scrolling the list. */}
      {!noRun && template && (
        <div className="enc-mobile-bar">
          <span className="enc-mobile-bar__text">
            <strong>{template.name}</strong>
            <span className="subtle">{team ? team.name : 'Choose a team below'}</span>
          </span>
          <Button
            variant={template.is_boss ? 'gold' : 'primary'}
            loading={starting}
            disabled={!team}
            onClick={() => void start()}
          >
            Start
          </Button>
        </div>
      )}
    </div>
  );
}

function RunHeader({ run }: { run: Run }) {
  const bossBeaten = (run.bosses_defeated ?? []).includes(run.stage);
  return (
    <section className="run-strip panel panel--tight fade-in" aria-label="Run progress">
      <div className="run-strip__item">
        <span className="run-strip__label">Stage</span>
        <span className="run-strip__value num">
          {run.stage}
          <span className="run-strip__of">/8</span>
        </span>
      </div>
      <div className="run-strip__item">
        <span className="run-strip__label">Wins in this stage</span>
        <span className="run-strip__value num">{run.wins_in_stage}</span>
      </div>
      <div className="run-strip__item run-strip__item--boss" data-state={run.boss_available ? 'ready' : bossBeaten ? 'done' : 'locked'}>
        <span className="run-strip__label">Stage boss</span>
        <span className="run-strip__value run-strip__value--text">
          {run.boss_available ? 'Unlocked' : bossBeaten ? 'Defeated' : 'Locked'}
        </span>
      </div>
      <div className="run-strip__item">
        <span className="run-strip__label">Bosses defeated</span>
        <span className="run-strip__value num">{(run.bosses_defeated ?? []).length}</span>
      </div>
    </section>
  );
}

function StageGroups({
  encounters,
  run,
  selected,
  onSelect,
}: {
  encounters: EncounterTemplate[];
  run: Run | null;
  selected: number | null;
  onSelect: (id: number) => void;
}) {
  const stages = [...new Set(encounters.map((e) => e.stage))].sort((a, b) => b - a);
  const currentStage = run?.stage ?? stages[0];
  const bossListed = encounters.some((e) => e.is_boss && e.stage === currentStage);
  const bossBeaten = run ? (run.bosses_defeated ?? []).includes(run.stage) : false;
  const showLocked = run !== null && run.status === 'active' && !bossListed && !run.boss_available && !bossBeaten;
  if (!stages.includes(currentStage)) stages.unshift(currentStage);

  let index = 0;
  return (
    <div className="stack stack--lg">
      {stages.map((stage) => {
        const items = encounters
          .filter((e) => e.stage === stage)
          .sort((a, b) => Number(b.is_boss) - Number(a.is_boss) || a.template_id - b.template_id);
        const isCurrent = stage === currentStage;
        return (
          <section key={stage} className="enc-stage" aria-label={`Stage ${stage}`}>
            <header className="enc-stage__head">
              <h3 className="enc-stage__title">Stage {stage}</h3>
              {isCurrent ? <span className="tag tag--accent">Current stage</span> : <span className="tag">Cleared · replay for XP</span>}
            </header>
            <div className="enc-grid stagger">
              {items.map((t) => (
                <EncounterCard
                  key={t.template_id}
                  t={t}
                  selected={selected === t.template_id}
                  onSelect={() => onSelect(t.template_id)}
                  index={index++}
                />
              ))}
              {isCurrent && showLocked && run && <LockedBoss run={run} />}
            </div>
          </section>
        );
      })}
    </div>
  );
}

const RARITY_ORDER = ['common', 'rare', 'legendary'] as const;

function EncounterCard({ t, selected, onSelect, index }: { t: EncounterTemplate; selected: boolean; onSelect: () => void; index: number }) {
  const weights = RARITY_ORDER.map((k) => [k, Number(t.rarity_weights?.[k] ?? 0)] as const);
  const total = weights.reduce((s, [, w]) => s + w, 0) || 1;
  const levels = t.level_min === t.level_max ? `Lv ${t.level_min}` : `Lv ${t.level_min}–${t.level_max}`;
  return (
    <button
      type="button"
      className={`enc-card${t.is_boss ? ' enc-card--boss' : ''}`}
      data-element={t.element}
      aria-pressed={selected}
      onClick={onSelect}
      style={{ '--i': index } as CSSProperties}
    >
      <span className="enc-card__bg" aria-hidden="true" />
      <span className="enc-card__top">
        {t.is_boss ? (
          <span className="boss-badge">
            <CrownIcon /> Boss
          </span>
        ) : (
          <span className="enc-card__stage num">Stage {t.stage}</span>
        )}
        {selected && (
          <span className="enc-card__check" aria-hidden="true">
            ✓
          </span>
        )}
      </span>
      <span className="enc-card__art" aria-hidden="true">
        {t.is_boss && t.boss_monster_id ? (
          <Sprite monsterId={t.boss_monster_id} alt="" size={128} element={t.element} float />
        ) : (
          <span className="enc-card__orb">
            <ElementIcon element={t.element} size={44} />
          </span>
        )}
      </span>
      <span className="enc-card__name">{t.name}</span>
      <span className="enc-card__meta">
        <ElementBadge element={t.element} size="sm" />
        <span className="enc-card__lv num">{levels}</span>
      </span>
      <span className="enc-card__rarity">
        <span className="enc-card__rarity-label">Wild rarity</span>
        <span className="rarity-bar" aria-hidden="true">
          {weights
            .filter(([, w]) => w > 0)
            .map(([k, w]) => (
              <span key={k} className="rarity-bar__seg" data-rarity={k} style={{ flexGrow: w }} />
            ))}
        </span>
        <span className="rarity-legend">
          {weights
            .filter(([, w]) => w > 0)
            .map(([k, w]) => (
              <span key={k} className="rarity-legend__item" data-rarity={k}>
                <span className="rarity-legend__dot" />
                {capitalize(k)} {Math.round((w / total) * 100)}%
              </span>
            ))}
        </span>
      </span>
    </button>
  );
}

function LockedBoss({ run }: { run: Run }) {
  return (
    <div className="enc-card enc-card--locked" aria-label="Stage boss locked">
      <span className="enc-card__top">
        <span className="boss-badge boss-badge--locked">
          <LockIcon /> Boss locked
        </span>
      </span>
      <span className="enc-card__art" aria-hidden="true">
        <span className="enc-card__orb enc-card__orb--locked">
          <LockIcon size={40} />
        </span>
      </span>
      <span className="enc-card__name">Guardian of stage {run.stage}</span>
      <span className="enc-card__hint">
        Keep winning encounters in this stage to unlock its boss. Wins in this stage so far:{' '}
        <strong className="num">{run.wins_in_stage}</strong>.
      </span>
    </div>
  );
}

function TeamPicker({
  teams,
  loading,
  error,
  reload,
  selected,
  onSelect,
}: {
  teams: Team[];
  loading: boolean;
  error: unknown;
  reload: () => void;
  selected: string | null;
  onSelect: (id: string) => void;
}) {
  if (error !== null) return <ErrorBanner compact title="Teams could not be loaded" error={error} onRetry={reload} />;
  if (loading)
    return (
      <div className="stack stack--sm">
        <div className="skeleton" style={{ height: 76 }} />
        <div className="skeleton" style={{ height: 76 }} />
      </div>
    );
  if (teams.length === 0)
    return (
      <div className="enc-noteam">
        <p className="muted">You need a team to fight.</p>
        <Link to="/teams/new" className="btn btn--sm btn--primary">
          Build a team
        </Link>
      </div>
    );
  return (
    <div className="team-pick" role="radiogroup" aria-label="Team">
      {teams.map((t) => (
        <button
          key={t.team_id}
          type="button"
          role="radio"
          aria-checked={selected === t.team_id}
          aria-label={`${t.name}: ${t.members.map((m) => `${m.name} level ${m.level}`).join(', ')}`}
          className="team-pick__item"
          onClick={() => onSelect(t.team_id)}
        >
          <span className="team-pick__name">{t.name}</span>
          <span className="team-pick__sprites">
            {t.members.map((m) => (
              <span key={m.owned_id} className="team-pick__member" title={`${m.name} · Lv ${m.level}`}>
                <Sprite src={m.image_url} monsterId={m.monster_id} alt={m.name} size={48} element={m.element} />
                <span className="team-pick__lv num">Lv {m.level}</span>
              </span>
            ))}
          </span>
        </button>
      ))}
      <Link to="/teams" className="team-pick__manage">
        Manage teams
      </Link>
    </div>
  );
}

function ValidationNote({
  result,
  loading,
  error,
  teamId,
}: {
  result: TeamValidation | undefined;
  loading: boolean;
  error: unknown;
  teamId: string;
}) {
  if (loading)
    return (
      <p className="enc-check">
        <Spinner size={14} /> Checking the team…
      </p>
    );
  if (error !== null || !result) return null;
  if (result.valid) return <p className="enc-check enc-check--ok">✓ Ready for battle</p>;
  return (
    <ErrorBanner
      compact
      tone="warning"
      title="This team cannot fight right now"
      message={result.errors.map((e) => e.message).join(' ')}
      action={
        <Link to={`/teams/${teamId}`} className="btn btn--sm">
          Edit team
        </Link>
      }
    />
  );
}

function StartError({
  error,
  team,
  onStarters,
  onRetry,
}: {
  error: unknown;
  team: Team | null;
  onStarters: () => void;
  onRetry: () => void;
}) {
  const code = isApiError(error) ? error.code : '';
  if (code === 'TEAM_INVALID') {
    return (
      <ErrorBanner
        compact
        title="This team cannot enter the battle"
        error={error}
        action={
          team ? (
            <Link to={`/teams/${team.team_id}`} className="btn btn--sm">
              Edit team
            </Link>
          ) : undefined
        }
      />
    );
  }
  if (code === 'RESET_PENDING') {
    return <ErrorBanner compact tone="warning" title="Your previous run is still closing" error={error} onRetry={onRetry} />;
  }
  if (code === 'NO_ACTIVE_RUN') {
    return (
      <ErrorBanner
        compact
        tone="info"
        title="No active run"
        error={error}
        action={
          <button type="button" className="btn btn--sm btn--primary" onClick={onStarters}>
            Choose starters
          </button>
        }
      />
    );
  }
  if (isApiError(error) && error.status === 404) {
    return <ErrorBanner compact title="Not available any more" message="This encounter or team is no longer available. The list has been refreshed." />;
  }
  return <ErrorBanner compact title="The battle could not start" error={error} onRetry={onRetry} />;
}

function SwordsIcon() {
  return (
    <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <path d="M14.5 17.5 3 6V3h3l11.5 11.5M13 19l6-6M16 16l4 4M19 21l2-2M9.5 6.5 14 2h3v3l-4.5 4.5M5 14l-2 2M3 19l2 2" />
    </svg>
  );
}

function CrownIcon() {
  return (
    <svg width="16" height="16" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true">
      <path d="M3 7l4.5 4L12 4l4.5 7L21 7l-2 12H5L3 7Z" />
    </svg>
  );
}

function LockIcon({ size = 16 }: { size?: number }) {
  return (
    <svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round" aria-hidden="true">
      <rect x="5" y="11" width="14" height="10" rx="2" />
      <path d="M8 11V8a4 4 0 0 1 8 0v3" />
    </svg>
  );
}
