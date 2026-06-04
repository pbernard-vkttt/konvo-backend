-- Workspace industry tag and onboarding completion flag.
-- Existing tenants are marked as having completed onboarding so they are not
-- sent through the wizard on their next login.

alter table tenants
    add column industry varchar(80) not null default '',
    add column onboarding_completed boolean not null default false;

update tenants set onboarding_completed = true;
