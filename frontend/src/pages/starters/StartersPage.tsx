import { useState, type CSSProperties } from 'react';
import { isApiError } from '../../api/client';
import { getMonster, getMonstersByIds } from '../../api/monsters';
import { chooseStarters, getStarterPool } from '../../api/players';
import type { MonsterSummary } from '../../api/types';
import { AbilityCard } from '../../components/AbilityCard';
import { ElementBadge, RarityBadge } from '../../components/Badges';
import { Button } from '../../components/Button';
import { ErrorBanner } from '../../components/ErrorBanner';
import { MonsterCard, MonsterCardSkeleton } from '../../components/MonsterCard';
import { PageHeader } from '../../components/PageHeader';
import { Sprite } from '../../components/Sprite';
import { StatTable } from '../../components/StatTable';
import { useToast } from '../../components/toast-context';
import { useAsync } from '../../hooks/useAsync';
import { dexNumber } from '../../lib/format';
import { useSession } from '../../session/useSession';
import './starters.css';

const PICK = 3;

/** /starters — pick exactly 3 of the 10 monsters of the starter pool (US-06). */
export function StartersPage() {
  const { refreshPlayer, player } = useSession();
  const toast = useToast();

  const pool = useAsync(async (signal) => {
    const p = await getStarterPool(signal);
    const monsters = await getMonstersByIds(p.pool, signal);
    // Keep the pool order returned by the players service.
    const byId = new Map(monsters.map((m) => [m.monster_id, m]));
    return p.pool.map((id) => byId.get(id)).filter((m): m is MonsterSummary => m !== undefined);
  }, `starters:${player?.current_run_id ?? ''}`);

  const [selected, setSelected] = useState<number[]>([]);
  const [focusId, setFocusId] = useState<number | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [submitError, setSubmitError] = useState<unknown>(null);
  const [nudge, setNudge] = useState(0);

  const monsters = pool.data ?? [];
  const inspectedId = focusId ?? monsters[0]?.monster_id ?? null;
  const detail = useAsync((signal) => getMonster(inspectedId as number, signal), inspectedId === null ? null : `monster:${inspectedId}`);
  const inspected = detail.data && detail.data.monster_id === inspectedId ? detail.data : undefined;

  const full = selected.length >= PICK;

  function toggle(id: number) {
    setFocusId(id);
    setSubmitError(null);
    if (selected.includes(id)) setSelected(selected.filter((x) => x !== id));
    else if (selected.length >= PICK) setNudge((n) => n + 1);
    else setSelected([...selected, id]);
  }

  async function confirm() {
    if (selected.length !== PICK || submitting) return;
    setSubmitting(true);
    setSubmitError(null);
    try {
      await chooseStarters(selected);
      toast.show({ tone: 'success', title: 'Your journey begins!', message: 'Your three companions joined your collection.' });
      await refreshPlayer(); // the guard then leaves /starters
    } catch (e) {
      if (isApiError(e) && e.code === 'STARTERS_ALREADY_CHOSEN') {
        await refreshPlayer();
        return;
      }
      setSubmitError(e);
      setSubmitting(false);
    }
  }

  const selectedMonsters = selected
    .map((id) => monsters.find((m) => m.monster_id === id))
    .filter((m): m is MonsterSummary => m !== undefined);

  return (
    <div className="page starters">
      <PageHeader
        eyebrow="A new run begins"
        title="Choose your first companions"
        subtitle="Ten wild monsters answered your call. Pick three to start your run with: they will fight for you in every encounter of stage 1."
      />

      {pool.error !== null && (
        <ErrorBanner title="The starter pool could not be loaded" error={pool.error} onRetry={pool.reload} />
      )}

      <div className="starters__layout">
        <section aria-labelledby="pool-title" className="stack">
          <h2 id="pool-title" className="visually-hidden">
            Starter pool
          </h2>
          <div className="grid-cards grid-cards--lg stagger">
            {pool.data === undefined && pool.error === null
              ? Array.from({ length: 10 }, (_, i) => <MonsterCardSkeleton key={i} size="lg" />)
              : monsters.map((m, i) => {
                  const isSel = selected.includes(m.monster_id);
                  return (
                    <MonsterCard
                      key={m.monster_id}
                      monster={m}
                      size="lg"
                      selected={isSel}
                      onSelect={() => toggle(m.monster_id)}
                      className={!isSel && full ? 'mcard--disabled' : undefined}
                      corner={
                        isSel ? undefined : (
                          <span className="starters__pick-hint" aria-hidden="true">
                            +
                          </span>
                        )
                      }
                      style={{ '--i': i } as CSSProperties}
                    />
                  );
                })}
          </div>
        </section>

        <aside className="panel starters__inspect" aria-live="polite" aria-label="Monster details">
          {inspected ? (
            <div className="stack fade-in" key={inspected.monster_id} data-element={inspected.element}>
              <div className="starters__inspect-hero">
                <Sprite src={inspected.image_url} alt={inspected.name} size={160} element={inspected.element} float />
              </div>
              <div className="stack stack--sm">
                <p className="eyebrow num">{dexNumber(inspected.monster_id)}</p>
                <h2 className="starters__inspect-name">{inspected.name}</h2>
                <div className="cluster">
                  <ElementBadge element={inspected.element} />
                  <RarityBadge rarity={inspected.rarity} />
                </div>
              </div>
              <p className="muted">{inspected.description}</p>
              <StatTable stats={inspected.base_stats} caption="Base stats" compact />
              <AbilityCard ability={inspected.ability} />
            </div>
          ) : detail.error !== null ? (
            <ErrorBanner error={detail.error} onRetry={detail.reload} compact />
          ) : (
            <div className="stack" aria-hidden="true">
              <div className="skeleton" style={{ width: 160, height: 160, borderRadius: '50%', margin: '0 auto' }} />
              <div className="skeleton" style={{ width: '50%', height: 26 }} />
              <div className="skeleton" style={{ width: '100%', height: 60 }} />
              <div className="skeleton" style={{ width: '100%', height: 110 }} />
            </div>
          )}
        </aside>
      </div>

      <div className="starters__bar" role="region" aria-label="Your selection">
        <div className="starters__bar-inner">
          <div className="starters__slots">
            {Array.from({ length: PICK }, (_, i) => {
              const m = selectedMonsters[i];
              return m ? (
                <button
                  key={m.monster_id}
                  type="button"
                  className="starters__slot starters__slot--filled"
                  data-element={m.element}
                  onClick={() => toggle(m.monster_id)}
                  aria-label={`Remove ${m.name}`}
                  title={`Remove ${m.name}`}
                >
                  <Sprite src={m.image_url} alt="" size={64} />
                </button>
              ) : (
                <span key={`empty-${i}`} className="starters__slot" aria-hidden="true">
                  {i + 1}
                </span>
              );
            })}
          </div>
          <div className="starters__count" key={nudge} data-nudge={nudge > 0 || undefined}>
            <span className="starters__count-value num" data-full={full || undefined}>
              {selected.length}/{PICK}
            </span>
            <span className="starters__count-label">
              {full ? 'Ready to go!' : `Pick ${PICK - selected.length} more`}
            </span>
          </div>
          <Button
            variant="primary"
            size="lg"
            className="starters__confirm"
            disabled={!full}
            loading={submitting}
            onClick={() => void confirm()}
          >
            {submitting ? 'Summoning…' : 'Begin your journey'}
          </Button>
        </div>
        {submitError !== null && (
          <div className="starters__bar-error">
            <ErrorBanner
              title={
                isApiError(submitError) && submitError.status === 503
                  ? 'A game service is busy — your choice is safe to retry'
                  : 'Your choice was not accepted'
              }
              error={submitError}
              onRetry={() => void confirm()}
              onDismiss={() => setSubmitError(null)}
              compact
            />
          </div>
        )}
      </div>
    </div>
  );
}
