-- M3: WhatsApp channel binding plus the minimal conversation/message persistence
-- the webhook ingest path needs. The rich inbox surface (assignments, snooze,
-- bulk actions, attachments) lands in M4 — keep this migration deliberately
-- small so M4 can evolve schema freely.
--
-- Encryption-at-rest for channel.access_token is deferred to M6 (per plan §3.4);
-- M3 stores the token as-is and notes the deviation. Rotate via Settings → Channels.

----------------------------------------------------------------
-- channels — per-tenant binding to an external messaging provider.
-- A tenant can connect at most one WhatsApp channel for now; the schema
-- doesn't enforce it (a future expansion to multi-number is a one-line
-- constraint drop).
----------------------------------------------------------------
create table channels (
    id                     uuid primary key default gen_random_uuid(),
    tenant_id              uuid        not null references tenants(id) on delete cascade,
    provider               varchar(16) not null,
    display_name           varchar(120) not null,
    status                 varchar(16) not null default 'connected',
    phone_number           varchar(32),
    phone_number_id        varchar(64),
    waba_id                varchar(64),
    app_secret             varchar(255),
    access_token           text,
    webhook_verify_token   varchar(120) not null,
    created_at             timestamptz not null default now(),
    updated_at             timestamptz not null default now(),
    constraint channels_provider_chk check (provider in ('whatsapp_meta','whatsapp_stub')),
    constraint channels_status_chk   check (status   in ('connected','disconnected','error'))
);

create index channels_tenant_idx          on channels (tenant_id);
create unique index channels_phone_number_id_unique
    on channels (phone_number_id)
    where phone_number_id is not null;

----------------------------------------------------------------
-- customers — minimal contact record keyed by the channel-side identifier.
-- M4 expands with tags, custom fields, profile photo, etc.
----------------------------------------------------------------
create table customers (
    id              uuid primary key default gen_random_uuid(),
    tenant_id       uuid        not null references tenants(id) on delete cascade,
    phone           varchar(32) not null,
    display_name    varchar(160),
    profile_name    varchar(160),
    locale          varchar(16),
    created_at      timestamptz not null default now(),
    updated_at      timestamptz not null default now(),
    constraint customers_phone_per_tenant_unique unique (tenant_id, phone)
);

create index customers_tenant_idx on customers (tenant_id);

----------------------------------------------------------------
-- conversations — one thread per (channel, customer). Statuses match the
-- M1 design system's inbox states.
----------------------------------------------------------------
create table conversations (
    id              uuid primary key default gen_random_uuid(),
    tenant_id       uuid        not null references tenants(id) on delete cascade,
    channel_id      uuid        not null references channels(id) on delete cascade,
    customer_id     uuid        not null references customers(id) on delete cascade,
    status          varchar(16) not null default 'open',
    last_message_at timestamptz,
    last_message_preview varchar(280),
    created_at      timestamptz not null default now(),
    updated_at      timestamptz not null default now(),
    constraint conversations_status_chk check (status in ('open','snoozed','closed')),
    constraint conversations_channel_customer_unique unique (channel_id, customer_id)
);

create index conversations_tenant_idx       on conversations (tenant_id);
create index conversations_last_message_idx on conversations (tenant_id, last_message_at desc);
create index conversations_status_idx       on conversations (tenant_id, status);

----------------------------------------------------------------
-- messages — every inbound and outbound payload, persisted in order.
-- wa_message_id is the provider-side identifier (Meta's wamid.* for now).
-- raw_payload keeps the original JSON for debugging — small enough at this
-- volume that compressing isn't worth the complexity.
----------------------------------------------------------------
create table messages (
    id              uuid primary key default gen_random_uuid(),
    tenant_id       uuid        not null references tenants(id) on delete cascade,
    conversation_id uuid        not null references conversations(id) on delete cascade,
    direction       varchar(8)  not null,
    content_type    varchar(24) not null default 'text',
    body            text,
    wa_message_id   varchar(128),
    status          varchar(16) not null default 'received',
    sent_at         timestamptz not null default now(),
    delivered_at    timestamptz,
    read_at         timestamptz,
    error_code      varchar(64),
    error_message   text,
    raw_payload     jsonb,
    created_at      timestamptz not null default now(),
    updated_at      timestamptz not null default now(),
    constraint messages_direction_chk check (direction in ('inbound','outbound')),
    constraint messages_status_chk    check (status    in ('received','queued','sent','delivered','read','failed'))
);

create index messages_conversation_idx on messages (conversation_id, sent_at);
create index messages_tenant_idx       on messages (tenant_id);
create unique index messages_wa_message_id_unique
    on messages (wa_message_id)
    where wa_message_id is not null;
