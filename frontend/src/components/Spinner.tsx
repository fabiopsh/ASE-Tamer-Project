import './feedback.css';

export function Spinner({ size = 20, label }: { size?: number; label?: string }) {
  return (
    <span className="spinner" style={{ width: size, height: size }} role={label ? 'status' : undefined}>
      {label && <span className="visually-hidden">{label}</span>}
    </span>
  );
}

/** Centered loader for whole-page waits (e.g. the profile at startup). */
export function FullScreenLoader({ label = 'Loading…' }: { label?: string }) {
  return (
    <div className="fullscreen-loader" role="status" aria-live="polite">
      <span className="fullscreen-loader__orb" aria-hidden="true" />
      <p className="fullscreen-loader__label">{label}</p>
    </div>
  );
}
