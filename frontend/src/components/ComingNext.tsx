import { Link } from 'react-router-dom';
import { NavIcon, type NavIconName } from '../layout/NavIcon';
import { PageHeader } from './PageHeader';
import './feedback.css';

interface ComingNextProps {
  eyebrow: string;
  title: string;
  description: string;
  icon: NavIconName;
  /** Endpoints the finished page will use (from docs/plan/09-frontend.md). */
  endpoints: string[];
}

/** Placeholder for pages that FE-2 will build. Each route has its own page file using it. */
export function ComingNext({ eyebrow, title, description, icon, endpoints }: ComingNextProps) {
  return (
    <div className="page page--narrow">
      <PageHeader eyebrow={eyebrow} title={title} subtitle={description} />
      <section className="panel coming-next fade-in">
        <span className="coming-next__icon" aria-hidden="true">
          <NavIcon name={icon} size={40} />
        </span>
        <div className="stack stack--sm">
          <span className="tag tag--accent">Coming next</span>
          <h2>This part of the game is being built</h2>
          <p className="muted">
            It opens as soon as the matching game service is online. Meanwhile, explore your collection or browse the
            catalogue.
          </p>
          <details className="coming-next__dev">
            <summary>Game services this page will use</summary>
            <ul className="coming-next__endpoints">
              {endpoints.map((e) => (
                <li key={e}>
                  <code>{e}</code>
                </li>
              ))}
            </ul>
          </details>
          <div className="cluster">
            <Link to="/collection" className="btn btn--primary btn--sm">
              My collection
            </Link>
            <Link to="/" className="btn btn--ghost btn--sm">
              Back home
            </Link>
          </div>
        </div>
      </section>
    </div>
  );
}
