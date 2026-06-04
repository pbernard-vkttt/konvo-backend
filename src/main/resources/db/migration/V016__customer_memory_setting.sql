-- Workspace-level Vee memory window. The value is the number of previous
-- conversation messages included as chat history before the latest inbound.

alter table tenants
    add column customer_memory_message_limit integer not null default 12,
    add constraint tenants_customer_memory_message_limit_chk
        check (customer_memory_message_limit between 0 and 50);
