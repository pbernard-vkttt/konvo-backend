-- Workspace-level custom system prompt appended after the base Vee persona.
-- Owners/admins can tune tone or operational rules without replacing the
-- guarded platform prompt.

alter table tenants
    add column custom_system_prompt varchar(300) not null default '',
    add constraint tenants_custom_system_prompt_length_chk
        check (char_length(custom_system_prompt) <= 300);
