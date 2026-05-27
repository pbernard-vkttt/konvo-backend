-- M5: knowledge base + AI reply pipeline.
--
-- This migration finally enables the pgvector extension (deferred from V001
-- back in M2 — see implementation log deviation #8) and adds:
--   * knowledge_sources         — uploaded text / future PDFs / URLs
--   * knowledge_chunks          — chunked + embedded snippets used for RAG
--   * ai_runs                   — per-completion audit row (provider, tokens, cost)
--   * conversations.auto_reply_enabled — per-thread toggle for Vee auto-reply
--
-- Local dev: requires pgvector installed for the database. See infra/README.md
-- "Installing pgvector for native Postgres".

create extension if not exists vector;

----------------------------------------------------------------
-- knowledge_sources — the user-facing "thing I taught Vee about".
-- M5 only supports plain text content (uploaded as a paste). PDF + URL
-- ingestion lands in a follow-up — the type column is the extension point.
----------------------------------------------------------------
create table knowledge_sources (
    id              uuid primary key default gen_random_uuid(),
    tenant_id       uuid        not null references tenants(id) on delete cascade,
    title           varchar(200) not null,
    type            varchar(16) not null default 'text',
    status          varchar(16) not null default 'ready',
    content         text,
    char_count      integer     not null default 0,
    chunk_count     integer     not null default 0,
    created_by_user_id uuid     references users(id) on delete set null,
    created_at      timestamptz not null default now(),
    updated_at      timestamptz not null default now(),
    constraint knowledge_sources_type_chk   check (type   in ('text','pdf','url')),
    constraint knowledge_sources_status_chk check (status in ('indexing','ready','failed'))
);

create index knowledge_sources_tenant_idx on knowledge_sources (tenant_id);

----------------------------------------------------------------
-- knowledge_chunks — chunked + embedded body for cosine retrieval.
-- We deliberately store tenant_id on the chunk too (denormalised from the
-- source) so the retriever query is a single-table scan with a tenant filter.
--
-- vector(1536) matches OpenAI's text-embedding-3-small. If a tenant later
-- swaps to a different embedding model we'll add a column and migrate;
-- mixing dimensions in one column isn't supported.
----------------------------------------------------------------
create table knowledge_chunks (
    id              uuid primary key default gen_random_uuid(),
    tenant_id       uuid        not null references tenants(id) on delete cascade,
    source_id       uuid        not null references knowledge_sources(id) on delete cascade,
    ordinal         integer     not null,
    content         text        not null,
    token_count     integer     not null default 0,
    embedding       vector(1536) not null,
    created_at      timestamptz not null default now()
);

create index knowledge_chunks_source_idx on knowledge_chunks (source_id);

-- IVFFlat cosine index. lists=100 is the pgvector tutorial default and is
-- fine for the small per-tenant chunk counts we expect early. Re-tune when
-- a single tenant crosses ~10k chunks.
create index knowledge_chunks_embedding_idx
    on knowledge_chunks
    using ivfflat (embedding vector_cosine_ops)
    with (lists = 100);

----------------------------------------------------------------
-- ai_runs — one row per provider call (chat or embed). Captures token /
-- latency / cost so Settings → Insights can show usage breakdowns later.
----------------------------------------------------------------
create table ai_runs (
    id              uuid primary key default gen_random_uuid(),
    tenant_id       uuid        not null references tenants(id) on delete cascade,
    conversation_id uuid        references conversations(id) on delete set null,
    purpose         varchar(24) not null,
    provider        varchar(24) not null,
    model           varchar(64) not null,
    prompt_tokens   integer     not null default 0,
    completion_tokens integer   not null default 0,
    cost_estimate_usd numeric(10, 6) not null default 0,
    latency_ms      integer,
    status          varchar(16) not null default 'ok',
    error_message   text,
    created_at      timestamptz not null default now(),
    constraint ai_runs_purpose_chk check (purpose in ('reply','embed','suggest')),
    constraint ai_runs_status_chk  check (status  in ('ok','failed'))
);

create index ai_runs_tenant_idx on ai_runs (tenant_id, created_at desc);

----------------------------------------------------------------
-- conversations.auto_reply_enabled — when true, an inbound message on this
-- thread triggers the AI reply pipeline (M5). Default off so M4's
-- agent-only behaviour stays the safe default until the user opts in.
----------------------------------------------------------------
alter table conversations
    add column auto_reply_enabled boolean not null default false;
