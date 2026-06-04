package com.vulkantechtt.konvo.users;

/**
 * Tenant-scoped role assigned through a TenantMembership. Stored as the enum
 * name (varchar) so adding a new role is a plain code+migration check change.
 *
 * Authority strings used in @PreAuthorize / hasRole(...) are the bare name
 * (no ROLE_ prefix) — the security wiring adds the prefix when building
 * GrantedAuthorities. Use ROLE_PREFIX + name() in filter code, or pass the
 * name() directly to hasAuthority/hasRole helpers.
 */
public enum Role {
    OWNER,
    ADMIN,
    /**
     * Inbox supervisor: sees and manages every conversation in the tenant like
     * an admin (status, auto-reply, replies, assigning agents) but has none of
     * the admin powers outside the inbox (team, channels, knowledge base,
     * templates, settings, billing).
     */
    MANAGER,
    AGENT,
    VIEWER,
    BILLING;

    public static final String ROLE_PREFIX = "ROLE_";

    public String authority() {
        return ROLE_PREFIX + name();
    }
}
