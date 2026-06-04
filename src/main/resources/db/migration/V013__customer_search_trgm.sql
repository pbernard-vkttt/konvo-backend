-- L-4: trigram indexes for inbox/customer free-text search.
--
-- Both ConversationRepository.search and CustomerRepository.search match on
-- the customer's name/phone with leading-wildcard predicates:
--   lower(display_name) like '%q%' / lower(profile_name) like '%q%' / phone like '%q%'
-- A b-tree can't serve a leading `%`, so those degrade to a sequential scan as
-- a tenant's contact list grows. pg_trgm GIN indexes on the exact indexed
-- expressions let the planner satisfy the LIKE predicates from an index.
--
-- pg_trgm is a stock contrib extension (same packaging as the vector extension
-- already enabled in V005). Index expressions mirror the query expressions so
-- the planner can actually use them (lower(...) for the name columns, raw for
-- phone, which the query does not lower-case).

create extension if not exists pg_trgm;

create index if not exists customers_display_name_trgm_idx
    on customers using gin (lower(display_name) gin_trgm_ops);

create index if not exists customers_profile_name_trgm_idx
    on customers using gin (lower(profile_name) gin_trgm_ops);

create index if not exists customers_phone_trgm_idx
    on customers using gin (phone gin_trgm_ops);
