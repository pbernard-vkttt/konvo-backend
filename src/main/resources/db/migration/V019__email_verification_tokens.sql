-- Email verification tokens — one-time links sent on owner registration
-- to confirm email ownership. Pattern mirrors password_reset_tokens.
CREATE TABLE email_verification_tokens (
    id            uuid         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id       uuid         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash    varchar(128) NOT NULL UNIQUE,
    expires_at    timestamptz  NOT NULL,
    consumed_at   timestamptz,
    created_at    timestamptz  NOT NULL DEFAULT now()
);

CREATE INDEX email_verification_tokens_user_idx ON email_verification_tokens(user_id);
