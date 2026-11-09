import { Link } from 'react-router-dom';
import { EmptyState } from '../components/EmptyState';
import { Sprite } from '../components/Sprite';

export function NotFoundPage() {
  return (
    <div className="page page--narrow" style={{ paddingTop: '10vh' }}>
      <EmptyState
        art={<Sprite monsterId={97} alt="" size={128} element="chaos" float />}
        title="Lost in the void"
        message="This place does not exist, or it drifted away. Let us take you back to camp."
        action={
          <Link to="/" className="btn btn--primary">
            Back home
          </Link>
        }
      />
    </div>
  );
}
