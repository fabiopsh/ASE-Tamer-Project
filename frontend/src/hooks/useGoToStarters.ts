import { useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import { useSession } from '../session/useSession';

/**
 * After a lost, abandoned or reset run the profile still says starters_chosen = true until it is re-read.
 * Re-read it, then go to /starters (the guard keeps the player there until new starters are chosen).
 */
export function useGoToStarters(): () => void {
  const { refreshPlayer } = useSession();
  const navigate = useNavigate();
  return useCallback(() => {
    void refreshPlayer().finally(() => navigate('/starters'));
  }, [refreshPlayer, navigate]);
}
