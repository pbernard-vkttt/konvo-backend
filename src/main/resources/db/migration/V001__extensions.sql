-- Enable required PostgreSQL extensions for Konvo CRM.
--
-- pgcrypto       — provides gen_random_uuid() so we can default UUIDs in SQL
--                  if a future migration prefers DB-side generation over Hibernate.
-- vector         — pgvector, used by the knowledge-base module (M5) for
--                  embedding similarity search.
--
-- This migration is intentionally minimal: subsequent V00x__*.sql files in
-- each bounded context add the domain tables.

create extension if not exists pgcrypto;
create extension if not exists vector;
