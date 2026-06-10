-- Distinguish who authored an outbound reply so the inbox can badge it as
-- coming from Vee (the AI) or from a named human agent. Nullable + no backfill:
-- legacy rows stay null and the UI simply shows no badge for them. Inbound
-- messages are left null too — the frontend already derives "customer" from
-- the message direction.
alter table messages add column sender_type varchar(16);
alter table messages add column sender_name varchar(160);

alter table messages
    add constraint messages_sender_type_chk
    check (sender_type is null or sender_type in ('customer', 'agent', 'ai'));
