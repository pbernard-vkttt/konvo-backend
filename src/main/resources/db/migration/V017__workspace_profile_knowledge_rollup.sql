-- Workspace profile fields that Vee can learn from through a managed
-- knowledge source. The source_key is nullable so user-created sources keep
-- their existing behaviour, while managed sources can be upserted safely.

alter table tenants
    add column working_hours text not null default '',
    add column business_offerings text not null default '';

alter table knowledge_sources
    add column source_key varchar(64);

create unique index knowledge_sources_tenant_source_key_idx
    on knowledge_sources (tenant_id, source_key)
    where source_key is not null;
