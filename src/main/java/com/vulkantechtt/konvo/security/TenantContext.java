package com.vulkantechtt.konvo.security;

import java.util.UUID;

/**
 * Per-request tenant resolution. Set by the auth/tenant filter once a request
 * is authenticated and a tenant scope has been determined. Domain services
 * read from here rather than passing tenant_id through every method signature.
 *
 * M1 ships the holder; M2 wires the filter that populates it.
 */
public final class TenantContext {

    private static final ThreadLocal<UUID> CURRENT = new ThreadLocal<>();

    private TenantContext() {}

    public static void set(UUID tenantId) {
        CURRENT.set(tenantId);
    }

    public static UUID get() {
        return CURRENT.get();
    }

    public static UUID require() {
        UUID id = CURRENT.get();
        if (id == null) {
            throw new IllegalStateException("Tenant context not set for this request");
        }
        return id;
    }

    public static void clear() {
        CURRENT.remove();
    }
}
