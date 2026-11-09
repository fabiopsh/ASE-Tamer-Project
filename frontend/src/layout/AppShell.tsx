import { useEffect, useRef } from 'react';
import { Link, NavLink, Outlet, useLocation } from 'react-router-dom';
import { useSession } from '../session/useSession';
import { useGameStatus } from '../session/useGameStatus';
import { Logo } from './Logo';
import { NAV_ITEMS } from './nav-items';
import { NavIcon } from './NavIcon';
import { RunChip } from './RunChip';
import './layout.css';

/** Shell of every logged-in page: HUD top bar, navigation, resume-battle banner, page outlet. */
export function AppShell() {
  const { battle } = useGameStatus();
  const { pathname } = useLocation();
  const battleLive = battle !== null && battle.status === 'in_progress';
  const navRef = useRef<HTMLElement>(null);

  // On narrow screens the nav row scrolls: keep the current page's link visible.
  useEffect(() => {
    const nav = navRef.current;
    const active = nav?.querySelector<HTMLElement>('.topnav__link.active');
    if (nav && active) nav.scrollTo({ left: active.offsetLeft - nav.clientWidth / 2 + active.clientWidth / 2 });
  }, [pathname]);

  return (
    <div className="shell">
      <a href="#main" className="skip-link">
        Skip to content
      </a>
      <header className="topbar">
        <div className="topbar__inner">
          <Link to="/" className="topbar__brand" aria-label="Monster Tamers, home">
            <Logo />
          </Link>
          <nav className="topnav" aria-label="Main" ref={navRef}>
            {NAV_ITEMS.map((item) => (
              <NavLink key={item.to} to={item.to} end={item.end} className="topnav__link">
                <NavIcon name={item.icon} size={18} />
                <span>{item.label}</span>
                {item.to === '/battle' && battleLive && <span className="topnav__live" aria-label="battle in progress" />}
              </NavLink>
            ))}
          </nav>
          <HudRight />
        </div>
      </header>

      {battleLive && pathname !== '/battle' && (
        <div className="resume-banner" role="status">
          <span className="resume-banner__pulse" aria-hidden="true" />
          <span className="resume-banner__text">
            A battle against <strong>{battle.wild.name}</strong> is still in progress.
          </span>
          <Link to="/battle" className="btn btn--primary btn--sm">
            Resume battle
          </Link>
        </div>
      )}

      <main id="main" className="shell__main" key={pathname}>
        <Outlet />
      </main>
    </div>
  );
}

/** Minimal shell for /starters: brand and player only (the rest is locked until starters are chosen). */
export function MinimalShell() {
  return (
    <div className="shell">
      <header className="topbar">
        <div className="topbar__inner">
          <span className="topbar__brand">
            <Logo />
          </span>
          <span className="topbar__spacer" />
          <HudRight hideRun />
        </div>
      </header>
      <main id="main" className="shell__main">
        <Outlet />
      </main>
    </div>
  );
}

function HudRight({ hideRun = false }: { hideRun?: boolean }) {
  const { player, logout } = useSession();
  const name = player?.display_name ?? 'Tamer';
  return (
    <div className="hud">
      {!hideRun && <RunChip />}
      <span className="hud__player" title={name}>
        <span className="hud__avatar" aria-hidden="true">
          {name.replace(/^Tamer-/, '').charAt(0).toUpperCase() || 'T'}
        </span>
        <span className="hud__name">{name}</span>
      </span>
      <button type="button" className="hud__logout" onClick={logout} aria-label="Log out" title="Log out">
        <NavIcon name="logout" size={20} />
      </button>
    </div>
  );
}
