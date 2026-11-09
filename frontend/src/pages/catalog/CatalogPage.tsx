import type { CSSProperties } from 'react';
import { useSearchParams } from 'react-router-dom';
import { listMonsters } from '../../api/monsters';
import { ELEMENTS, RARITIES, type Element, type Rarity } from '../../api/types';
import { ElementIcon } from '../../components/ElementIcon';
import { EmptyState } from '../../components/EmptyState';
import { ErrorBanner } from '../../components/ErrorBanner';
import { FilterChips } from '../../components/FilterChips';
import { MonsterCard, MonsterCardSkeleton } from '../../components/MonsterCard';
import { PageHeader } from '../../components/PageHeader';
import { Pagination } from '../../components/Pagination';
import { useAsync } from '../../hooks/useAsync';
import { capitalize } from '../../lib/format';

const PAGE_SIZE = 20;

const ELEMENT_OPTIONS = ELEMENTS.map((e) => ({
  value: e,
  label: capitalize(e),
  element: e,
  icon: <ElementIcon element={e} size={16} />,
}));
const RARITY_OPTIONS = RARITIES.map((r) => ({ value: r, label: capitalize(r) }));

function asElement(v: string | null): Element | null {
  return v && (ELEMENTS as readonly string[]).includes(v) ? (v as Element) : null;
}
function asRarity(v: string | null): Rarity | null {
  return v && (RARITIES as readonly string[]).includes(v) ? (v as Rarity) : null;
}

/** /catalog — all species, filters by element and rarity, paginated (US-10). Filters live in the URL. */
export function CatalogPage() {
  const [params, setParams] = useSearchParams();
  const element = asElement(params.get('element'));
  const rarity = asRarity(params.get('rarity'));
  const page = Math.max(0, Number.parseInt(params.get('page') ?? '0', 10) || 0);

  const { data, error, loading, reload } = useAsync(
    (signal) => listMonsters({ element: element ?? undefined, rarity: rarity ?? undefined, page, size: PAGE_SIZE }, signal),
    `catalog:${element}:${rarity}:${page}`,
  );

  function update(next: { element?: Element | null; rarity?: Rarity | null; page?: number }) {
    const p = new URLSearchParams(params);
    const set = (k: string, v: string | null) => (v === null || v === '' ? p.delete(k) : p.set(k, v));
    if (next.element !== undefined) set('element', next.element);
    if (next.rarity !== undefined) set('rarity', next.rarity);
    const newPage = next.page ?? 0;
    set('page', newPage > 0 ? String(newPage) : null);
    setParams(p);
    if (next.page !== undefined) window.scrollTo({ top: 0, behavior: 'smooth' });
  }

  return (
    <div className="page">
      <PageHeader
        eyebrow="Bestiary"
        title="Monster catalogue"
        subtitle="Every species known to tamers: one hundred creatures across seven elements. Open one to see its stats and ability."
      />

      <section className="panel panel--tight stack" aria-label="Filters">
        <FilterChips
          label="Element"
          options={ELEMENT_OPTIONS}
          value={element}
          onChange={(v) => update({ element: v })}
        />
        <FilterChips label="Rarity" options={RARITY_OPTIONS} value={rarity} onChange={(v) => update({ rarity: v })} />
      </section>

      {error !== null && <ErrorBanner title="The catalogue could not be loaded" error={error} onRetry={reload} />}

      {data === undefined && error === null ? (
        <div className="grid-cards">
          {Array.from({ length: 10 }, (_, i) => (
            <MonsterCardSkeleton key={i} />
          ))}
        </div>
      ) : data && data.items.length === 0 ? (
        <EmptyState
          title="No monster matches"
          message="No species has this combination of element and rarity. Try another filter."
          action={
            <button type="button" className="btn btn--sm" onClick={() => update({ element: null, rarity: null })}>
              Clear filters
            </button>
          }
        />
      ) : data ? (
        <>
          <div className={`grid-cards stagger${loading ? ' is-loading' : ''}`} key={`${element}:${rarity}:${data.page}`}>
            {data.items.map((m, i) => (
              <MonsterCard key={m.monster_id} monster={m} to={`/catalog/${m.monster_id}`} style={{ '--i': i } as CSSProperties} />
            ))}
          </div>
          <Pagination
            page={data.page}
            totalPages={data.total_pages}
            totalItems={data.total_items}
            pageSize={data.size}
            onPageChange={(p) => update({ page: p })}
            disabled={loading}
          />
        </>
      ) : null}
    </div>
  );
}
