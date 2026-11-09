import { useState, type FormEvent } from 'react';
import { Link } from 'react-router-dom';
import { isApiError } from '../../api/client';
import { Button } from '../../components/Button';
import { ErrorBanner } from '../../components/ErrorBanner';
import { AuthLayout } from '../../layout/AuthLayout';
import { useSession } from '../../session/useSession';

/**
 * Input hints mirror RegisterRequest.java so the player gets instant feedback;
 * the backend remains the authority and its errors are shown as returned.
 */
function usernameProblem(u: string): string | null {
  if (u.length === 0) return null;
  if (u.length < 3 || u.length > 20) return 'Use 3 to 20 characters.';
  if (!/^[a-zA-Z0-9_]+$/.test(u)) return 'Only letters, digits and underscore.';
  return null;
}
function passwordProblem(p: string, u: string): string | null {
  if (p.length === 0) return null;
  if (p.length < 8) return 'At least 8 characters.';
  if (new TextEncoder().encode(p).length > 72) return 'At most 72 bytes.';
  if (p.toLowerCase() === u.toLowerCase()) return 'The password cannot be your username.';
  return null;
}

export function RegisterPage() {
  const { register } = useSession();
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [confirm, setConfirm] = useState('');
  const [touched, setTouched] = useState({ username: false, password: false, confirm: false });
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<unknown>(null);

  const u = username.trim();
  const uErr = usernameProblem(u);
  const pErr = passwordProblem(password, u);
  const cErr = confirm.length > 0 && confirm !== password ? 'The passwords do not match.' : null;
  const taken = isApiError(error) && error.code === 'USERNAME_TAKEN';
  const canSubmit = u.length > 0 && password.length > 0 && confirm.length > 0 && !uErr && !pErr && !cErr;

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    setTouched({ username: true, password: true, confirm: true });
    if (!canSubmit || busy) return;
    setBusy(true);
    setError(null);
    try {
      await register(u, password);
    } catch (err) {
      setError(err);
      setBusy(false);
    }
  }

  return (
    <AuthLayout>
      <div className="stack stack--sm">
        <p className="eyebrow">A new tamer arrives</p>
        <h1 className="auth__title">Create your account</h1>
        <p className="muted">Pick a name and you will choose your first three monsters right after.</p>
      </div>

      {error !== null && !taken && <ErrorBanner title="Could not create the account" error={error} compact />}

      <form className="panel stack" onSubmit={onSubmit} noValidate>
        <div className="field">
          <label className="field__label" htmlFor="reg-username">
            Username
          </label>
          <input
            id="reg-username"
            className="input"
            autoComplete="username"
            autoCapitalize="none"
            spellCheck={false}
            maxLength={20}
            value={username}
            aria-invalid={(touched.username && uErr !== null) || taken || undefined}
            aria-describedby="reg-username-hint"
            onChange={(e) => {
              setUsername(e.target.value);
              if (taken) setError(null);
            }}
            onBlur={() => setTouched((t) => ({ ...t, username: true }))}
          />
          {taken ? (
            <span id="reg-username-hint" className="field__error">
              This username is already taken.
            </span>
          ) : touched.username && uErr ? (
            <span id="reg-username-hint" className="field__error">
              {uErr}
            </span>
          ) : (
            <span id="reg-username-hint" className="field__hint">
              3–20 letters, digits or underscore.
            </span>
          )}
        </div>
        <div className="field">
          <label className="field__label" htmlFor="reg-password">
            Password
          </label>
          <input
            id="reg-password"
            className="input"
            type="password"
            autoComplete="new-password"
            value={password}
            aria-invalid={(touched.password && pErr !== null) || undefined}
            aria-describedby="reg-password-hint"
            onChange={(e) => setPassword(e.target.value)}
            onBlur={() => setTouched((t) => ({ ...t, password: true }))}
          />
          {touched.password && pErr ? (
            <span id="reg-password-hint" className="field__error">
              {pErr}
            </span>
          ) : (
            <span id="reg-password-hint" className="field__hint">
              8–72 characters, different from the username.
            </span>
          )}
        </div>
        <div className="field">
          <label className="field__label" htmlFor="reg-confirm">
            Confirm password
          </label>
          <input
            id="reg-confirm"
            className="input"
            type="password"
            autoComplete="new-password"
            value={confirm}
            aria-invalid={(touched.confirm && cErr !== null) || undefined}
            onChange={(e) => setConfirm(e.target.value)}
            onBlur={() => setTouched((t) => ({ ...t, confirm: true }))}
          />
          {touched.confirm && cErr && <span className="field__error">{cErr}</span>}
        </div>
        <Button type="submit" variant="primary" size="lg" block loading={busy} disabled={!canSubmit}>
          {busy ? 'Creating your account…' : 'Create account'}
        </Button>
      </form>

      <p className="auth__switch">
        Already a tamer? <Link to="/login">Log in</Link>
      </p>
    </AuthLayout>
  );
}
