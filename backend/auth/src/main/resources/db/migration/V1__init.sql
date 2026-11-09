-- auth: accounts and refresh tokens (docs/plan/03-auth.md)

CREATE TABLE accounts (
    player_id      uuid PRIMARY KEY,
    username       varchar(20) NOT NULL,          -- normalised (lowercase); replaced in P2 (AUTH-8)
    password_hash  varchar(72) NOT NULL,          -- bcrypt, cost 12
    created_at     timestamptz NOT NULL DEFAULT now(),
    last_login     timestamptz
);
CREATE UNIQUE INDEX ux_accounts_username ON accounts (username);

-- Used from P2 (AUTH-7): only the SHA-256 of a refresh token is stored.
CREATE TABLE refresh_tokens (
    token_hash  char(64) PRIMARY KEY,
    player_id   uuid NOT NULL REFERENCES accounts (player_id),
    family_id   uuid NOT NULL,
    expires_at  timestamptz NOT NULL,
    revoked     boolean NOT NULL DEFAULT false,
    created_at  timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX ix_refresh_tokens_player ON refresh_tokens (player_id);
