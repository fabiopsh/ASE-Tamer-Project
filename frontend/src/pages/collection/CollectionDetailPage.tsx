import { Link, useParams } from 'react-router-dom';
import { getOwnedMonster } from '../../api/monsters';
import { AbilityCard } from '../../components/AbilityCard';
import { BackLink, DetailSkeleton } from '../../components/BackLink';
import { ErrorBanner } from '../../components/ErrorBanner';
import { XpBar } from '../../components/HpBar';
import { MonsterHero } from '../../components/MonsterHero';
import { StatTable } from '../../components/StatTable';
import { useAsync } from '../../hooks/useAsync';
import { formatDateTime, SOURCE_LABELS, shortId } from '../../lib/format';

/** /collection/:ownedId — one owned instance: level, XP, effective stats, ability, origin (US-11). */
export function CollectionDetailPage() {
  const { ownedId = '' } = useParams();
  const { data, error, reload } = useAsync((signal) => getOwnedMonster(ownedId, signal), `owned:${ownedId}`);
  const m = data && data.owned_id === ownedId ? data : undefined;
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
