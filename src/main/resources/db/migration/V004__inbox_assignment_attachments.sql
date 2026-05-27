-- M4: Inbox enrichments.
--
--   * conversations.assigned_user_id — optional FK to users(id). NULL means
--     "unassigned"; agents see assigned-to-me + unassigned, owners/admins
--     see everything.
--
--   * messages.attachments — jsonb array placeholder so M5 (media uploads,
--     image/audio/document content types) can extend without a schema change.
--     M4 still ships text-only messages; this column stays null for now.

alter table conversations
    add column assigned_user_id uuid references users(id) on delete set null;

create index conversations_assigned_idx
    on conversations (tenant_id, assigned_user_id)
    where assigned_user_id is not null;

alter table messages
    add column attachments jsonb;
