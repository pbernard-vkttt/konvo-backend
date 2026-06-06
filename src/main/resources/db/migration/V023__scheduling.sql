-- M9: Scheduling — Google Calendar (live engine) + Calendly (booking-link fallback).
--
-- scheduling_settings — one row per tenant. Holds the workspace's single shared
--                       Google Calendar connection (OAuth offline tokens, AES-
--                       encrypted at rest via EncryptedStringConverter, same as
--                       channels.access_token) plus the Calendly fallback link
--                       and the booking knobs (duration, working hours, window).
--                       Booking mode is derived: GOOGLE if connected, else LINK
--                       if a calendly_url is set, else DISABLED.
--
-- appointments        — first-class booking record linked to a customer and the
--                       originating conversation. google_event_id is null when
--                       the booking came via the Calendly link / no live calendar.
--                       source = who booked it (vee | agent); created_by_user_id
--                       is null for Vee-driven bookings.
--
-- conversations.pending_booking — jsonb scratchpad for Vee's multi-turn booking
--                       flow (offered slots + step) so it survives across inbound
--                       messages. Null when no booking is in progress.

----------------------------------------------------------------
-- scheduling_settings
----------------------------------------------------------------
create table scheduling_settings (
    id                          uuid primary key default gen_random_uuid(),
    tenant_id                   uuid        not null unique references tenants(id) on delete cascade,
    google_connected            boolean     not null default false,
    google_account_email        varchar(254),
    google_calendar_id          varchar(255) not null default 'primary',
    google_refresh_token        text,                          -- AES-encrypted
    google_access_token         text,                          -- AES-encrypted, cached
    google_token_expires_at     timestamptz,
    calendly_url                varchar(500),                  -- public link, stored plain
    meeting_duration_minutes    int         not null default 30,
    timezone                    varchar(64) not null default 'UTC',
    booking_window_days         int         not null default 14,
    work_day_start_hour         int         not null default 9,
    work_day_end_hour           int         not null default 17,
    work_days                   varchar(32) not null default 'MON,TUE,WED,THU,FRI',
    created_at                  timestamptz not null default now(),
    updated_at                  timestamptz not null default now()
);

----------------------------------------------------------------
-- appointments
----------------------------------------------------------------
create table appointments (
    id                  uuid primary key default gen_random_uuid(),
    tenant_id           uuid        not null references tenants(id) on delete cascade,
    customer_id         uuid        not null references customers(id) on delete cascade,
    conversation_id     uuid        references conversations(id) on delete set null,
    google_event_id     varchar(255),                          -- null for Calendly / no-calendar
    title               varchar(200) not null,
    notes               text,
    starts_at           timestamptz not null,
    ends_at             timestamptz not null,
    status              varchar(16) not null default 'booked', -- booked | cancelled
    source              varchar(16) not null,                  -- vee | agent
    created_by_user_id  uuid        references users(id) on delete set null,  -- null for Vee
    created_at          timestamptz not null default now(),
    updated_at          timestamptz not null default now()
);

create index appointments_tenant_start_idx on appointments (tenant_id, starts_at desc);
create index appointments_tenant_customer_idx on appointments (tenant_id, customer_id);

----------------------------------------------------------------
-- conversations.pending_booking (Vee multi-turn booking state)
----------------------------------------------------------------
alter table conversations add column pending_booking jsonb;
