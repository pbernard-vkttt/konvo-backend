package com.vulkantechtt.konvo.tenants;

import com.vulkantechtt.konvo.audit.AuditAction;
import com.vulkantechtt.konvo.audit.AuditService;
import com.vulkantechtt.konvo.common.KonvoException;
import com.vulkantechtt.konvo.knowledge.WorkspaceKnowledgeRollupService;
import com.vulkantechtt.konvo.security.KonvoPrincipal;
import com.vulkantechtt.konvo.tenants.dto.TenantResponse;
import com.vulkantechtt.konvo.tenants.dto.UpdateTenantSettingsRequest;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TenantService {

    private final TenantRepository tenants;
    private final AuditService audit;
    private final WorkspaceKnowledgeRollupService workspaceKnowledgeRollup;

    public TenantService(
            TenantRepository tenants,
            AuditService audit,
            WorkspaceKnowledgeRollupService workspaceKnowledgeRollup) {
        this.tenants = tenants;
        this.audit = audit;
        this.workspaceKnowledgeRollup = workspaceKnowledgeRollup;
    }

    @Transactional(readOnly = true)
    public TenantResponse me(UUID tenantId) {
        return toResponse(requireTenant(tenantId));
    }

    @Transactional
    public TenantResponse updateSettings(KonvoPrincipal actor, UpdateTenantSettingsRequest req) {
        Tenant tenant = requireTenant(actor.tenantId());
        int oldLimit = tenant.getCustomerMemoryMessageLimit();
        String oldWorkingHours = safe(tenant.getWorkingHours());
        String oldBusinessOfferings = safe(tenant.getBusinessOfferings());
        String oldCustomSystemPrompt = safe(tenant.getCustomSystemPrompt());
        int newLimit = req.customerMemoryMessageLimit();
        String newWorkingHours = req.workingHours() == null ? oldWorkingHours : normalize(req.workingHours());
        String newBusinessOfferings = req.businessOfferings() == null
                ? oldBusinessOfferings
                : normalize(req.businessOfferings());
        String newCustomSystemPrompt = req.customSystemPrompt() == null
                ? oldCustomSystemPrompt
                : normalize(req.customSystemPrompt());

        tenant.setCustomerMemoryMessageLimit(newLimit);
        tenant.setWorkingHours(newWorkingHours);
        tenant.setBusinessOfferings(newBusinessOfferings);
        tenant.setCustomSystemPrompt(newCustomSystemPrompt);
        Tenant saved = tenants.save(tenant);
        workspaceKnowledgeRollup.sync(actor, saved);

        if (oldLimit != newLimit
                || !Objects.equals(oldWorkingHours, newWorkingHours)
                || !Objects.equals(oldBusinessOfferings, newBusinessOfferings)
                || !Objects.equals(oldCustomSystemPrompt, newCustomSystemPrompt)) {
            audit.record(actor, AuditAction.WORKSPACE_SETTINGS_UPDATED, saved.getId(),
                    "Updated workspace settings",
                    Map.of(
                            "customerMemoryMessageLimit", Map.of("from", oldLimit, "to", newLimit),
                            "workingHoursChanged", !Objects.equals(oldWorkingHours, newWorkingHours),
                            "businessOfferingsChanged", !Objects.equals(oldBusinessOfferings, newBusinessOfferings),
                            "customSystemPromptChanged", !Objects.equals(oldCustomSystemPrompt, newCustomSystemPrompt)));
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
                safe(tenant.getWorkingHours()),
                safe(tenant.getBusinessOfferings()),
                safe(tenant.getCustomSystemPrompt()),
                tenant.getCreatedAt());
    }

    private static String normalize(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("\r\n", "\n")
                .replace('\r', '\n')
                .strip();
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
