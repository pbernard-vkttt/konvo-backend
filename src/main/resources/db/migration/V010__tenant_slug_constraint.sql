-- V010: Database-level format guard for tenant slugs.
--
-- AuthService enforces the slug regex at registration time, but the constraint
-- was missing from the schema, leaving admin/migration paths unchecked.
-- The pattern mirrors AuthService.SLUG: lower-case letters, digits, and
-- hyphens; first and last character must be alphanumeric; total length 3–80.
alter table tenants
    add constraint tenants_slug_format_chk
        check (slug ~ '^[a-z0-9][a-z0-9-]{1,78}[a-z0-9]$');
