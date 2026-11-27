import { Navigate, Outlet, useLocation } from 'react-router-dom';
import { ErrorBanner } from '../components/ErrorBanner';
import { FullScreenLoader } from '../components/Spinner';
import { GameStatusProvider } from './GameStatusContext';
import { useSession } from './useSession';

/** Rejects open redirects (protocol-relative `//...`, non-relative URLs) and auth routes (`/login`, `/register`). */
function safeRedirectTarget(from: unknown): string {
  if (typeof from !== 'string') return '/';
  if (!from.startsWith('/') || from.startsWith('//')) return '/';
  const pathOnly = from.split(/[?#]/, 1)[0];
  if (pathOnly === '/login' || pathOnly === '/register') return '/';
  return from;
}

/**
 * Guard for every logged-in route (docs/plan/09-frontend.md, navigation guard):
 * restoring/loading → loader; no token → /login; starters not chosen → /starters;
 * starters chosen and on /starters → /.
 */
export function RequireAuth() {
  const { playerStatus, player, playerError, refreshPlayer, logout } = useSession();
  const location = useLocation();

  if (playerStatus === 'loading') {
    return <FullScreenLoader label="Summoning your profile…" />;
  }
  if (playerStatus === 'anonymous') {
    return <Navigate to="/login" replace state={{ from: location.pathname + location.search }} />;
  }
  if (playerStatus === 'error' || !player) {
    return (
      <div className="page page--narrow" style={{ paddingTop: '18vh' }}>
        <ErrorBanner
          title="We could not load your profile"
          error={playerError}
          onRetry={() => void refreshPlayer()}
          action={
            <button type="button" className="btn btn--ghost btn--sm" onClick={() => void logout()}>
              Log out
            </button>
          }
        />
      </div>
    );
  }

  const onStarters = location.pathname === '/starters';
  if (!player.starters_chosen && !onStarters) return <Navigate to="/starters" replace />;
  if (player.starters_chosen && onStarters) return <Navigate to="/" replace />;

  return (
    <GameStatusProvider>
      <Outlet />
    </GameStatusProvider>
  );
}

/** Login and register: a logged-in player goes back to where they came from (or home). */
export function GuestOnly() {
  const { isAuthenticated, restoring } = useSession();
  const location = useLocation();
  if (restoring) {
    return <FullScreenLoader label="Restoring your session…" />;
  }
  if (isAuthenticated) {
    const from = (location.state as { from?: unknown } | null)?.from;
    return <Navigate to={safeRedirectTarget(from)} replace />;
  }
  return <Outlet />;
}
