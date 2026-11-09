import { Link, useParams } from 'react-router-dom';
import { getElements, getMonster } from '../../api/monsters';
import type { Element } from '../../api/types';
import { AbilityCard } from '../../components/AbilityCard';
import { ElementBadge } from '../../components/Badges';
import { ErrorBanner } from '../../components/ErrorBanner';
import { MonsterHero } from '../../components/MonsterHero';
import { StatTable } from '../../components/StatTable';
import { useAsync } from '../../hooks/useAsync';
import { BackLink, DetailSkeleton } from '../../components/BackLink';
import './catalog.css';

const MAX_ID = 100;

/** /catalog/:id — species detail with base stats, growth, ability and element matchups (US-11). */
export function CatalogDetailPage() {
  const { id } = useParams();
  const monsterId = Number.parseInt(id ?? '', 10);
  const valid = Number.isInteger(monsterId) && monsterId > 0;

  const monster = useAsync((signal) => getMonster(monsterId, signal), valid ? `monster:${monsterId}` : null);
  const elements = useAsync((signal) => getElements(signal), 'elements');
  const m = monster.data && monster.data.monster_id === monsterId ? monster.data : undefined;

  if (!valid || (monster.error !== null && !m)) {
    return (
      <div className="page page--narrow">
        <BackLink to="/catalog" label="Catalogue" />
        <ErrorBanner
          title="This monster could not be found"
          error={valid ? monster.error : undefined}
          message={valid ? undefined : 'The address does not point to a monster.'}
          onRetry={valid ? monster.reload : undefined}
        />
      </div>
    );
  }

  return (
    <div className="page">
      <div className="row row--between row--wrap">
        <BackLink to="/catalog" label="Catalogue" />
        <div className="cluster">
          {monsterId > 1 && (
            <Link className="btn btn--ghost btn--sm" to={`/catalog/${monsterId - 1}`} aria-label="Previous species">
              ‹ Prev
            </Link>
          )}
          {monsterId < MAX_ID && (
            <Link className="btn btn--ghost btn--sm" to={`/catalog/${monsterId + 1}`} aria-label="Next species">
              Next ›
            </Link>
          )}
        </div>
      </div>

      {!m ? (
        <DetailSkeleton />
      ) : (
        <>
          <MonsterHero monster={m}>
            <p className="muted text-lg">{m.description}</p>
            <AbilityCard ability={m.ability} />
          </MonsterHero>

          <div className="split split--even">
            <section className="panel">
              <h2 className="panel__title">Base stats</h2>
              <StatTable stats={m.base_stats} growth={m.growth} />
              <p className="subtle text-sm">Growth is added to each stat at every level after the first.</p>
            </section>
            <section className="panel">
              <h2 className="panel__title">Element matchups</h2>
              {elements.data ? (
                <Matchups element={m.element} strong={elements.data.strong_against[m.element]} weak={elements.data.weak_against[m.element]} />
              ) : elements.error !== null ? (
                <ErrorBanner error={elements.error} onRetry={elements.reload} compact />
              ) : (
                <div className="skeleton" style={{ height: 96 }} />
              )}
            </section>
          </div>
        </>
      )}
    </div>
  );
}

function Matchups({ element, strong, weak }: { element: Element; strong?: Element; weak?: Element }) {
  return (
    <dl className="matchups">
      <div className="matchups__row matchups__row--strong">
        <dt>Strong against</dt>
        <dd>{strong ? <ElementBadge element={strong} /> : '—'}</dd>
      </div>
      <div className="matchups__row matchups__row--weak">
        <dt>Weak against</dt>
        <dd>{weak ? <ElementBadge element={weak} /> : '—'}</dd>
      </div>
      <p className="subtle text-sm">
        A {element} monster deals more damage to the element it is strong against and less to the one it is weak against.
      </p>
    </dl>
  );
}
