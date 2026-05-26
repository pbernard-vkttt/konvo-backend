-- Enable required PostgreSQL extensions for Konvo CRM.
--
-- pgcrypto       — provides gen_random_uuid() so we can default UUIDs in SQL
--                  if a future migration prefers DB-side generation over Hibernate.
--
-- pgvector (the `vector` extension) is added in the M5 knowledge-base
-- migration where it is first needed. Keeping it out of V001 lets the app
-- run against a stock Postgres in M1–M4 without requiring the extra package.

create extension if not exists pgcrypto;
