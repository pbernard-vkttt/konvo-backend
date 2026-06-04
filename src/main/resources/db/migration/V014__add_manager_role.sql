-- V014: add the MANAGER role.
--
-- Managers see and manage every conversation in their tenant (like admins) but
-- have no admin powers outside the inbox. Roles are stored as varchar guarded
-- by CHECK constraints (see V002), so widening the allowed set is a drop + add
-- on the two role columns — no enum/type migration needed.

alter table tenant_memberships
    drop constraint tenant_memberships_role_chk,
    add  constraint tenant_memberships_role_chk
        check (role in ('OWNER','ADMIN','MANAGER','AGENT','VIEWER','BILLING'));

alter table user_invitations
    drop constraint user_invitations_role_chk,
    add  constraint user_invitations_role_chk
        check (role in ('OWNER','ADMIN','MANAGER','AGENT','VIEWER','BILLING'));
