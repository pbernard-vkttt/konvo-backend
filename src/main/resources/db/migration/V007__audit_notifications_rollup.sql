-- M7: Audit log + per-user notifications + usage roll-up indexes.
--
-- audit_log     — append-only tenant-scoped journal of meaningful actions
--                 (member CRUD, channel connect/disconnect, template sync,
--                 plan change). Diff payload is jsonb so each action type
--                 can carry whatever before/after fields make sense without
--                 widening the schema.
--
-- notifications — per-user in-app bell. SSE-pushed when written; the bell
--                 also poll-fetches on app load. Marking read flips read_at.
--
-- usage_counters indexes — the V006 table exists but had no helpful indexes
--                 for the rollup job. Add the tenant-scan + period-scan
--                 indexes here so the daily job and the on-demand reads are
--                 both cheap once it's populated.

----------------------------------------------------------------
-- audit_log
----------------------------------------------------------------
create table audit_log (
    id              uuid primary key default gen_random_uuid(),
    tenant_id       uuid        not null references tenants(id) on delete cascade,
    actor_user_id   uuid        references users(id) on delete set null,
    actor_email     varchar(255),                       -- snapshot, survives user deletion
    action          varchar(64) not null,               -- e.g. "member.invited"
    entity_type     varchar(48) not null,               -- e.g. "membership"
    entity_id       uuid,                                -- optional FK-shape id
    summary         varchar(500) not null,              -- short human line for the UI
    diff            jsonb,                               -- optional before/after fields
    created_at      timestamptz not null default now()
);

create index audit_log_tenant_recent_idx on audit_log (tenant_id, created_at desc);
create index audit_log_entity_idx        on audit_log (tenant_id, entity_type, entity_id);

----------------------------------------------------------------
-- notifications
----------------------------------------------------------------
create table notifications (
    id              uuid primary key default gen_random_uuid(),
    tenant_id       uuid        not null references tenants(id) on delete cascade,
    user_id         uuid        not null references users(id) on delete cascade,
    type            varchar(48) not null,               -- e.g. "conversation.assigned"
    title           varchar(200) not null,
    body            varchar(500),
    link            varchar(500),                       -- optional in-app deep link, e.g. /app/inbox?c=<id>
    read_at         timestamptz,
    created_at      timestamptz not null default now()
);

-- Recent first, unread cheap to count.
create index notifications_user_recent_idx on notifications (user_id, created_at desc);
create index notifications_user_unread_idx on notifications (user_id) where read_at is null;

----------------------------------------------------------------
-- usage_counters indexes (table itself exists in V006)
----------------------------------------------------------------
create index if not exists usage_counters_tenant_idx on usage_counters (tenant_id);
create index if not exists usage_counters_period_idx on usage_counters (period_key);
