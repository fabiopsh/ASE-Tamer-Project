import { Navigate, Outlet, useLocation } from 'react-router-dom';
import { ErrorBanner } from '../components/ErrorBanner';
import { FullScreenLoader } from '../components/Spinner';
import { GameStatusProvider } from './GameStatusContext';
import { useSession } from './useSession';

/**
 * Guard for every logged-in route (docs/plan/09-frontend.md, navigation guard):
 * no token → /login; profile loading → loader; starters not chosen → /starters;
 * starters chosen and on /starters → /.
 */
export function RequireAuth() {
  const { playerStatus, player, playerError, refreshPlayer, logout } = useSession();
  const location = useLocation();

  if (playerStatus === 'anonymous') {
    return <Navigate to="/login" replace state={{ from: location.pathname + location.search }} />;
  }
  if (playerStatus === 'loading') {
    return <FullScreenLoader label="Summoning your profile…" />;
  }
  if (playerStatus === 'error' || !player) {
    return (
      <div className="page page--narrow" style={{ paddingTop: '18vh' }}>
        <ErrorBanner
          title="We could not load your profile"
          error={playerError}
          onRetry={() => void refreshPlayer()}
          action={
            <button type="button" className="btn btn--ghost btn--sm" onClick={logout}>
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
  const { isAuthenticated } = useSession();
  const location = useLocation();
  if (isAuthenticated) {
    const from = (location.state as { from?: string } | null)?.from;
    return <Navigate to={from && from !== '/login' ? from : '/'} replace />;
  }
  return <Outlet />;
}
