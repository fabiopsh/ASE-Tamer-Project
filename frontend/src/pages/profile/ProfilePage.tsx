import { useState, type FormEvent } from 'react';
import { updateMe } from '../../api/players';
import { AVATARS, type Avatar } from '../../api/types';
import { AvatarBadge } from '../../components/AvatarIcon';
import { AVATAR_LABELS, isAvatar } from '../../components/avatars';
import { Button } from '../../components/Button';
import { ErrorBanner } from '../../components/ErrorBanner';
import { PageHeader } from '../../components/PageHeader';
import { useToast } from '../../components/toast-context';
import { formatDate } from '../../lib/format';
import { useSession } from '../../session/useSession';
import './profile.css';

function displayNameProblem(name: string): string | null {
  const trimmed = name.trim();
  if (trimmed.length < 3 || trimmed.length > 30) return 'Use 3 to 30 characters.';
  return null;
}

/** /profile — edit display name and avatar (US-04). */
export function ProfilePage() {
  const { player, updatePlayer } = useSession();
  const toast = useToast();

  const [displayName, setDisplayName] = useState(player?.display_name ?? '');
  const [avatar, setAvatar] = useState<Avatar | null>(
    isAvatar(player?.avatar) ? player.avatar : null,
  );
  const [touched, setTouched] = useState(false);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<unknown>(null);

  const trimmed = displayName.trim();
  const nameErr = displayNameProblem(displayName);
  const currentAvatar = isAvatar(player?.avatar) ? player.avatar : null;
  const dirty = trimmed !== (player?.display_name ?? '') || avatar !== currentAvatar;
  const canSave = !saving && dirty && nameErr === null;

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    setTouched(true);
    if (!canSave) return;
    setSaving(true);
    setError(null);
    try {
      const updated = await updateMe({ display_name: trimmed, avatar });
      updatePlayer(updated);
      setDisplayName(updated.display_name);
      setAvatar(isAvatar(updated.avatar) ? updated.avatar : null);
      toast.show({ title: 'Profile updated', tone: 'success' });
    } catch (err) {
      setError(err);
    } finally {
      setSaving(false);
    }
  }

  return (
    <div className="page page--narrow">
      <PageHeader
        eyebrow="Tamer identity"
        title="Profile"
        subtitle="Customize how your name and emblem appear in the archive."
      />

      {error !== null && (
        <ErrorBanner title="Your profile could not be updated" error={error} onDismiss={() => setError(null)} compact />
      )}

      <form className="panel stack stack--lg" onSubmit={onSubmit} noValidate>
        <div className="profile-preview">
          <span className="profile-preview__badge" aria-hidden="true">
            <AvatarBadge avatar={avatar} displayName={trimmed || 'Tamer'} size={34} />
          </span>
          <div className="stack stack--sm" style={{ gap: 2 }}>
            <strong className="profile-preview__name">{trimmed || 'Tamer'}</strong>
            <span className="subtle text-sm">
              {avatar ? AVATAR_LABELS[avatar] : 'No emblem'} · Joined {formatDate(player?.created_at)}
            </span>
          </div>
        </div>

        <div className="field">
          <label className="field__label" htmlFor="profile-display-name">
            Display name
          </label>
          <input
            id="profile-display-name"
            className="input"
            value={displayName}
            maxLength={30}
            aria-invalid={(touched && nameErr !== null) || undefined}
            aria-describedby="profile-display-name-hint"
            onChange={(e) => {
              setDisplayName(e.target.value);
              setError(null);
            }}
            onBlur={() => setTouched(true)}
          />
          {touched && nameErr ? (
            <span id="profile-display-name-hint" className="field__error">
              {nameErr}
            </span>
          ) : (
            <span id="profile-display-name-hint" className="field__hint">
              3–30 characters.
            </span>
          )}
        </div>

        <fieldset className="profile-avatars">
          <legend className="field__label">Emblem</legend>
          <div className="profile-avatar-grid" role="radiogroup" aria-label="Avatar emblem">
            <button
              type="button"
              role="radio"
              aria-checked={avatar === null}
              className={`profile-avatar-choice${avatar === null ? ' is-selected' : ''}`}
              onClick={() => {
                setAvatar(null);
                setError(null);
              }}
            >
              <span className="profile-avatar-choice__icon" aria-hidden="true">
                {(trimmed || 'T').replace(/^Tamer-/, '').charAt(0).toUpperCase() || 'T'}
              </span>
              <span className="profile-avatar-choice__label">None</span>
            </button>
            {AVATARS.map((key) => (
              <button
                key={key}
                type="button"
                role="radio"
                aria-checked={avatar === key}
                className={`profile-avatar-choice${avatar === key ? ' is-selected' : ''}`}
                onClick={() => {
                  setAvatar(key);
                  setError(null);
                }}
              >
                <span className="profile-avatar-choice__icon" aria-hidden="true">
                  <AvatarBadge avatar={key} displayName={trimmed || 'Tamer'} size={22} />
                </span>
                <span className="profile-avatar-choice__label">{AVATAR_LABELS[key]}</span>
              </button>
            ))}
          </div>
        </fieldset>

        <div className="row row--between row--wrap">
          <span className="subtle text-sm">
            {dirty ? 'You have unsaved changes.' : 'All changes saved.'}
          </span>
          <Button type="submit" variant="primary" loading={saving} disabled={!canSave}>
            Save profile
          </Button>
        </div>
      </form>
    </div>
  );
}
