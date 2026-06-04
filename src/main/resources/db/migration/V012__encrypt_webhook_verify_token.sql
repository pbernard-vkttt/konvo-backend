-- M-8: encryption-at-rest for channels.webhook_verify_token.
--
-- The token was the last per-channel secret still stored as plaintext. It now
-- rides the same AES-256-GCM path as app_secret/access_token via
-- EncryptedStringConverter; stored shape is `enc:v1:<base64(iv||ct||tag)>`.
--
-- Widen varchar(120) → text: the ciphertext + `enc:v1:` prefix is longer than
-- the plaintext token and a longer operator-chosen token must still fit.
-- Existing plaintext rows are read verbatim (the converter passes through any
-- value without the `enc:v1:` prefix) and re-encrypted by ChannelSecretBackfill
-- on startup — no data-walk needed here.

alter table channels alter column webhook_verify_token type text;
