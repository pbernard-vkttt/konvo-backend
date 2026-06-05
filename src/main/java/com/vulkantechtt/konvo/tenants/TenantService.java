package com.vulkantechtt.konvo.tenants;

import com.vulkantechtt.konvo.audit.AuditAction;
import com.vulkantechtt.konvo.audit.AuditService;
import com.vulkantechtt.konvo.common.KonvoException;
import com.vulkantechtt.konvo.common.SafeText;
import com.vulkantechtt.konvo.knowledge.WorkspaceKnowledgeRollupService;
import com.vulkantechtt.konvo.security.KonvoPrincipal;
import com.vulkantechtt.konvo.tenants.dto.TenantResponse;
import com.vulkantechtt.konvo.tenants.dto.UpdateOnboardingWorkspaceRequest;
import com.vulkantechtt.konvo.tenants.dto.UpdateTenantSettingsRequest;
import com.vulkantechtt.konvo.users.MembershipStatus;
import com.vulkantechtt.konvo.users.Role;
import com.vulkantechtt.konvo.users.TenantMembershipRepository;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TenantService {
    private static final Pattern SLUG = Pattern.compile("^[a-z0-9][a-z0-9-]{1,78}[a-z0-9]$");

    private final TenantRepository tenants;
    private final TenantMembershipRepository memberships;
    private final AuditService audit;
    private final WorkspaceKnowledgeRollupService workspaceKnowledgeRollup;

    public TenantService(
            TenantRepository tenants,
            TenantMembershipRepository memberships,
            AuditService audit,
            WorkspaceKnowledgeRollupService workspaceKnowledgeRollup) {
        this.tenants = tenants;
        this.memberships = memberships;
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

    @Transactional
    public TenantResponse updateOnboardingWorkspace(KonvoPrincipal actor, UpdateOnboardingWorkspaceRequest req) {
        requireActiveOwnerMembership(actor);
        Tenant tenant = requireTenant(actor.tenantId());
        if (tenant.isOnboardingCompleted()) {
            throw KonvoException.conflict("Workspace onboarding is already complete");
        }

        String oldName = tenant.getName();
        String oldSlug = tenant.getSlug();
        String oldWorkingHours = safe(tenant.getWorkingHours());
        String oldBusinessOfferings = safe(tenant.getBusinessOfferings());
        String oldCustomSystemPrompt = safe(tenant.getCustomSystemPrompt());
        String oldIndustry = safe(tenant.getIndustry());

        String newName = normalizeWorkspaceName(req.workspaceName());
        String newSlug = normalizeWorkspaceSlug(req.workspaceSlug());
        String newWorkingHours = req.workingHours() == null ? oldWorkingHours : normalize(req.workingHours());
        String newBusinessOfferings = req.businessOfferings() == null
                ? oldBusinessOfferings
                : normalize(req.businessOfferings());
        String newCustomSystemPrompt = req.customSystemPrompt() == null
                ? oldCustomSystemPrompt
                : normalize(req.customSystemPrompt());
        String newIndustry = req.industry() == null ? oldIndustry : req.industry().strip();

        tenants.findBySlug(newSlug)
                .filter(existing -> !existing.getId().equals(tenant.getId()))
                .ifPresent(existing -> {
                    throw KonvoException.conflict("That workspace slug is taken");
                });

        tenant.setName(newName);
        tenant.setSlug(newSlug);
        tenant.setWorkingHours(newWorkingHours);
        tenant.setBusinessOfferings(newBusinessOfferings);
        tenant.setCustomSystemPrompt(newCustomSystemPrompt);
        tenant.setIndustry(newIndustry);
        Tenant saved = tenants.save(tenant);
        workspaceKnowledgeRollup.sync(actor, saved);

        if (!Objects.equals(oldName, newName)
                || !Objects.equals(oldSlug, newSlug)
                || !Objects.equals(oldWorkingHours, newWorkingHours)
                || !Objects.equals(oldBusinessOfferings, newBusinessOfferings)
                || !Objects.equals(oldCustomSystemPrompt, newCustomSystemPrompt)
                || !Objects.equals(oldIndustry, newIndustry)) {
            audit.record(actor, AuditAction.WORKSPACE_SETTINGS_UPDATED, saved.getId(),
                    "Updated onboarding workspace details",
                    Map.of(
                            "workspaceName", Map.of("from", oldName, "to", newName),
                            "workspaceSlug", Map.of("from", oldSlug, "to", newSlug),
                            "workingHoursChanged", !Objects.equals(oldWorkingHours, newWorkingHours),
                            "businessOfferingsChanged", !Objects.equals(oldBusinessOfferings, newBusinessOfferings),
                            "customSystemPromptChanged", !Objects.equals(oldCustomSystemPrompt, newCustomSystemPrompt),
                            "industryChanged", !Objects.equals(oldIndustry, newIndustry)));
        }
        return toResponse(saved);
    }

    @Transactional
    public TenantResponse completeOnboarding(KonvoPrincipal actor) {
        requireActiveOwnerMembership(actor);
        Tenant tenant = requireTenant(actor.tenantId());
        tenant.setOnboardingCompleted(true);
        Tenant saved = tenants.save(tenant);
        audit.record(actor, AuditAction.WORKSPACE_SETTINGS_UPDATED, saved.getId(),
                "Onboarding completed", Map.of());
        return toResponse(saved);
    }

    private Tenant requireTenant(UUID tenantId) {
        return tenants.findById(tenantId)
                .orElseThrow(() -> KonvoException.notFound("Tenant", tenantId));
    }

    private void requireActiveOwnerMembership(KonvoPrincipal actor) {
        var membership = memberships.findByTenantIdAndUserId(actor.tenantId(), actor.userId())
                .orElseThrow(TenantService::sessionWorkspaceMismatch);
        if (membership.getStatus() != MembershipStatus.active) {
            throw sessionWorkspaceMismatch();
        }
        if (membership.getRole() != Role.OWNER) {
            throw KonvoException.forbidden("Only workspace owners can manage onboarding");
        }
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
                safe(tenant.getIndustry()),
                tenant.isOnboardingCompleted(),
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

    private static String normalizeWorkspaceName(String value) {
        String normalized = SafeText.singleLine(value, "", 120);
        if (normalized.isBlank()) {
            throw KonvoException.badRequest("Workspace name is required");
        }
        return normalized;
    }

    private static String normalizeWorkspaceSlug(String value) {
        String normalized = safe(value).strip().toLowerCase();
        if (!SLUG.matcher(normalized).matches()) {
            throw KonvoException.badRequest("Workspace slug must be lower-case letters, numbers, or dashes (3–80 chars)");
        }
        return normalized;
    }

    private static KonvoException sessionWorkspaceMismatch() {
        return new KonvoException(
                HttpStatus.UNAUTHORIZED,
                "session_workspace_mismatch",
                "Your session no longer matches this workspace. Sign in again.");
    }
}
