-- M2: Tenants, users, memberships, and auth-supporting tables.
--
-- Naming:
--   * Tables are plural, snake_case.
--   * Surrogate PK is uuid; tenant scoping via tenant_id FK with on delete cascade.
--   * Timestamps are timestamptz (UTC) named created_at / updated_at.
--   * "soft delete" is intentionally NOT introduced here — milestones that
--     need it (customers/conversations) add a deleted_at column themselves.
--
-- Role values are kept as varchar instead of a Postgres enum so we can add
-- roles later via plain migrations without ALTER TYPE dances.

----------------------------------------------------------------
-- tenants — one row per workspace
----------------------------------------------------------------
create table tenants (
    id              uuid primary key default gen_random_uuid(),
    name            varchar(120) not null,
    slug            varchar(80)  not null,
    country_code    varchar(2)   not null default 'TT',
    plan            varchar(32)  not null default 'free',
    status          varchar(16)  not null default 'active',
    created_at      timestamptz  not null default now(),
    updated_at      timestamptz  not null default now(),
    constraint tenants_slug_unique unique (slug),
    constraint tenants_status_chk  check (status in ('active','suspended','cancelled'))
);

create index tenants_status_idx on tenants (status);

----------------------------------------------------------------
-- users — identity carriers. A user may belong to multiple tenants
-- via tenant_memberships, so users are NOT tenant-scoped directly.
----------------------------------------------------------------
create table users (
    id              uuid primary key default gen_random_uuid(),
    email           varchar(254) not null,
    email_verified  boolean      not null default false,
    full_name       varchar(160) not null,
    password_hash   varchar(120),     -- nullable: oauth-only users may have none
    status          varchar(16)  not null default 'active',
    last_login_at   timestamptz,
    created_at      timestamptz  not null default now(),
    updated_at      timestamptz  not null default now(),
    constraint users_email_unique unique (email),
    constraint users_status_chk   check (status in ('active','disabled'))
);

create index users_status_idx on users (status);

----------------------------------------------------------------
-- tenant_memberships — many-to-many users ↔ tenants with per-tenant role.
-- Roles: OWNER, ADMIN, AGENT, VIEWER, BILLING.
----------------------------------------------------------------
create table tenant_memberships (
    id              uuid primary key default gen_random_uuid(),
    tenant_id       uuid        not null references tenants(id) on delete cascade,
    user_id         uuid        not null references users(id)   on delete cascade,
    role            varchar(16) not null,
    status          varchar(16) not null default 'active',
    created_at      timestamptz not null default now(),
    updated_at      timestamptz not null default now(),
    constraint tenant_memberships_unique          unique (tenant_id, user_id),
    constraint tenant_memberships_role_chk        check (role   in ('OWNER','ADMIN','AGENT','VIEWER','BILLING')),
    constraint tenant_memberships_status_chk      check (status in ('active','disabled'))
);

create index tenant_memberships_user_idx   on tenant_memberships (user_id);
create index tenant_memberships_tenant_idx on tenant_memberships (tenant_id);

----------------------------------------------------------------
-- auth_identities — third-party identity links (Google, Microsoft, etc.).
-- Empty in M2 but the table lands now so OIDC in later milestones is a no-op migration.
----------------------------------------------------------------
create table auth_identities (
    id              uuid primary key default gen_random_uuid(),
    user_id         uuid        not null references users(id) on delete cascade,
    provider        varchar(32) not null,
    subject         varchar(255) not null,
    email           varchar(254),
    created_at      timestamptz not null default now(),
    updated_at      timestamptz not null default now(),
    constraint auth_identities_provider_subject_unique unique (provider, subject)
);

create index auth_identities_user_idx on auth_identities (user_id);

----------------------------------------------------------------
-- refresh_tokens — server-side refresh-token state. We store SHA-256 of the
-- token so a DB leak can't be used to mint sessions; the client only ever
-- sees the raw value via an HTTP-only Secure SameSite=Lax cookie.
----------------------------------------------------------------
create table refresh_tokens (
    id              uuid primary key default gen_random_uuid(),
    user_id         uuid        not null references users(id) on delete cascade,
    tenant_id       uuid        not null references tenants(id) on delete cascade,
    token_hash      varchar(128) not null,
    issued_at       timestamptz not null default now(),
    expires_at      timestamptz not null,
    revoked_at      timestamptz,
    replaced_by     uuid        references refresh_tokens(id),
    user_agent      varchar(255),
    ip_address      varchar(64),
    constraint refresh_tokens_token_hash_unique unique (token_hash)
);

create index refresh_tokens_user_idx       on refresh_tokens (user_id);
create index refresh_tokens_expires_at_idx on refresh_tokens (expires_at);

----------------------------------------------------------------
-- user_invitations — admin invites a teammate by email; they accept and
-- become a tenant_membership. Tokens are random opaque strings stored hashed.
----------------------------------------------------------------
create table user_invitations (
    id              uuid primary key default gen_random_uuid(),
    tenant_id       uuid        not null references tenants(id) on delete cascade,
    email           varchar(254) not null,
    role            varchar(16) not null,
    token_hash      varchar(128) not null,
    invited_by_user_id uuid     references users(id) on delete set null,
    expires_at      timestamptz not null,
    accepted_at     timestamptz,
    revoked_at      timestamptz,
    created_at      timestamptz not null default now(),
    updated_at      timestamptz not null default now(),
    constraint user_invitations_token_hash_unique unique (token_hash),
    constraint user_invitations_role_chk          check (role in ('OWNER','ADMIN','AGENT','VIEWER','BILLING'))
);

create unique index user_invitations_active_idx
    on user_invitations (tenant_id, lower(email))
    where accepted_at is null and revoked_at is null;

----------------------------------------------------------------
-- password_reset_tokens — short-lived single-use reset tokens, stored hashed.
----------------------------------------------------------------
create table password_reset_tokens (
    id              uuid primary key default gen_random_uuid(),
    user_id         uuid        not null references users(id) on delete cascade,
    token_hash      varchar(128) not null,
    expires_at      timestamptz not null,
    consumed_at     timestamptz,
    created_at      timestamptz not null default now(),
    constraint password_reset_tokens_token_hash_unique unique (token_hash)
);

create index password_reset_tokens_user_idx on password_reset_tokens (user_id);
