import { useEffect, useState, type CSSProperties, type DragEvent } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { isApiError } from '../../api/client';
import { listCollection } from '../../api/monsters';
import { createTeam, getTeam, updateTeam, validateTeam } from '../../api/teams';
import type { OwnedMonster, TeamValidation, Uuid } from '../../api/types';
import { BackLink, DetailSkeleton } from '../../components/BackLink';
import { ElementBadge, LevelBadge } from '../../components/Badges';
import { Button } from '../../components/Button';
import { EmptyState } from '../../components/EmptyState';
import { ErrorBanner } from '../../components/ErrorBanner';
import { MonsterCard, MonsterCardSkeleton } from '../../components/MonsterCard';
import { PageHeader } from '../../components/PageHeader';
import { Spinner } from '../../components/Spinner';
import { Sprite } from '../../components/Sprite';
import { useToast } from '../../components/toast-context';
import { useAsync } from '../../hooks/useAsync';
import './teams.css';

const SLOTS = 3;

/**
 * /teams/new and /teams/:id — pick up to three active monsters, order them, name the team.
 * Every change is checked with POST /api/teams/validate: the client shows the backend errors and never decides
 * validity itself. The backend validates again on save.
 */
export function TeamEditorPage() {
  const { id } = useParams();
  const editing = id !== undefined;
  const team = useAsync((signal) => getTeam(id!, signal), editing ? `team:${id}` : null);
  const collection = useAsync((signal) => listCollection({ size: 50 }, signal), 'editor:collection');

  if (editing && team.error !== null) {
    return (
      <div className="page page--narrow">
        <BackLink to="/teams" label="Teams" />
        <ErrorBanner
          title={isApiError(team.error) && team.error.isNotFound ? 'This team does not exist' : 'The team could not be loaded'}
          error={team.error}
          onRetry={team.reload}
        />
      </div>
    );
  }
  if (editing && team.data === undefined) {
    return (
      <div className="page">
        <BackLink to="/teams" label="Teams" />
        <DetailSkeleton />
      </div>
    );
  }
  return (
    <Editor
      key={team.data?.team_id ?? 'new'}
      teamId={team.data?.team_id}
      initialName={team.data?.name ?? ''}
      initialMembers={team.data?.members.map((m) => m.owned_id) ?? []}
      collection={collection.data?.items}
      collectionError={collection.error}
      reloadCollection={collection.reload}
    />
  );
}

interface EditorProps {
  teamId?: Uuid;
  initialName: string;
  initialMembers: Uuid[];
  collection: OwnedMonster[] | undefined;
  collectionError: unknown;
  reloadCollection: () => void;
}

function Editor({ teamId, initialName, initialMembers, collection, collectionError, reloadCollection }: EditorProps) {
  const navigate = useNavigate();
  const toast = useToast();
  const [name, setName] = useState(initialName);
  const [members, setMembers] = useState<Uuid[]>(initialMembers);
  const [saving, setSaving] = useState(false);
  const [saveError, setSaveError] = useState<unknown>(null);
  const [dragFrom, setDragFrom] = useState<number | null>(null);

  // Live validation: one request per change of the member list (debounced, the previous one aborted).
  const membersKey = members.join(',');
  const [validation, setValidation] = useState<{ key: string; result: TeamValidation | null; error: unknown }>({
    key: '',
    result: null,
    error: null,
  });
  useEffect(() => {
    const ctrl = new AbortController();
    const ids = membersKey ? membersKey.split(',') : [];
    const timer = window.setTimeout(() => {
      validateTeam(ids, ctrl.signal).then(
        (result) => setValidation({ key: membersKey, result, error: null }),
        (error: unknown) => {
          if (error instanceof DOMException && error.name === 'AbortError') return;
          setValidation({ key: membersKey, result: null, error });
        },
      );
    }, 180);
    return () => {
      window.clearTimeout(timer);
      ctrl.abort();
    };
  }, [membersKey]);
  const checking = validation.key !== membersKey;
  const result = checking ? null : validation.result;

  const byId = new Map((collection ?? []).map((m) => [m.owned_id, m]));
  const memberErrors = new Map<string, string[]>();
  for (const e of result?.errors ?? []) {
    if (e.owned_id) memberErrors.set(e.owned_id, [...(memberErrors.get(e.owned_id) ?? []), e.message]);
  }
  const teamErrors = (result?.errors ?? []).filter((e) => !e.owned_id);

  function toggle(ownedId: Uuid) {
    setSaveError(null);
    setMembers((list) => {
      if (list.includes(ownedId)) return list.filter((x) => x !== ownedId);
      if (list.length >= SLOTS) {
        toast.show({ title: 'All three slots are taken', message: 'Remove a monster from the team first.', tone: 'info' });
        return list;
      }
      return [...list, ownedId];
    });
  }

  function move(from: number, to: number) {
    if (to < 0 || to >= members.length || from === to) return;
    setSaveError(null);
    setMembers((list) => {
      const next = [...list];
      const [item] = next.splice(from, 1);
      next.splice(to, 0, item);
      return next;
    });
  }

  async function save() {
    setSaving(true);
    setSaveError(null);
    try {
      const body = { name: name.trim(), members };
      const saved = teamId ? await updateTeam(teamId, body) : await createTeam(body);
      toast.show({ title: `Team "${saved.name}" saved`, tone: 'success' });
      navigate('/teams');
    } catch (e) {
      setSaveError(e);
    } finally {
      setSaving(false);
    }
  }

  const saveCodes = isApiError(saveError) ? saveError.details.map((d) => d.code).filter(Boolean) : [];
  const nameRejected = saveCodes.includes('NAME_INVALID');

  return (
    <div className="page">
      <PageHeader
        before={<BackLink to="/teams" label="Teams" />}
        eyebrow="Prepare"
        title={teamId ? `Edit ${initialName || 'team'}` : 'New team'}
        subtitle="Pick up to three monsters from your collection. Slot 1 fights first; drag or use the arrows to change the order."
      />

      <div className="editor">
        <section className="panel editor__team" aria-labelledby="team-slots">
          <div className="field">
            <label className="field__label" htmlFor="team-name">
              Team name
            </label>
            <input
              id="team-name"
              className="input"
              value={name}
              maxLength={60}
              placeholder="e.g. Night Watch"
              aria-invalid={nameRejected || undefined}
              onChange={(e) => {
                setName(e.target.value);
                setSaveError(null);
              }}
            />
            <span className="field__hint">Letters, digits, spaces, _ and -. Checked when you save.</span>
          </div>

          <h2 id="team-slots" className="editor__subtitle">
            Battle order
          </h2>
          <ol className="slots">
            {Array.from({ length: SLOTS }, (_, i) => {
              const ownedId = members[i];
              const m = ownedId ? byId.get(ownedId) : undefined;
              if (!ownedId) {
                return (
                  <li key={`empty-${i}`} className="slot slot--empty">
                    <span className="slot__num num">{i + 1}</span>
                    <span className="subtle">Pick a monster from your collection</span>
                  </li>
                );
              }
              const errs = memberErrors.get(ownedId);
              return (
                <li
                  key={ownedId}
                  className={`slot${errs ? ' slot--invalid' : ''}${dragFrom === i ? ' slot--dragging' : ''}`}
                  data-element={m?.element}
                  draggable
                  onDragStart={(e: DragEvent) => {
                    setDragFrom(i);
                    e.dataTransfer.effectAllowed = 'move';
                    e.dataTransfer.setData('text/plain', String(i));
                  }}
                  onDragEnd={() => setDragFrom(null)}
                  onDragOver={(e) => e.preventDefault()}
                  onDrop={(e) => {
                    e.preventDefault();
                    const from = Number(e.dataTransfer.getData('text/plain'));
                    if (Number.isInteger(from)) move(from, i);
                    setDragFrom(null);
                  }}
                >
                  <span className="slot__grip" aria-hidden="true">
                    <svg width="14" height="20" viewBox="0 0 14 20" fill="currentColor">
                      <circle cx="4" cy="4" r="1.6" /><circle cx="10" cy="4" r="1.6" />
                      <circle cx="4" cy="10" r="1.6" /><circle cx="10" cy="10" r="1.6" />
                      <circle cx="4" cy="16" r="1.6" /><circle cx="10" cy="16" r="1.6" />
                    </svg>
                  </span>
                  <span className="slot__num num">{i + 1}</span>
                  {m ? (
                    <Sprite src={m.image_url} alt="" size={64} element={m.element} />
                  ) : (
                    <Sprite alt="" size={64} />
                  )}
                  <span className="slot__info">
                    <span className="slot__name">{m?.name ?? 'Unknown monster'}</span>
                    {m && (
                      <span className="cluster">
                        <LevelBadge level={m.level} />
                        <ElementBadge element={m.element} size="sm" />
                      </span>
                    )}
                    {errs?.map((msg) => (
                      <span key={msg} className="field__error">
                        {msg}
                      </span>
                    ))}
                  </span>
                  <span className="slot__actions">
                    <button
                      type="button"
                      className="btn btn--ghost btn--sm btn--icon"
                      aria-label={`Move ${m?.name ?? 'monster'} up`}
                      disabled={i === 0}
                      onClick={() => move(i, i - 1)}
                    >
                      <Chevron dir="up" />
                    </button>
                    <button
                      type="button"
                      className="btn btn--ghost btn--sm btn--icon"
                      aria-label={`Move ${m?.name ?? 'monster'} down`}
                      disabled={i === members.length - 1}
                      onClick={() => move(i, i + 1)}
                    >
                      <Chevron dir="down" />
                    </button>
                    <button
                      type="button"
                      className="btn btn--ghost btn--sm btn--icon"
                      aria-label={`Remove ${m?.name ?? 'monster'}`}
                      onClick={() => toggle(ownedId)}
                    >
                      ✕
                    </button>
                  </span>
                </li>
              );
            })}
          </ol>

          <div className="validation" aria-live="polite">
            {checking ? (
              <p className="validation__state">
                <Spinner size={16} /> Checking the team…
              </p>
            ) : validation.error ? (
              <ErrorBanner compact tone="warning" title="The team could not be checked" error={validation.error} />
            ) : result?.valid ? (
              <p className="validation__state validation__state--ok">
                <CheckIcon /> This team is ready for battle.
              </p>
            ) : result ? (
              <div className="validation__errors">
                <p className="validation__state validation__state--bad">Not ready yet</p>
                <ul>
                  {teamErrors.map((e) => (
                    <li key={e.code + e.message}>{e.message}</li>
                  ))}
                  {teamErrors.length === 0 && <li>Fix the monsters marked above.</li>}
                </ul>
              </div>
            ) : null}
          </div>

          {saveError !== null && (
            <ErrorBanner compact title="The team was not saved" error={saveError} />
          )}

          <div className="editor__actions">
            <Link to="/teams" className="btn btn--ghost">
              Cancel
            </Link>
            <Button variant="primary" loading={saving} onClick={() => void save()}>
              {teamId ? 'Save changes' : 'Create team'}
            </Button>
          </div>
        </section>

        <section className="editor__pool" aria-labelledby="pool-title">
          <div className="row row--between">
            <h2 id="pool-title" className="editor__subtitle">
              Your collection
            </h2>
            {collection && <span className="muted num text-sm">{members.length} of {SLOTS} chosen</span>}
          </div>
          {collectionError !== null && (
            <ErrorBanner title="Your collection could not be loaded" error={collectionError} onRetry={reloadCollection} />
          )}
          {collection === undefined && collectionError === null ? (
            <div className="grid-cards">
              {Array.from({ length: 6 }, (_, i) => (
                <MonsterCardSkeleton key={i} />
              ))}
            </div>
          ) : collection && collection.length === 0 ? (
            <EmptyState title="No monsters yet" message="Choose your starters first: they will appear here." />
          ) : collection ? (
            <div className="grid-cards stagger">
              {collection.map((m, i) => {
                const pos = members.indexOf(m.owned_id);
                return (
                  <MonsterCard
                    key={m.owned_id}
                    monster={m}
                    level={m.level}
                    selected={pos >= 0}
                    onSelect={() => toggle(m.owned_id)}
                    footer={
                      pos >= 0 ? (
                        <span className="tag tag--accent">Slot {pos + 1}</span>
                      ) : (
                        <PoolStats m={m} />
                      )
                    }
                    style={{ '--i': i } as CSSProperties}
                  />
                );
              })}
            </div>
          ) : null}
        </section>
      </div>
    </div>
  );
}

function PoolStats({ m }: { m: OwnedMonster }) {
  return (
    <span className="pool-stats num" aria-label={`HP ${m.stats.hp}, attack ${m.stats.attack}, defense ${m.stats.defense}, speed ${m.stats.speed}`}>
      <span>HP {m.stats.hp}</span>
      <span>ATK {m.stats.attack}</span>
      <span>DEF {m.stats.defense}</span>
      <span>SPD {m.stats.speed}</span>
    </span>
  );
}

function Chevron({ dir }: { dir: 'up' | 'down' }) {
  return (
    <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.6" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <path d={dir === 'up' ? 'm6 15 6-6 6 6' : 'm6 9 6 6 6-6'} />
    </svg>
  );
}

function CheckIcon() {
  return (
    <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <path d="m5 12.5 4.5 4.5L19 7.5" />
    </svg>
  );
}
