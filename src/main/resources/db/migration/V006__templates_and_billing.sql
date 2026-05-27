-- M6: Templates + plans + subscriptions + usage roll-ups.
--
-- WhatsApp message templates (the things Meta pre-approves) are required for
-- any send outside the 24-hour customer service window. We mirror Meta's
-- shape closely so re-syncing is a no-op when nothing changed.
--
-- Billing surface: plans (catalogue, seeded inline below), subscriptions
-- (one active per tenant), and a placeholder usage_counters table for the
-- pre-aggregation we'll wire up when the live usage scan gets too pricey.
-- M6 itself computes usage on demand from `messages` and `ai_runs`.

----------------------------------------------------------------
-- message_templates — one row per (tenant, Meta name, language).
-- components stored as jsonb verbatim — Meta returns a structured tree
-- (HEADER / BODY / FOOTER / BUTTONS) and we don't gain anything by parsing it
-- into JPA columns just yet.
----------------------------------------------------------------
create table message_templates (
    id              uuid primary key default gen_random_uuid(),
    tenant_id       uuid        not null references tenants(id) on delete cascade,
    name            varchar(120) not null,
    language        varchar(16) not null,
    category        varchar(24) not null,
    status          varchar(16) not null,
    components      jsonb,
    meta_template_id varchar(64),
    created_at      timestamptz not null default now(),
    updated_at      timestamptz not null default now(),
    constraint message_templates_unique unique (tenant_id, name, language),
    constraint message_templates_category_chk check (category in ('marketing','utility','authentication')),
    constraint message_templates_status_chk   check (status   in ('approved','pending','rejected','paused','disabled'))
);

create index message_templates_tenant_idx on message_templates (tenant_id);

----------------------------------------------------------------
-- plans — catalogue. Limits are explicit numeric columns so a service-layer
-- check is a single field read; a future "feature flags as jsonb" can stack
-- on top without disturbing the existing columns.
----------------------------------------------------------------
create table plans (
    id              varchar(32) primary key,
    name            varchar(80) not null,
    monthly_price_usd numeric(10, 2) not null default 0,
    msg_monthly_limit integer  not null,
    ai_runs_monthly_limit integer not null,
    ai_tokens_monthly_limit integer not null,
    knowledge_sources_limit integer not null,
    members_limit   integer     not null,
    is_public       boolean     not null default true,
    created_at      timestamptz not null default now()
);

insert into plans (id, name, monthly_price_usd, msg_monthly_limit, ai_runs_monthly_limit, ai_tokens_monthly_limit, knowledge_sources_limit, members_limit) values
    ('free',    'Free',    0.00,    500,    200,     50000,  5,  3),
    ('starter', 'Starter', 29.00,  5000,  3000,    500000, 25, 10),
    ('pro',     'Pro',     99.00, 25000, 15000,   2500000, 200, 50);

----------------------------------------------------------------
-- subscriptions — one active per tenant. period_start/period_end roll
-- monthly. Status is intentionally loose — we'll connect payments in M8+.
----------------------------------------------------------------
create table subscriptions (
    id              uuid primary key default gen_random_uuid(),
    tenant_id       uuid        not null references tenants(id) on delete cascade,
    plan_id         varchar(32) not null references plans(id),
    status          varchar(16) not null default 'active',
    period_start    timestamptz not null default now(),
    period_end      timestamptz not null default now() + interval '1 month',
    created_at      timestamptz not null default now(),
    updated_at      timestamptz not null default now(),
    constraint subscriptions_status_chk check (status in ('active','past_due','cancelled'))
);

create unique index subscriptions_active_per_tenant_idx
    on subscriptions (tenant_id)
    where status = 'active';

-- Back-fill: every existing tenant gets a free subscription so the billing
-- endpoint returns sensibly today. New tenants get one via AuthService.
insert into subscriptions (tenant_id, plan_id)
select id, 'free' from tenants
where id not in (select tenant_id from subscriptions where status = 'active');

----------------------------------------------------------------
-- usage_counters — pre-aggregated rolling counts (year-month key).
-- Not populated in M6: the billing endpoint computes from raw `messages`
-- and `ai_runs` on the fly. M7 / M8 wires a daily rollup job to fill this
-- so dashboards don't full-scan at large tenant scale.
----------------------------------------------------------------
create table usage_counters (
    id              uuid primary key default gen_random_uuid(),
    tenant_id       uuid        not null references tenants(id) on delete cascade,
    period_key      varchar(7)  not null,            -- "2026-05"
    messages_sent   integer     not null default 0,
    ai_runs         integer     not null default 0,
    ai_tokens       integer     not null default 0,
    updated_at      timestamptz not null default now(),
    constraint usage_counters_tenant_period_unique unique (tenant_id, period_key)
);
