-- M8: AES-256-GCM encryption-at-rest for channel.app_secret + access_token.
--
-- Reverses M3 deviation #14 ("plain text now, encrypt in M8"). Stored shape
-- is `enc:v1:<base64(iv||ct||tag)>`; the EncryptedStringConverter handles
-- the round-trip. Anything without the `enc:v1:` prefix is read as legacy
-- plaintext and re-encrypted on next write — no data walk migration needed.
--
-- Widening app_secret from varchar(255) → text gives unlimited headroom; the
-- encrypted ciphertext for a 32-byte Meta app_secret runs ~87 chars but a
-- rotating provider could ship larger keys.

alter table channels alter column app_secret type text;

----------------------------------------------------------------
-- ShedLock table — claimed by UsageRollupJob (and any future @Scheduled
-- jobs) so a multi-pod deploy doesn't double-run. Schema is fixed by
-- shedlock-provider-jdbc-template; do not rename columns.
----------------------------------------------------------------
create table shedlock (
    name        varchar(64)  not null primary key,
    lock_until  timestamptz  not null,
    locked_at   timestamptz  not null,
    locked_by   varchar(255) not null
);

