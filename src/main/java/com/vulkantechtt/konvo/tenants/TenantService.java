package com.vulkantechtt.konvo.tenants;

import com.vulkantechtt.konvo.audit.AuditAction;
import com.vulkantechtt.konvo.audit.AuditService;
import com.vulkantechtt.konvo.common.KonvoException;
import com.vulkantechtt.konvo.security.KonvoPrincipal;
import com.vulkantechtt.konvo.tenants.dto.TenantResponse;
import com.vulkantechtt.konvo.tenants.dto.UpdateTenantSettingsRequest;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TenantService {

    private final TenantRepository tenants;
    private final AuditService audit;

    public TenantService(TenantRepository tenants, AuditService audit) {
        this.tenants = tenants;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public TenantResponse me(UUID tenantId) {
        return toResponse(requireTenant(tenantId));
    }

    @Transactional
    public TenantResponse updateSettings(KonvoPrincipal actor, UpdateTenantSettingsRequest req) {
        Tenant tenant = requireTenant(actor.tenantId());
        int oldLimit = tenant.getCustomerMemoryMessageLimit();
        int newLimit = req.customerMemoryMessageLimit();
        tenant.setCustomerMemoryMessageLimit(newLimit);
        Tenant saved = tenants.save(tenant);

        if (oldLimit != newLimit) {
            audit.record(actor, AuditAction.WORKSPACE_SETTINGS_UPDATED, saved.getId(),
                    "Updated workspace settings",
                    Map.of("customerMemoryMessageLimit", Map.of("from", oldLimit, "to", newLimit)));
        }
        return toResponse(saved);
    }

    private Tenant requireTenant(UUID tenantId) {
        return tenants.findById(tenantId)
                .orElseThrow(() -> KonvoException.notFound("Tenant", tenantId));
    }

    private static TenantResponse toResponse(Tenant tenant) {
        return new TenantResponse(
                tenant.getId(),
                tenant.getName(),
                tenant.getSlug(),
                tenant.getCountryCode(),
                tenant.getPlan(),
                tenant.getStatus().name(),
                tenant.getCustomerMemoryMessageLimit(),
                tenant.getCreatedAt());
    }
}
