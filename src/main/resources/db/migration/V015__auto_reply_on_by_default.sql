-- V015: Vee auto-reply is on by default for new conversations.
--
-- Conversations are created via JPA (which now initialises the field to true),
-- but we flip the column default too so the two can't drift and any non-JPA
-- insert behaves the same. Existing rows are left as the agent set them.

alter table conversations
    alter column auto_reply_enabled set default true;
