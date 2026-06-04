-- V009: Compound index on messages for the Insights response-time CTE.
--
-- InsightsService.responseTimeBuckets uses a correlated lookup that pairs
-- each inbound message with the first subsequent outbound in the same
-- conversation, ordered by created_at. Without this index the query scans
-- the full messages table per tenant at any non-trivial message volume.
create index if not exists messages_conv_dir_created_idx
    on messages (conversation_id, direction, created_at);
