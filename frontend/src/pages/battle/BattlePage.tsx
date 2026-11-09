import { useCallback, useEffect, useMemo, useRef, useState, type CSSProperties } from 'react';
import { Link, useLocation } from 'react-router-dom';
import { errorMessage, isApiError } from '../../api/client';
import { getCurrentBattle, sendBattleAction } from '../../api/encounters';
import type { Battle, BattleAction, BattleMember, Round } from '../../api/types';
import { BattleLogLine } from '../../components/BattleLog';
import { ElementBadge } from '../../components/Badges';
import { EmptyState } from '../../components/EmptyState';
import { ErrorBanner } from '../../components/ErrorBanner';
import { HpBar } from '../../components/HpBar';
import { Spinner } from '../../components/Spinner';
import { Sprite } from '../../components/Sprite';
import { useToast } from '../../components/toast-context';
import { useMediaQuery, usePrefersReducedMotion } from '../../hooks/useMediaQuery';
import { describeEvent, describeRound, namesFrom } from '../../lib/battleText';
import { useGameStatus } from '../../session/useGameStatus';
import { Arena } from './Arena';
import { eventsOf, revealStep, shownState } from './battleView';
import { ResultPanel } from './ResultPanel';
import './battle.css';

type ActionType = BattleAction['type'];

/**
 * /battle — the battle in progress (US-19 to US-22). The client sends only actions; every number comes from the
 * battle state returned by the server. Events of the last round are revealed one by one (≤ about 2 s, skippable)
 * and drive cosmetic animations only.
 */
export function BattlePage() {
  const location = useLocation();
  const toast = useToast();
  const { refresh } = useGameStatus();
  const reducedMotion = usePrefersReducedMotion();
  const compact = useMediaQuery('(max-width: 720px)');

  const initial = (location.state as { battle?: Battle } | null)?.battle;
  const [battle, setBattle] = useState<Battle | null | undefined>(initial);
  const [loadError, setLoadError] = useState<unknown>(null);
  const [prev, setPrev] = useState<Battle | null>(null);
  const [revealed, setRevealed] = useState(Number.POSITIVE_INFINITY);
  const [pending, setPending] = useState<ActionType | null>(null);
  const [switchMode, setSwitchMode] = useState(false);
  const [rounds, setRounds] = useState<Round[]>(() => (initial?.last_round ? [initial.last_round] : []));
  const [logOpen, setLogOpen] = useState(false);
  const [nonce, setNonce] = useState(0);
  const pendingRef = useRef(false);

  useEffect(() => {
    window.scrollTo({ top: 0 });
  }, []);

  // Load (or reload) the current battle from the server. The state passed by /encounters is shown meanwhile;
  // history state survives a page reload, so the server answer always wins unless an action was sent since.
  const actionsSent = useRef(0);
  useEffect(() => {
    const ctrl = new AbortController();
    const sentAtStart = actionsSent.current;
    getCurrentBattle(ctrl.signal).then(
      (b) => {
        if (actionsSent.current !== sentAtStart) return;
        setLoadError(null);
        setBattle(b);
        setPrev(null);
        setRevealed(Number.POSITIVE_INFINITY);
        setRounds((r) => {
          if (!b?.last_round) return r;
          return r.some((x) => x.turn === b.last_round!.turn) ? r : [...r, b.last_round];
        });
      },
      (e: unknown) => {
        if (e instanceof DOMException && e.name === 'AbortError') return;
        setLoadError(e);
      },
    );
    return () => ctrl.abort();
  }, [nonce]);

  const events = useMemo(() => eventsOf(battle), [battle]);
  const revealing = battle != null && prev !== null && revealed < events.length;

  // Staggered reveal of the round's events.
  useEffect(() => {
    if (!revealing) return;
    const id = window.setTimeout(() => setRevealed((r) => r + 1), revealed === 0 ? 60 : revealStep(events.length));
    return () => window.clearTimeout(id);
  }, [revealing, revealed, events.length]);

  const skip = useCallback(() => setRevealed(Number.POSITIVE_INFINITY), []);

  const act = useCallback(
    async (action: BattleAction) => {
      if (!battle || battle.status !== 'in_progress' || pendingRef.current) return;
      pendingRef.current = true;
      actionsSent.current += 1;
      setRevealed(Number.POSITIVE_INFINITY);
      setPending(action.type);
      setSwitchMode(false);
      try {
        const next = await sendBattleAction(action);
        setPrev(battle);
        setBattle(next);
        setRevealed(reducedMotion ? Number.POSITIVE_INFINITY : 0);
        if (next.last_round) setRounds((r) => [...r, next.last_round!]);
        refresh();
      } catch (e) {
        if (isApiError(e) && e.code === 'INVALID_ACTION') {
          toast.show({ title: 'That move is not possible', message: e.message, tone: 'warning' });
          setNonce((n) => n + 1);
        } else if (isApiError(e) && e.status === 404) {
          toast.show({ title: 'This battle is no longer active', message: 'It may have expired after a long pause.', tone: 'info' });
          setNonce((n) => n + 1);
          refresh();
        } else if (isApiError(e) && e.code === 'RESET_PENDING') {
          toast.show({ title: 'Your run is being closed', message: e.message, tone: 'warning' });
        } else {
          toast.show({ title: 'The action was not sent', message: errorMessage(e), tone: 'error' });
        }
      } finally {
        pendingRef.current = false;
        setPending(null);
      }
    },
    [battle, reducedMotion, refresh, toast],
  );

  const inProgress = battle?.status === 'in_progress';
  const shown = battle ? shownState(prev, battle, revealed) : null;
  const benchMembers = shown ? shown.team.filter((m) => m.position !== shown.activePosition) : [];
  const canSwitchTo = (m: BattleMember) => !m.knocked_out && m.hp > 0;
  const canAct = Boolean(inProgress && !pending);

  // Keyboard: A attack, S switch, F flee, 1-3 switch to that slot, Esc closes the switch choice.
  useEffect(() => {
    function onKey(e: KeyboardEvent) {
      if (e.ctrlKey || e.metaKey || e.altKey) return;
      const t = e.target as HTMLElement | null;
      if (t && (t.tagName === 'INPUT' || t.tagName === 'TEXTAREA' || t.tagName === 'SELECT' || t.isContentEditable)) return;
      if (document.querySelector('dialog[open]')) return;
      if (!battle || battle.status !== 'in_progress') return;
      const k = e.key.toLowerCase();
      if (k === 'a') void act({ type: 'attack' });
      else if (k === 'f') void act({ type: 'flee' });
      else if (k === 's') setSwitchMode((s) => !s);
      else if (k === 'escape') setSwitchMode(false);
      else if (['1', '2', '3'].includes(k)) {
        const slot = Number(k);
        const m = battle.team.find((x) => x.position === slot);
        if (m && !m.active && !m.knocked_out) void act({ type: 'switch', slot });
      } else return;
      e.preventDefault();
    }
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [act, battle]);

  // ---------------------------------------------------------------- empty / loading states
  if (battle === undefined) {
    if (loadError !== null) {
      return (
        <div className="page page--narrow">
          <ErrorBanner title="The battle could not be loaded" error={loadError} onRetry={() => setNonce((n) => n + 1)} />
        </div>
      );
    }
    return (
      <div className="page battle-page" aria-busy="true">
        <div className="skeleton" style={{ height: 56, borderRadius: 16 }} />
        <div className="skeleton" style={{ height: 'clamp(340px, 54vh, 520px)', borderRadius: 22 }} />
        <div className="skeleton" style={{ height: 120, borderRadius: 16 }} />
      </div>
    );
  }
  if (battle === null) {
    return (
      <div className="page page--narrow">
        <EmptyState
          title="No battle in progress"
          message="Pick an encounter and a team to start a new battle. Finished battles are kept in your history."
          art={<Sprite alt="" size={96} />}
          action={
            <>
              <Link to="/encounters" className="btn btn--primary">
                Find an encounter
              </Link>
              <Link to="/history" className="btn">
                Battle history
              </Link>
            </>
          }
        />
      </div>
    );
  }

  // ---------------------------------------------------------------- battle
  const names = namesFrom(battle.wild.name, battle.team);
  const currentLines = describeRound(battle.last_round, names);
  const visibleCount = Math.min(revealed, currentLines.length);
  const lastLine = revealing
    ? visibleCount > 0
      ? describeEvent(events[visibleCount - 1], names)
      : null
    : currentLines[currentLines.length - 1] ?? null;
  const fxEvent = revealing && revealed > 0 ? events[revealed - 1] : null;
  const fx = fxEvent ? { event: fxEvent, key: `${battle.turn}-${revealed}` } : null;
  const finished = battle.status === 'finished' && !revealing;
  const active = shown!.team.find((m) => m.position === shown!.activePosition);

  let caption: string;
  let captionDetail: string | undefined;
  if (pending) {
    caption = 'Resolving the round…';
  } else if (revealing && !lastLine) {
    caption = '…';
  } else if (revealing && lastLine) {
    caption = lastLine.text;
    captionDetail = lastLine.detail;
  } else if (finished) {
    caption = lastLine?.text ?? 'The battle is over.';
  } else if (prev === null && !battle.last_round) {
    caption = `A wild ${battle.wild.name} appears! What will ${active?.name ?? 'you'} do?`;
  } else {
    caption = `What will ${active?.name ?? 'you'} do?`;
    captionDetail = lastLine ? `Last: ${lastLine.text}` : undefined;
  }

  const outcome = battle.result?.outcome;
  const banner = finished && (outcome === 'victory' || outcome === 'defeat' || outcome === 'fled') ? outcome : null;
  const pastRounds = rounds.filter((r) => r.turn !== battle.last_round?.turn).slice().reverse();

  return (
    <div className="page battle-page">
      <header className="battle-top fade-in" data-element={battle.template.element}>
        <div className="battle-top__where">
          {battle.template.is_boss && <span className="boss-badge">Boss</span>}
          <div className="stack stack--sm" style={{ gap: 0 }}>
            <span className="eyebrow">Stage {battle.template.stage}</span>
            <h1 className="battle-top__title">{battle.template.name}</h1>
          </div>
        </div>
        <div className="battle-top__info">
          {battle.wild.ability && (
            <div className="wild-ability" data-element={battle.wild.element}>
              <span className="wild-ability__label">Wild ability</span>
              <span className="wild-ability__name">{battle.wild.ability.name}</span>
              <span className="wild-ability__desc">{battle.wild.ability.description}</span>
            </div>
          )}
          <div className="turn-counter" aria-label={`Turn ${battle.turn}`}>
            <span className="turn-counter__label">Turn</span>
            <span className="turn-counter__value num">{battle.turn}</span>
          </div>
        </div>
      </header>

      <div className="battle-layout">
        <div className="battle-main">
          <Arena
            battle={battle}
            shown={shown!}
            fx={fx}
            caption={caption}
            captionDetail={captionDetail}
            onSkip={skip}
            revealing={revealing}
            banner={banner}
            compact={compact}
            reducedMotion={reducedMotion}
          />

          {finished && battle.result ? (
            <ResultPanel battle={battle} result={battle.result} />
          ) : (
            <section className="deck panel" aria-label="Your move">
              <div className="deck__actions" role="group" aria-label="Actions">
                <ActionButton
                  kind="attack"
                  label="Attack"
                  hint={active ? `${active.name} strikes` : 'Strike'}
                  shortcut="A"
                  disabled={!canAct}
                  busy={pending === 'attack'}
                  onClick={() => void act({ type: 'attack' })}
                />
                <ActionButton
                  kind="switch"
                  label="Switch"
                  hint="Bring in another"
                  shortcut="S"
                  disabled={!canAct || !benchMembers.some(canSwitchTo)}
                  busy={pending === 'switch'}
                  pressed={switchMode}
                  onClick={() => setSwitchMode((s) => !s)}
                />
                <ActionButton
                  kind="flee"
                  label="Flee"
                  hint="Try to escape"
                  shortcut="F"
                  disabled={!canAct}
                  busy={pending === 'flee'}
                  onClick={() => void act({ type: 'flee' })}
                />
              </div>
              {pending && (
                <p className="deck__resolving" role="status">
                  <Spinner size={16} /> Resolving…
                </p>
              )}

              <div className={`bench${switchMode ? ' bench--choose' : ''}`}>
                <div className="bench__head">
                  <h2 className="bench__title">{switchMode ? 'Choose who steps in' : 'Bench'}</h2>
                  <span className="subtle text-sm">{switchMode ? 'Press 1–3 or click. Esc to cancel.' : 'Click a monster to switch it in.'}</span>
                </div>
                {benchMembers.length === 0 ? (
                  <p className="muted text-sm">No other monster in this team.</p>
                ) : (
                  <ul className="bench__list">
                    {benchMembers.map((m) => {
                      const ko = !canSwitchTo(m);
                      return (
                        <li key={m.owned_id}>
                          <button
                            type="button"
                            className={`bench-mon${ko ? ' bench-mon--ko' : ''}`}
                            data-element={m.element}
                            disabled={!canAct || ko}
                            onClick={() => void act({ type: 'switch', slot: m.position })}
                            aria-label={`Switch to ${m.name}, level ${m.level}, ${m.hp} of ${m.max_hp} HP${ko ? ', knocked out' : ''}`}
                          >
                            <span className="bench-mon__key num" aria-hidden="true">
                              {m.position}
                            </span>
                            <Sprite monsterId={m.monster_id} src={m.image_url} alt="" size={64} element={m.element} dim={ko} />
                            <span className="bench-mon__info">
                              <span className="bench-mon__name">
                                {m.name} <span className="bench-mon__lv num">Lv {m.level}</span>
                              </span>
                              {ko ? (
                                <span className="bench-mon__ko">Knocked out</span>
                              ) : (
                                <HpBar hp={m.hp} max={m.max_hp} label={`${m.name} HP`} size="sm" />
                              )}
                            </span>
                            <ElementBadge element={m.element} size="sm" iconOnly />
                          </button>
                        </li>
                      );
                    })}
                  </ul>
                )}
              </div>
            </section>
          )}
        </div>

        <aside className={`battle-log panel${logOpen ? ' is-open' : ''}`} aria-labelledby="log-title">
          <button
            type="button"
            className="battle-log__toggle"
            aria-expanded={logOpen}
            aria-controls="battle-log-body"
            onClick={() => setLogOpen((o) => !o)}
          >
            <span id="log-title" className="battle-log__title">
              Battle log
            </span>
            <span className="battle-log__chev" aria-hidden="true">
              ▾
            </span>
          </button>
          <div id="battle-log-body" className="battle-log__body" onClick={revealing ? skip : undefined}>
            {battle.last_round ? (
              <section className="blog-round" aria-label={`Turn ${battle.last_round.turn}`}>
                <h3 className="blog-round__turn">
                  <span>Turn</span> <span className="num">{battle.last_round.turn}</span>
                  {revealing && <span className="battle-log__live">live</span>}
                </h3>
                <ol className="blog-lines">
                  {currentLines.slice(0, revealing ? visibleCount : currentLines.length).map((l, i) => (
                    <BattleLogLine key={`${battle.turn}-${i}`} line={l} index={i} fresh={prev !== null} />
                  ))}
                </ol>
              </section>
            ) : (
              <p className="muted text-sm">The battle has just begun. Each round you choose an action; the log shows what happened.</p>
            )}
            {pastRounds.map((r) => (
              <section key={r.turn} className="blog-round blog-round--past" aria-label={`Turn ${r.turn}`}>
                <h3 className="blog-round__turn">
                  <span>Turn</span> <span className="num">{r.turn}</span>
                </h3>
                <ol className="blog-lines">
                  {describeRound(r, names).map((l, i) => (
                    <BattleLogLine key={i} line={l} />
                  ))}
                </ol>
              </section>
            ))}
          </div>
        </aside>
      </div>
    </div>
  );
}

function ActionButton({
  kind,
  label,
  hint,
  shortcut,
  disabled,
  busy,
  pressed,
  onClick,
}: {
  kind: 'attack' | 'switch' | 'flee';
  label: string;
  hint: string;
  shortcut: string;
  disabled: boolean;
  busy: boolean;
  pressed?: boolean;
  onClick: () => void;
}) {
  return (
    <button
      type="button"
      className={`action action--${kind}`}
      disabled={disabled}
      aria-pressed={pressed}
      aria-keyshortcuts={shortcut}
      aria-busy={busy || undefined}
      onClick={onClick}
      style={{ '--delay': kind === 'attack' ? 0 : kind === 'switch' ? 1 : 2 } as CSSProperties}
    >
      <span className="action__icon" aria-hidden="true">
        {busy ? <Spinner size={22} /> : <ActionIcon kind={kind} />}
      </span>
      <span className="action__text">
        <span className="action__label">{label}</span>
        <span className="action__hint">{hint}</span>
      </span>
      <kbd className="action__key">{shortcut}</kbd>
    </button>
  );
}

function ActionIcon({ kind }: { kind: 'attack' | 'switch' | 'flee' }) {
  const d =
    kind === 'attack'
      ? 'M14.5 17.5 3 6V3h3l11.5 11.5M13 19l6-6M16 16l4 4M19 21l2-2'
      : kind === 'switch'
        ? 'M4 8h13l-4-4M20 16H7l4 4'
        : 'M13 4h3a2 2 0 0 1 2 2v12a2 2 0 0 1-2 2h-3M8 8l-4 4 4 4M4 12h11';
  return (
    <svg width="26" height="26" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round">
      <path d={d} />
    </svg>
  );
}
