import type { ReactNode } from 'react';
import './feedback.css';

interface PageHeaderProps {
  /** Small uppercase line above the title. */
  eyebrow?: ReactNode;
  title: ReactNode;
  subtitle?: ReactNode;
  /** Buttons on the right (below on phones). */
  actions?: ReactNode;
  /** Content above the eyebrow, e.g. a back link. */
  before?: ReactNode;
}

/** Title block used at the top of every page. */
export function PageHeader({ eyebrow, title, subtitle, actions, before }: PageHeaderProps) {
  return (
    <header className="page-header fade-in">
      {before}
      <div className="page-header__row">
        <div className="page-header__text">
          {eyebrow && <p className="eyebrow">{eyebrow}</p>}
          <h1 className="page-header__title">{title}</h1>
          {subtitle && <p className="page-header__subtitle">{subtitle}</p>}
        </div>
        {actions && <div className="page-header__actions">{actions}</div>}
      </div>
    </header>
  );
}
