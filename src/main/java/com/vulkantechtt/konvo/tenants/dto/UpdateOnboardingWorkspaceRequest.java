package com.vulkantechtt.konvo.tenants.dto;

import com.vulkantechtt.konvo.tenants.Tenant;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateOnboardingWorkspaceRequest(
        @NotBlank
        @Size(max = 120)
        String workspaceName,

        @NotBlank
        @Size(max = 80)
        String workspaceSlug,

        @Size(max = 80)
        String industry,

        @Size(max = Tenant.MAX_WORKING_HOURS_LENGTH)
        String workingHours,

        @Size(max = Tenant.MAX_BUSINESS_OFFERINGS_LENGTH)
        String businessOfferings,

        @Size(max = Tenant.MAX_CUSTOM_SYSTEM_PROMPT_LENGTH)
        String customSystemPrompt) {
}
