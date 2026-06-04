package com.vulkantechtt.konvo.tenants.dto;

import com.vulkantechtt.konvo.tenants.Tenant;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record UpdateTenantSettingsRequest(
        @NotNull
        @Min(0)
        @Max(Tenant.MAX_CUSTOMER_MEMORY_MESSAGE_LIMIT)
        Integer customerMemoryMessageLimit) {
}
