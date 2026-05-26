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
    AGENT,
    VIEWER,
    BILLING;

    public static final String ROLE_PREFIX = "ROLE_";

    public String authority() {
        return ROLE_PREFIX + name();
    }
}
