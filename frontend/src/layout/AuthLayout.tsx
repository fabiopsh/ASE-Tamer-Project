import type { ReactNode } from 'react';
import type { Element } from '../api/types';
import { Sprite } from '../components/Sprite';
import { Logo } from './Logo';
import './layout.css';

/** Legendary bosses shown around the hero orbit (ids from the stage table of plan 07). */
const ORBIT: { id: number; element: Element; angle: number; size: number }[] = [
  { id: 16, element: 'dream', angle: -90, size: 128 },
  { id: 62, element: 'echo', angle: -18, size: 96 },
  { id: 47, element: 'time', angle: 54, size: 128 },
  { id: 92, element: 'silence', angle: 126, size: 96 },
  { id: 74, element: 'gravity', angle: 198, size: 96 },
];

/** Two-column layout for login and registration: art on the left (desktop), form on the right. */
export function AuthLayout({ children }: { children: ReactNode }) {
  return (
    <div className="auth">
      <section className="auth__hero" aria-hidden="true">
        <div className="auth__hero-copy">
          <Logo size={44} />
          <h2 className="auth__hero-title">
            Tame the <span className="gradient-text">impossible</span>.
          </h2>
          <p className="auth__hero-text">
            Gather creatures born from dreams, echoes and lost time. Build a team of three, climb eight stages and face
            the bosses that guard them.
          </p>
        </div>
        <div className="auth__orbit">
          <span className="auth__ring" />
          <span className="auth__ring auth__ring--inner" />
          <Sprite monsterId={100} alt="" size={192} element="chaos" float />
          {ORBIT.map((o, i) => {
            const rad = (o.angle * Math.PI) / 180;
            return (
              <span
                key={o.id}
                className="auth__orbit-item"
                style={{
                  left: `calc(50% + ${Math.cos(rad) * 40}% - ${o.size / 2}px)`,
                  top: `calc(50% + ${Math.sin(rad) * 40}% - ${o.size / 2}px)`,
                  animationDelay: `${i * 0.4}s`,
                }}
              >
                <Sprite monsterId={o.id} alt="" size={o.size} element={o.element} float />
              </span>
            );
          })}
        </div>
      </section>
      <section className="auth__panel-wrap">
        <div className="auth__mobile-brand" aria-hidden="true">
          <Logo size={40} />
        </div>
        <div className="auth__panel">{children}</div>
      </section>
    </div>
  );
}
