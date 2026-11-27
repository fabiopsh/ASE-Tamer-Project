import { useState, type FormEvent } from 'react';
import { Link, useParams } from 'react-router-dom';
import { getOwnedMonster, updateNickname } from '../../api/monsters';
import type { OwnedMonster } from '../../api/types';
import { AbilityCard } from '../../components/AbilityCard';
import { BackLink, DetailSkeleton } from '../../components/BackLink';
import { Button } from '../../components/Button';
import { ErrorBanner } from '../../components/ErrorBanner';
import { XpBar } from '../../components/HpBar';
import { MonsterHero } from '../../components/MonsterHero';
import { StatTable } from '../../components/StatTable';
import { useToast } from '../../components/toast-context';
import { useAsync } from '../../hooks/useAsync';
import { formatDateTime, SOURCE_LABELS, shortId } from '../../lib/format';

/** /collection/:ownedId — one owned instance: level, XP, effective stats, nickname, ability, origin (US-11, US-13). */
export function CollectionDetailPage() {
  const { ownedId = '' } = useParams();
  const { data, error, reload } = useAsync((signal) => getOwnedMonster(ownedId, signal), `owned:${ownedId}`);
  const [override, setOverride] = useState<OwnedMonster | null>(null);

  const fetched = data && data.owned_id === ownedId ? data : undefined;
  const m = override && override.owned_id === ownedId ? override : fetched;
  const backTo = m && m.status === 'archived' ? `/collection?run=${encodeURIComponent(m.run_id)}` : '/collection';

  if (error !== null && !m) {
    return (
      <div className="page page--narrow">
        <BackLink to="/collection" label="My collection" />
        <ErrorBanner title="This monster is not in your collection" error={error} onRetry={reload} />
      </div>
    );
  }

  return (
    <div className="page">
      <BackLink to={backTo} label="My collection" />
      {!m ? (
        <DetailSkeleton />
      ) : (
        <>
          <MonsterHero
            monster={m}
            level={m.level}
            tags={m.status === 'archived' ? <span className="tag">Archived</span> : <span className="tag tag--success">Active</span>}
          >
            <div className="panel panel--tight">
              <XpBar xp={m.xp} toNext={m.xp_to_next_level} level={m.level} />
            </div>
            <NicknameEditor key={`${m.owned_id}:${m.nickname ?? ''}`} monster={m} onUpdated={setOverride} />
            <AbilityCard ability={m.ability} />
          </MonsterHero>

          <div className="split split--even">
            <section className="panel">
              <h2 className="panel__title">Stats at level {m.level}</h2>
              <StatTable stats={m.stats} />
            </section>
            <section className="panel">
              <h2 className="panel__title">Origin</h2>
              <dl className="kv">
                <dt>How it joined</dt>
                <dd>{SOURCE_LABELS[m.source] ?? m.source}</dd>
                <dt>Joined on</dt>
                <dd>{formatDateTime(m.obtained_at)}</dd>
                <dt>Run</dt>
                <dd className="num">{shortId(m.run_id)}</dd>
                {m.encounter_id && (
                  <>
                    <dt>Encounter</dt>
                    <dd>
                      <Link to={`/history/${m.encounter_id}`}>View the battle record</Link>
                    </dd>
                  </>
                )}
                <dt>Species</dt>
                <dd>
                  <Link to={`/catalog/${m.monster_id}`}>See {m.name} in the catalogue</Link>
                </dd>
              </dl>
            </section>
          </div>
        </>
      )}
    </div>
  );
}

function NicknameEditor({ monster, onUpdated }: { monster: OwnedMonster; onUpdated: (next: OwnedMonster) => void }) {
  const toast = useToast();
  const current = monster.nickname?.trim() ?? '';
  const [draft, setDraft] = useState(current);
  const [saving, setSaving] = useState(false);
  const [saveError, setSaveError] = useState<unknown>(null);

  const trimmed = draft.trim();
  const changed = trimmed !== current;

  async function save(nextNickname: string | null) {
    setSaving(true);
    setSaveError(null);
    try {
      const updated = await updateNickname(monster.owned_id, nextNickname);
      onUpdated(updated);
      toast.show({
        title: nextNickname ? `Nickname set to "${nextNickname}"` : 'Nickname cleared',
        tone: 'success',
      });
    } catch (err) {
      setSaveError(err);
    } finally {
      setSaving(false);
    }
  }

  function onSubmit(e: FormEvent) {
    e.preventDefault();
    if (saving || !changed) return;
    void save(trimmed.length > 0 ? trimmed : null);
  }

  return (
    <form className="panel panel--tight stack stack--sm" onSubmit={onSubmit} aria-label="Monster nickname">
      <div className="field">
        <label className="field__label" htmlFor="monster-nickname">
          Nickname
        </label>
        <div className="row row--wrap">
          <input
            id="monster-nickname"
            className="input"
            style={{ flex: '1 1 180px' }}
            value={draft}
            maxLength={30}
            placeholder={monster.name}
            disabled={saving}
            onChange={(e) => {
              setDraft(e.target.value);
              setSaveError(null);
            }}
          />
          <Button type="submit" variant="primary" size="sm" loading={saving} disabled={!changed}>
            Save
          </Button>
          {current && (
            <Button type="button" variant="ghost" size="sm" disabled={saving} onClick={() => void save(null)}>
              Clear
            </Button>
          )}
        </div>
        <span className="field__hint">Up to 30 characters. Leave empty or clear to use the species name.</span>
      </div>
      {saveError !== null && (
        <ErrorBanner compact title="Could not update nickname" error={saveError} onDismiss={() => setSaveError(null)} />
      )}
    </form>
  );
}
