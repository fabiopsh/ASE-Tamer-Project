import { Link } from 'react-router-dom';
import './detail.css';

/** "‹ Catalogue" link above detail pages. */
export function BackLink({ to, label }: { to: string; label: string }) {
  return (
    <Link to={to} className="back-link">
      <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.4" aria-hidden="true">
        <path d="m14.5 6-6 6 6 6" strokeLinecap="round" strokeLinejoin="round" />
      </svg>
      {label}
    </Link>
  );
}

/** Skeleton of a MonsterHero + two panels. */
export function DetailSkeleton() {
  return (
    <div className="stack stack--lg" aria-hidden="true">
      <div className="panel detail-skeleton">
        <div className="skeleton" style={{ width: 200, height: 200, borderRadius: '50%' }} />
        <div className="stack" style={{ flex: 1 }}>
          <div className="skeleton" style={{ width: 60, height: 14 }} />
          <div className="skeleton" style={{ width: '55%', height: 40 }} />
          <div className="skeleton" style={{ width: 180, height: 26 }} />
          <div className="skeleton" style={{ width: '100%', height: 64 }} />
          <div className="skeleton" style={{ width: '100%', height: 96 }} />
        </div>
      </div>
      <div className="split split--even">
        <div className="panel">
          <div className="skeleton" style={{ height: 150 }} />
        </div>
        <div className="panel">
          <div className="skeleton" style={{ height: 150 }} />
        </div>
      </div>
    </div>
  );
}
