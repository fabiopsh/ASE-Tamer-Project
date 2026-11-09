import './pagination.css';

interface PaginationProps {
  /** 0-based page, as in the API. */
  page: number;
  totalPages: number;
  onPageChange: (page: number) => void;
  disabled?: boolean;
  /** Optional "21–40 of 100" summary. */
  totalItems?: number;
  pageSize?: number;
}

/** Page numbers with ellipses: 1 … 4 5 6 … 10. Shown 1-based to the player. */
export function Pagination({ page, totalPages, onPageChange, disabled, totalItems, pageSize }: PaginationProps) {
  if (totalPages <= 1 && totalItems === undefined) return null;
  const pages = pageWindow(page, totalPages);
  const go = (p: number) => {
    if (!disabled && p >= 0 && p < totalPages && p !== page) onPageChange(p);
  };

  return (
    <nav className="pagination" aria-label="Pages">
      {totalItems !== undefined && pageSize !== undefined && (
        <span className="pagination__summary num">
          {totalItems === 0
            ? 'No results'
            : `${page * pageSize + 1}–${Math.min(totalItems, (page + 1) * pageSize)} of ${totalItems}`}
        </span>
      )}
      {totalPages > 1 && (
        <span className="pagination__pages">
          <button
            type="button"
            className="pagination__btn"
            onClick={() => go(page - 1)}
            disabled={disabled || page <= 0}
            aria-label="Previous page"
          >
            <Chevron dir="left" />
          </button>
          {pages.map((p, i) =>
            p === 'gap' ? (
              <span key={`gap-${i}`} className="pagination__gap" aria-hidden="true">
                …
              </span>
            ) : (
              <button
                key={p}
                type="button"
                className="pagination__btn num"
                aria-current={p === page ? 'page' : undefined}
                aria-label={`Page ${p + 1}`}
                onClick={() => go(p)}
                disabled={disabled}
              >
                {p + 1}
              </button>
            ),
          )}
          <button
            type="button"
            className="pagination__btn"
            onClick={() => go(page + 1)}
            disabled={disabled || page >= totalPages - 1}
            aria-label="Next page"
          >
            <Chevron dir="right" />
          </button>
        </span>
      )}
    </nav>
  );
}

function pageWindow(page: number, total: number): (number | 'gap')[] {
  if (total <= 7) return Array.from({ length: total }, (_, i) => i);
  const out: (number | 'gap')[] = [0];
  const start = Math.max(1, page - 1);
  const end = Math.min(total - 2, page + 1);
  if (start > 1) out.push('gap');
  for (let i = start; i <= end; i++) out.push(i);
  if (end < total - 2) out.push('gap');
  out.push(total - 1);
  return out;
}

function Chevron({ dir }: { dir: 'left' | 'right' }) {
  return (
    <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.4" aria-hidden="true">
      <path d={dir === 'left' ? 'm14.5 6-6 6 6 6' : 'm9.5 6 6 6-6 6'} strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  );
}
