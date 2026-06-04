-- H-1: Transactional outbox for durable post-commit RabbitMQ publishes.
--
-- Producers (agent reply, inbound AI-reply trigger) used to publish to Rabbit
-- in an after-commit callback. If the broker was unreachable during that
-- window the committed DB row was left with no async follow-up and no retry.
--
-- This table lets those producers write the intended publish as a row in the
-- SAME transaction as the business change. A scheduled relay
-- (OutboxRelay, ShedLock-guarded) drains pending rows, publishes them to the
-- existing konvo.events exchange, and applies exponential backoff on failure.
--
-- Deliberately driven by JdbcTemplate (not a JPA entity) so it participates in
-- the caller's JPA transaction without adding a mapped entity under
-- ddl-auto=validate.

create table outbox_events (
    id              uuid         primary key default gen_random_uuid(),
    exchange        varchar(255) not null,            -- target AMQP exchange
    routing_key     varchar(255) not null,            -- e.g. whatsapp.outbound.text
    payload_type    varchar(255) not null,            -- FQN of the command record
    payload         jsonb        not null,            -- Jackson-serialised command
    status          varchar(16)  not null default 'pending',  -- pending|published|dead
    attempts        int          not null default 0,
    next_attempt_at timestamptz  not null default now(),
    last_error      text,
    created_at      timestamptz  not null default now(),
    published_at    timestamptz
);

-- The relay only ever scans for due, still-pending rows. Partial index keeps it
-- tiny once the bulk of rows have transitioned to published.
create index outbox_events_due_idx
    on outbox_events (next_attempt_at)
    where status = 'pending';
