-- AUTH-8: encrypt usernames at rest and track login backoff failures.
-- AUTH-7: add revoked_at timestamp and indexes for family revocation and expiry cleanup.

DROP INDEX IF EXISTS ux_accounts_username;
ALTER TABLE accounts
    DROP COLUMN username,
    ADD COLUMN username_hmac char(64) NOT NULL,
    ADD COLUMN username_enc  bytea NOT NULL;
CREATE UNIQUE INDEX ux_accounts_username_hmac ON accounts (username_hmac);

CREATE TABLE login_failures (
    username_hmac     char(64) PRIMARY KEY,
    failed_count      int NOT NULL,
    window_started_at timestamptz NOT NULL,
    locked_until      timestamptz
);

ALTER TABLE refresh_tokens
    ADD COLUMN revoked_at timestamptz;
CREATE INDEX ix_refresh_tokens_family ON refresh_tokens (family_id);
CREATE INDEX ix_refresh_tokens_expires ON refresh_tokens (expires_at);
