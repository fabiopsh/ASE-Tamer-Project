import { useEffect, useState, type FormEvent } from 'react';
import { Link } from 'react-router-dom';
import { isApiError } from '../../api/client';
import { Button } from '../../components/Button';
import { ErrorBanner } from '../../components/ErrorBanner';
import { AuthLayout } from '../../layout/AuthLayout';
import { clearSessionExpired, wasSessionExpired } from '../../session/storage';
import { useSession } from '../../session/useSession';

export function LoginPage() {
  const { login } = useSession();
  const [expired] = useState(wasSessionExpired);
  useEffect(clearSessionExpired, []);
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<unknown>(null);

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    if (busy) return;
    setBusy(true);
    setError(null);
    try {
      await login(username.trim(), password);
      // GuestOnly redirects once the session exists.
    } catch (err) {
      setError(err);
      setBusy(false);
    }
  }

  const wrongCredentials = isApiError(error) && error.code === 'invalid_grant';

  return (
    <AuthLayout>
      <div className="stack stack--sm">
        <p className="eyebrow">Welcome back</p>
        <h1 className="auth__title">Log in to your journey</h1>
        <p className="muted">Your monsters are waiting where you left them.</p>
      </div>

      {expired && !error && (
        <ErrorBanner tone="info" title="Session ended" message="Your session has expired. Please log in again." compact />
      )}
      {error !== null && (
        <ErrorBanner
          title={wrongCredentials ? 'Wrong username or password' : 'Could not log in'}
          error={wrongCredentials ? undefined : error}
          message={wrongCredentials ? 'Check both fields and try again.' : undefined}
          compact
        />
      )}

      <form className="panel stack" onSubmit={onSubmit} noValidate>
        <div className="field">
          <label className="field__label" htmlFor="login-username">
            Username
          </label>
          <input
            id="login-username"
            className="input"
            name="username"
            autoComplete="username"
            autoCapitalize="none"
            spellCheck={false}
            value={username}
            onChange={(e) => setUsername(e.target.value)}
            required
          />
        </div>
        <div className="field">
          <label className="field__label" htmlFor="login-password">
            Password
          </label>
          <input
            id="login-password"
            className="input"
            type="password"
            name="password"
            autoComplete="current-password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            required
          />
        </div>
        <Button type="submit" variant="primary" size="lg" block loading={busy} disabled={!username.trim() || !password}>
          {busy ? 'Entering…' : 'Log in'}
        </Button>
      </form>

      <p className="auth__switch">
        New tamer? <Link to="/register">Create an account</Link>
      </p>
    </AuthLayout>
  );
}
